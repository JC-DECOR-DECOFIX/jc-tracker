package br.com.jcdecor.tracker.tracking

enum class SessionStatus {
    IDLE,
    STARTING,
    TRACKING,
    DEGRADED,
    STOPPING,
    STOPPED,
}

fun SessionStatus.isActive(): Boolean =
    this == SessionStatus.STARTING ||
        this == SessionStatus.TRACKING ||
        this == SessionStatus.DEGRADED ||
        this == SessionStatus.STOPPING

/** GPS e heartbeat. STOPPING continua ativo na tela, mas não coleta. */
fun SessionStatus.isCollecting(): Boolean =
    this == SessionStatus.STARTING ||
        this == SessionStatus.TRACKING ||
        this == SessionStatus.DEGRADED

enum class CredentialState {
    MISSING,
    VALID,
    REFRESH_NEEDED,
    REVOKED,
}

fun CredentialState.debugLabel(): String = when (this) {
    CredentialState.MISSING -> "missing"
    CredentialState.VALID -> "valid"
    CredentialState.REFRESH_NEEDED -> "refresh-needed"
    CredentialState.REVOKED -> "revoked"
}

data class TrackingContext(
    val trackingSessionId: String,
    val deviceId: String,
    val startedAtEpochMs: Long,
    val routeId: String? = null,
    val driverId: String? = null,
)

data class TrackingSession(
    val trackingSessionId: String,
    val deviceId: String,
    val startedAtEpochMs: Long,
    val routeId: String? = null,
    val driverId: String? = null,
    val lastLocationAtEpochMs: Long? = null,
    val sequence: Long = 0,
    val status: SessionStatus,
    val interrupted: Boolean = false,
    val stoppedAtEpochMs: Long? = null,
    val lastAcceptedLatitude: Double? = null,
    val lastAcceptedLongitude: Double? = null,
    val lastAcceptedAtEpochMs: Long? = null,
    val serverAcknowledged: Boolean = false,
    val stopPending: Boolean = false,
) {
    fun context(): TrackingContext = TrackingContext(
        trackingSessionId = trackingSessionId,
        deviceId = deviceId,
        startedAtEpochMs = startedAtEpochMs,
        routeId = routeId,
        driverId = driverId,
    )
}

data class RawFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val altitudeMeters: Double?,
    val recordedAtEpochMs: Long,
)

data class LastFixSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val altitudeMeters: Double?,
    val recordedAtEpochMs: Long,
    val receivedAtEpochMs: Long,
    val acceptable: Boolean,
)

data class PendingPoint(
    val id: Long = 0,
    val trackingSessionId: String,
    val routeId: String?,
    val deviceId: String,
    val sequence: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float?,
    val bearing: Float?,
    val altitude: Double?,
    val recordedAt: String,
    val createdAt: Long,
    val retryCount: Int = 0,
)

enum class RejectReason {
    LOW_ACCURACY,
    STALE,
}

sealed class StartOutcome {
    data class Started(val session: TrackingSession) : StartOutcome()
    data class Resumed(val session: TrackingSession) : StartOutcome()
    data class AlreadyActive(val session: TrackingSession) : StartOutcome()
    data class InvalidRoute(val message: String) : StartOutcome()
    data class InterruptedPending(val session: TrackingSession) : StartOutcome()
}

sealed class LocationOutcome {
    data class Accepted(
        val sequence: Long,
        val sent: Boolean,
        val buffered: Boolean,
        val bufferReason: String?,
    ) : LocationOutcome()

    data class Rejected(val reason: RejectReason, val accuracyMeters: Float) : LocationOutcome()
    data class DisplayOnly(val reason: String) : LocationOutcome()
    data object NoActiveSession : LocationOutcome()
}

sealed class UploadResult {
    data object Success : UploadResult()
    data class Retryable(val reason: String) : UploadResult()

    /**
     * O backend recusou ESTE ponto de vez (4xx que não é autenticação nem limite:
     * payload inválido, sequência em conflito, sessão fechada, rota trocada). Reenviar
     * daria a mesma resposta para sempre e seguraria todos os pontos atrás dele; o
     * motor apaga o ponto, registra `LOCATION_DISCARDED` e segue com o próximo.
     */
    data class Rejected(val reason: String) : UploadResult()

    /** Para o envio deste ciclo sem apagar o ponto e sem registrar de novo. */
    data class Halted(val reason: String) : UploadResult()
}

data class HeartbeatPing(
    val trackingSessionId: String,
    val deviceId: String,
    val recordedAt: String,
    val appVersion: String? = null,
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val networkType: String? = null,
)

sealed class OutboundResult {
    data object Ok : OutboundResult()
    data class Retryable(val reason: String) : OutboundResult()
    data class Revoked(val reason: String) : OutboundResult()
}

interface TrackingOutbound {
    suspend fun publishStart(session: TrackingSession): OutboundResult
    suspend fun publishStop(session: TrackingSession, stoppedAtIso: String): OutboundResult
}

interface LocationUploader {
    suspend fun upload(point: PendingPoint): UploadResult
}

interface PendingLocationStore {
    suspend fun insert(point: PendingPoint): PendingPoint
    suspend fun listInSendOrder(): List<PendingPoint>
    suspend fun deleteById(id: Long)
    suspend fun incrementRetry(id: Long)
    suspend fun count(): Int
}

interface HeartbeatClient {
    suspend fun send(ping: HeartbeatPing)
}

interface UploadDiagnostics {
    fun onUploadSuccess(atEpochMs: Long)
    fun onHttpError(message: String)
}

interface TrackerStatus {
    fun onCredential(state: CredentialState, registered: Boolean)
    fun onHeartbeatSuccess(atEpochMs: Long)
}

interface TrackerLog {
    fun info(message: String)
}

object NoOpTrackerLog : TrackerLog {
    override fun info(message: String) = Unit
}
