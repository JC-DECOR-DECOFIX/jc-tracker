package br.com.jcdecor.tracker.data.remote

import br.com.jcdecor.tracker.data.local.DeviceCredentialStore
import br.com.jcdecor.tracker.tracking.CredentialState
import br.com.jcdecor.tracker.tracking.HeartbeatClient
import br.com.jcdecor.tracker.tracking.HeartbeatPing
import br.com.jcdecor.tracker.tracking.LocationUploader
import br.com.jcdecor.tracker.tracking.OutboundResult
import br.com.jcdecor.tracker.tracking.PendingPoint
import br.com.jcdecor.tracker.tracking.TrackerLog
import br.com.jcdecor.tracker.tracking.TrackerStatus
import br.com.jcdecor.tracker.tracking.TrackingOutbound
import br.com.jcdecor.tracker.tracking.TrackingSession
import br.com.jcdecor.tracker.tracking.UploadDiagnostics
import br.com.jcdecor.tracker.tracking.UploadResult
import br.com.jcdecor.tracker.util.Iso8601

data class DeviceProfile(
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String,
)

/**
 * Cliente REAL do contrato atual da Logística.
 * O aparelho não envia route_id. A credencial de bootstrap só registra a instalação.
 */
class LogisticsTracker(
    private val transport: TrackerTransport,
    private val credentials: DeviceCredentialStore,
    private val bootstrapToken: String,
    private val profile: DeviceProfile,
    private val log: TrackerLog,
    private val diagnostics: UploadDiagnostics?,
    private val status: TrackerStatus,
    private val sessionLookup: () -> TrackingSession?,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : LocationUploader, HeartbeatClient, TrackingOutbound {
    private val openedSessions = mutableSetOf<String>()
    private var recoveryBlockedUntil = 0L

    init {
        publishCredential()
    }

    override suspend fun publishStart(session: TrackingSession): OutboundResult {
        if (session.serverAcknowledged || session.trackingSessionId in openedSessions) {
            openedSessions += session.trackingSessionId
            return OutboundResult.Ok
        }
        return openSession(session.trackingSessionId, session.deviceId, Iso8601.formatUtc(session.startedAtEpochMs))
    }

    override suspend fun publishStop(session: TrackingSession, stoppedAtIso: String): OutboundResult {
        val opened = openSession(session.trackingSessionId, session.deviceId, Iso8601.formatUtc(session.startedAtEpochMs))
        if (opened !is OutboundResult.Ok) return opened
        val call = deviceCall(
            deviceId = session.deviceId,
            path = TrackerPaths.stop(session.trackingSessionId),
            json = trackerJson(
                "device_id" to session.deviceId,
                "stopped_at" to stoppedAtIso,
            ),
        )
        return when (call) {
            is Call.Ok -> {
                log.info("tracking.stop session=${session.trackingSessionId} result=${call.code}")
                OutboundResult.Ok
            }
            is Call.Revoked -> OutboundResult.Revoked(call.reason)
            is Call.Retryable -> {
                log.info("tracking.stop session=${session.trackingSessionId} result=${call.reason}")
                diagnostics?.onHttpError(call.reason)
                OutboundResult.Retryable(call.reason)
            }
            is Call.Rejected -> {
                // Parar uma sessão que o backend já fechou é o resultado desejado.
                log.info("tracking.stop session=${session.trackingSessionId} result=${call.reason}")
                OutboundResult.Ok
            }
            is Call.AuthRejected -> {
                diagnostics?.onHttpError(call.codeName)
                OutboundResult.Retryable(call.codeName)
            }
        }
    }

    override suspend fun upload(point: PendingPoint): UploadResult {
        val session = sessionLookup()
        val startedAt = if (session?.trackingSessionId == point.trackingSessionId) {
            Iso8601.formatUtc(session.startedAtEpochMs)
        } else {
            point.recordedAt
        }
        when (val opened = openSession(point.trackingSessionId, point.deviceId, startedAt)) {
            is OutboundResult.Revoked -> return UploadResult.Halted(opened.reason)
            is OutboundResult.Retryable -> return UploadResult.Retryable(opened.reason)
            OutboundResult.Ok -> Unit
        }
        val call = deviceCall(
            deviceId = point.deviceId,
            path = TrackerPaths.LOCATIONS,
            json = trackerJson(
                "tracking_session_id" to point.trackingSessionId,
                "device_id" to point.deviceId,
                "sequence" to point.sequence,
                "latitude" to point.latitude,
                "longitude" to point.longitude,
                "accuracy" to point.accuracy,
                "speed" to point.speed,
                "bearing" to point.bearing,
                "altitude" to point.altitude,
                "recorded_at" to point.recordedAt,
            ),
        )
        return when (call) {
            is Call.Ok -> {
                diagnostics?.onUploadSuccess(clock())
                log.info("tracking.location session=${point.trackingSessionId} seq=${point.sequence} result=${call.code}")
                UploadResult.Success
            }
            is Call.Revoked -> UploadResult.Halted(call.reason)
            is Call.Retryable -> {
                diagnostics?.onHttpError(call.reason)
                log.info("tracking.location session=${point.trackingSessionId} seq=${point.sequence} result=${call.reason}")
                UploadResult.Retryable(call.reason)
            }
            is Call.Rejected -> {
                diagnostics?.onHttpError(call.reason)
                log.info("tracking.location session=${point.trackingSessionId} seq=${point.sequence} result=${call.reason}")
                UploadResult.Rejected(call.reason)
            }
            is Call.AuthRejected -> {
                diagnostics?.onHttpError(call.codeName)
                UploadResult.Retryable(call.codeName)
            }
        }
    }

    override suspend fun send(ping: HeartbeatPing) {
        val session = sessionLookup()
        val startedAt = session?.takeIf { it.trackingSessionId == ping.trackingSessionId }
            ?.let { Iso8601.formatUtc(it.startedAtEpochMs) }
            ?: ping.recordedAt
        when (val opened = openSession(ping.trackingSessionId, ping.deviceId, startedAt)) {
            is OutboundResult.Revoked -> {
                diagnostics?.onHttpError(opened.reason)
                return
            }
            is OutboundResult.Retryable -> {
                diagnostics?.onHttpError(opened.reason)
                log.info("HEARTBEAT_FAILED session=${ping.trackingSessionId} code=${opened.reason}")
                return
            }
            OutboundResult.Ok -> Unit
        }
        val network = ping.networkType?.lowercase()?.take(16)?.takeIf { NETWORK_TYPE.matches(it) }
        val call = deviceCall(
            deviceId = ping.deviceId,
            path = TrackerPaths.HEARTBEAT,
            json = trackerJson(
                "tracking_session_id" to ping.trackingSessionId,
                "device_id" to ping.deviceId,
                "recorded_at" to ping.recordedAt,
                "app_version" to ping.appVersion?.take(32),
                "battery_level" to ping.batteryLevel,
                "is_charging" to ping.isCharging,
                "network_type" to network,
            ),
        )
        when (call) {
            is Call.Ok -> {
                status.onHeartbeatSuccess(clock())
                log.info("tracking.heartbeat session=${ping.trackingSessionId} result=${call.code}")
            }
            is Call.Revoked -> diagnostics?.onHttpError(call.reason)
            is Call.Retryable, is Call.Rejected -> {
                val reason = if (call is Call.Retryable) call.reason else (call as Call.Rejected).reason
                diagnostics?.onHttpError(reason)
                log.info("HEARTBEAT_FAILED session=${ping.trackingSessionId} code=$reason")
            }
            is Call.AuthRejected -> {
                diagnostics?.onHttpError(call.codeName)
                log.info("HEARTBEAT_FAILED session=${ping.trackingSessionId} code=${call.codeName}")
            }
        }
    }

    private suspend fun openSession(sessionId: String, deviceId: String, startedAt: String): OutboundResult {
        if (sessionId in openedSessions) return OutboundResult.Ok
        val known = sessionLookup()
        if (known?.trackingSessionId == sessionId && known.serverAcknowledged) {
            openedSessions += sessionId
            return OutboundResult.Ok
        }
        val call = deviceCall(
            deviceId = deviceId,
            path = TrackerPaths.SESSIONS,
            json = trackerJson(
                "tracking_session_id" to sessionId,
                "device_id" to deviceId,
                "started_at" to startedAt,
                "app_version" to profile.appVersion.take(32),
            ),
        )
        return when (call) {
            is Call.Ok -> {
                openedSessions += sessionId
                log.info("tracking.session session=$sessionId result=${call.code}")
                OutboundResult.Ok
            }
            is Call.Revoked -> OutboundResult.Revoked(call.reason)
            is Call.Retryable -> {
                diagnostics?.onHttpError(call.reason)
                log.info("tracking.session session=$sessionId result=${call.reason}")
                OutboundResult.Retryable(call.reason)
            }
            is Call.Rejected -> {
                diagnostics?.onHttpError(call.reason)
                log.info("tracking.session session=$sessionId result=${call.reason}")
                OutboundResult.Retryable(call.reason)
            }
            is Call.AuthRejected -> {
                diagnostics?.onHttpError(call.codeName)
                OutboundResult.Retryable(call.codeName)
            }
        }
    }

    private suspend fun deviceCall(deviceId: String, path: String, json: String): Call {
        if (!prepareCredential(deviceId)) {
            val record = credentials.read()
            return if (record.state(clock()) == CredentialState.REVOKED) {
                Call.Revoked("device_revoked")
            } else {
                Call.Retryable("CONFIG")
            }
        }
        val first = postDevice(path, json)
        if (first !is Call.AuthRejected) return first
        if (first.codeName == "device_revoked") {
            credentials.markRevoked()
            publishCredential()
            diagnostics?.onHttpError("device_revoked")
            log.info("tracking.auth_failed code=device_revoked")
            return Call.Revoked("device_revoked")
        }
        if (!recoverOnce(deviceId, first.codeName)) {
            diagnostics?.onHttpError(first.codeName)
            return Call.Retryable(first.codeName)
        }
        return when (val second = postDevice(path, json)) {
            is Call.AuthRejected -> {
                if (second.codeName == "device_revoked") {
                    credentials.markRevoked()
                    publishCredential()
                    diagnostics?.onHttpError("device_revoked")
                    Call.Revoked("device_revoked")
                } else {
                    diagnostics?.onHttpError(second.codeName)
                    Call.Retryable(second.codeName)
                }
            }
            else -> second
        }
    }

    private suspend fun prepareCredential(deviceId: String): Boolean {
        val record = credentials.read()
        val state = record.state(clock())
        publishCredential()
        if (state == CredentialState.REVOKED) return false
        if (record.token.isNullOrBlank()) return register(deviceId)
        if (state == CredentialState.REFRESH_NEEDED && clock() >= recoveryBlockedUntil) {
            refresh()
        }
        val after = credentials.read()
        publishCredential()
        return after.registered() && after.state(clock()) != CredentialState.REVOKED
    }

    private suspend fun recoverOnce(deviceId: String, codeName: String): Boolean {
        if (clock() < recoveryBlockedUntil) return false
        val preferRefresh = codeName == "device_credential_expired" || codeName == "device_credential_revoked"
        val recovered = refreshOrRegister(deviceId, preferRefresh)
        if (!recovered) recoveryBlockedUntil = clock() + RECOVERY_COOLDOWN_MS
        return recovered
    }

    private suspend fun refreshOrRegister(deviceId: String, preferRefresh: Boolean): Boolean {
        if (preferRefresh && refresh()) return true
        return register(deviceId)
    }

    private suspend fun refresh(): Boolean {
        val token = credentials.read().token ?: return false
        val response = try {
            transport.post(TrackerPaths.REFRESH, bearer(token), "{}")
        } catch (_: TrackerNetworkException) {
            return false
        }
        if (response.code !in 200..299) {
            log.info("tracking.refresh result=${errorCodeOf(response.body) ?: "HTTP_${response.code}"}")
            return false
        }
        return storeIssued(response.body, "tracking.refresh result=${response.code}")
    }

    private suspend fun register(deviceId: String): Boolean {
        if (credentials.read().state(clock()) == CredentialState.REVOKED) return false
        val bootstrap = bootstrapToken.trim()
        if (bootstrap.isEmpty()) {
            log.info("tracking.register result=CONFIG")
            diagnostics?.onHttpError("CONFIG")
            return false
        }
        val response = try {
            transport.post(
                TrackerPaths.REGISTER,
                bearer(bootstrap),
                trackerJson(
                    "device_id" to deviceId,
                    "app_version" to profile.appVersion.take(32),
                    "android_version" to profile.androidVersion.take(16),
                    "device_model" to profile.deviceModel.take(64),
                ),
            )
        } catch (exception: TrackerNetworkException) {
            log.info("tracking.register result=${exception.message}")
            diagnostics?.onHttpError(exception.message ?: "NETWORK")
            return false
        }
        if (response.code !in 200..299) {
            val code = errorCodeOf(response.body) ?: "HTTP_${response.code}"
            if (code == "device_revoked") {
                credentials.markRevoked()
                publishCredential()
                diagnostics?.onHttpError("device_revoked")
            } else {
                diagnostics?.onHttpError(code)
            }
            log.info("tracking.register device=$deviceId result=$code")
            return false
        }
        val stored = storeIssued(response.body, "tracking.register device=$deviceId result=${response.code}")
        if (stored) recoveryBlockedUntil = 0L
        return stored
    }

    private fun storeIssued(body: String, successLog: String): Boolean {
        val token = stringField(body, "device_access_token")
        if (token.isNullOrBlank() || !token.startsWith("jctdev_")) return false
        credentials.save(
            token = token,
            expiresAtEpochMs = parseIsoEpochMs(stringField(body, "expires_at")),
            refreshAfterEpochMs = parseIsoEpochMs(stringField(body, "refresh_after")),
        )
        publishCredential()
        log.info(successLog)
        return true
    }

    private suspend fun postDevice(path: String, json: String): Call {
        val token = credentials.read().token
        if (token.isNullOrBlank()) return Call.Retryable("CONFIG")
        val response = try {
            transport.post(path, bearer(token), json)
        } catch (exception: TrackerNetworkException) {
            return Call.Retryable(exception.message ?: "NETWORK")
        }
        if (response.code in 200..299) return Call.Ok(response.code)
        val code = errorCodeOf(response.body)
        if (response.code == 401 && code != null) return Call.AuthRejected(code)
        if (response.code in 400..499 && response.code !in RETRYABLE_CLIENT_ERRORS) {
            return Call.Rejected(code ?: "HTTP_${response.code}")
        }
        return Call.Retryable(code ?: "HTTP_${response.code}")
    }

    private fun publishCredential() {
        val record = credentials.read()
        status.onCredential(record.state(clock()), record.registered())
    }

    private fun bearer(token: String): String = "Bearer $token"

    private sealed class Call {
        data class Ok(val code: Int) : Call()
        data class Retryable(val reason: String) : Call()
        /** Um 4xx definitivo para esta requisição: repetir dá a mesma resposta. */
        data class Rejected(val reason: String) : Call()
        data class Revoked(val reason: String) : Call()
        data class AuthRejected(val codeName: String) : Call()
    }

    private companion object {
        const val RECOVERY_COOLDOWN_MS = 60_000L
        /** Os 4xx que ainda valem nova tentativa: autenticação, tempo esgotado, limite. */
        val RETRYABLE_CLIENT_ERRORS = setOf(401, 408, 425, 429)
        val NETWORK_TYPE = Regex("^[a-z0-9_-]{1,16}$")
    }
}
