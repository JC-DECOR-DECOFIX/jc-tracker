package br.com.jcdecor.tracker.data.remote

import br.com.jcdecor.tracker.data.local.MemoryCredentialStore
import br.com.jcdecor.tracker.tracking.CredentialState
import br.com.jcdecor.tracker.tracking.HeartbeatPing
import br.com.jcdecor.tracker.tracking.MemoryLog
import br.com.jcdecor.tracker.tracking.OutboundResult
import br.com.jcdecor.tracker.tracking.PendingPoint
import br.com.jcdecor.tracker.tracking.SessionStatus
import br.com.jcdecor.tracker.tracking.TrackingRuntime
import br.com.jcdecor.tracker.tracking.TrackingSession
import br.com.jcdecor.tracker.tracking.UploadResult
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsTrackerTest {
    private val bootstrap = "jcsvc_examplepublic_not-a-real-secret"
    private val deviceToken = "jctdev_issuedpublic_issued-secret"
    private val rotatedToken = "jctdev_rotatedpublic_rotated-secret"

    @Test
    fun registerUsesFullBootstrapAndNeverLogsIt() = runBlocking {
        val http = ScriptedTransport()
        http.enqueue(201, credentialBody(deviceToken))
        http.enqueue(201, """{"tracking_session_id":"session-1","status":"ACTIVE"}""")
        val log = MemoryLog()
        val tracker = tracker(http, log)
        val result = tracker.publishStart(session())
        assertTrue(result is OutboundResult.Ok)
        val register = http.calls[0]
        assertEquals(TrackerPaths.REGISTER, register.path)
        assertEquals("Bearer $bootstrap", register.authorization)
        assertEquals("11111111-1111-4111-8111-111111111111", json(register.body, "device_id"))
        assertFalse(register.body.contains("route_id"))
        val sessionCall = http.calls[1]
        assertEquals(TrackerPaths.SESSIONS, sessionCall.path)
        assertEquals("Bearer $deviceToken", sessionCall.authorization)
        assertFalse(sessionCall.body.contains("route_id"))
        assertEquals("session-1", json(sessionCall.body, "tracking_session_id"))
        val dumped = log.lines.joinToString("\n")
        assertFalse(dumped.contains(bootstrap))
        assertFalse(dumped.contains(deviceToken))
        assertFalse(dumped.contains("Authorization"))
    }

    @Test
    fun locationAndHeartbeatAndStopOmitRouteId() = runBlocking {
        val http = ScriptedTransport()
        http.enqueue(201, credentialBody(deviceToken))
        http.enqueue(201, """{"tracking_session_id":"session-1","status":"ACTIVE","route_id":null}""")
        http.enqueue(201, """{"result":"current","sequence":1,"route_id":null}""")
        http.enqueue(200, """{"result":"recorded","status":"ACTIVE","route_id":null}""")
        http.enqueue(200, """{"status":"STOPPED","route_id":null}""")
        val log = MemoryLog()
        val tracker = tracker(http, log)
        val point = point()
        assertTrue(tracker.upload(point) is UploadResult.Success)
        tracker.send(HeartbeatPing("session-1", point.deviceId, "2026-10-13T13:00:00.000Z", batteryLevel = 80, networkType = "wifi"))
        assertTrue(tracker.publishStop(session(), "2026-10-13T13:00:20.000Z") is OutboundResult.Ok)
        val paths = http.calls.map { it.path }
        assertEquals(
            listOf(
                TrackerPaths.REGISTER,
                TrackerPaths.SESSIONS,
                TrackerPaths.LOCATIONS,
                TrackerPaths.HEARTBEAT,
                TrackerPaths.stop("session-1"),
            ),
            paths,
        )
        assertTrue(paths.none { it.contains("api/tracking/routes") })
        val location = http.calls[2]
        assertEquals("session-1", json(location.body, "tracking_session_id"))
        assertEquals("1", json(location.body, "sequence"))
        assertFalse(location.body.contains("route_id"))
        val heartbeat = http.calls[3]
        assertEquals("session-1", json(heartbeat.body, "tracking_session_id"))
        assertFalse(heartbeat.body.contains("route_id"))
        assertFalse(log.lines.joinToString("\n").contains(deviceToken))
    }

    @Test
    fun offlineLocationStaysRetryable() = runBlocking {
        val http = ScriptedTransport()
        val store = MemoryCredentialStore()
        store.save(deviceToken, expiresAtEpochMs = 9_000_000_000_000L, refreshAfterEpochMs = 9_000_000_000_000L)
        http.failNext("NETWORK")
        val tracker = tracker(http, MemoryLog(), store, now = 1_000L)
        val result = tracker.upload(point())
        assertEquals("NETWORK", (result as UploadResult.Retryable).reason)
        assertEquals(1, http.calls.size)
    }

    @Test
    fun expiredCredentialRefreshesOnceAndRetriesTheOriginalCall() = runBlocking {
        val http = ScriptedTransport()
        val store = MemoryCredentialStore()
        store.save(deviceToken, expiresAtEpochMs = 1L, refreshAfterEpochMs = 1L)
        http.enqueue(200, credentialBody(rotatedToken))
        http.enqueue(201, """{"tracking_session_id":"session-1"}""")
        val tracker = tracker(http, MemoryLog(), store, now = 10L)
        assertTrue(tracker.publishStart(session()) is OutboundResult.Ok)
        assertEquals(TrackerPaths.REFRESH, http.calls[0].path)
        assertEquals("Bearer $deviceToken", http.calls[0].authorization)
        assertEquals(TrackerPaths.SESSIONS, http.calls[1].path)
        assertEquals("Bearer $rotatedToken", http.calls[1].authorization)
        assertEquals(1, http.calls.count { it.path == TrackerPaths.REFRESH })
        assertEquals(rotatedToken, store.read().token)
    }

    @Test
    fun http401ExpiredRefreshesOnceThenRetriesOnce() = runBlocking {
        val http = ScriptedTransport()
        val store = MemoryCredentialStore()
        store.save(deviceToken, expiresAtEpochMs = 9_000L, refreshAfterEpochMs = 9_000L)
        http.enqueue(401, """{"error":"expirada","code":"device_credential_expired"}""")
        http.enqueue(200, credentialBody(rotatedToken))
        http.enqueue(201, """{"tracking_session_id":"session-1"}""")
        val tracker = tracker(http, MemoryLog(), store, now = 1_000L)
        assertTrue(tracker.publishStart(session()) is OutboundResult.Ok)
        assertEquals(listOf(TrackerPaths.SESSIONS, TrackerPaths.REFRESH, TrackerPaths.SESSIONS), http.calls.map { it.path })
        assertEquals("Bearer $rotatedToken", http.calls.last().authorization)
    }

    @Test
    fun revokedDeviceStopsAndDoesNotRegisterAgain() = runBlocking {
        val http = ScriptedTransport()
        val store = MemoryCredentialStore()
        store.save(deviceToken, expiresAtEpochMs = 9_000L, refreshAfterEpochMs = 9_000L)
        http.enqueue(401, """{"error":"revogado","code":"device_revoked"}""")
        http.enqueue(401, """{"error":"revogado","code":"device_revoked"}""")
        val runtime = TrackingRuntime()
        val tracker = tracker(http, MemoryLog(), store, runtime, now = 1_000L)
        val first = tracker.upload(point())
        val second = tracker.upload(point())
        assertTrue(first is UploadResult.Halted)
        assertTrue(second is UploadResult.Halted)
        assertEquals(CredentialState.REVOKED, runtime.credentialState.value)
        assertEquals(1, http.calls.size)
        assertTrue(http.calls.none { it.path == TrackerPaths.REGISTER })
        assertFalse(runtime.deviceRegistered.value)
    }

    @Test
    fun activeSessionIsNotCreatedTwice() = runBlocking {
        val http = ScriptedTransport()
        http.enqueue(201, credentialBody(deviceToken))
        http.enqueue(201, """{"tracking_session_id":"session-1"}""")
        val tracker = tracker(http, MemoryLog())
        val open = session().copy(serverAcknowledged = false)
        assertTrue(tracker.publishStart(open) is OutboundResult.Ok)
        assertTrue(tracker.publishStart(open.copy(serverAcknowledged = true)) is OutboundResult.Ok)
        assertEquals(1, http.calls.count { it.path == TrackerPaths.SESSIONS })
    }

    private fun tracker(
        http: ScriptedTransport,
        log: MemoryLog,
        store: MemoryCredentialStore = MemoryCredentialStore(),
        runtime: TrackingRuntime = TrackingRuntime(),
        now: Long = 1_700_000_000_000L,
    ) = LogisticsTracker(
        transport = http,
        credentials = store,
        bootstrapToken = bootstrap,
        profile = DeviceProfile("1.0.0", "15", "test"),
        log = log,
        diagnostics = runtime,
        status = runtime,
        sessionLookup = { session() },
        clock = { now },
    )

    private fun session() = TrackingSession(
        trackingSessionId = "session-1",
        deviceId = "11111111-1111-4111-8111-111111111111",
        startedAtEpochMs = 1_700_000_000_000L,
        status = SessionStatus.STARTING,
    )

    private fun point() = PendingPoint(
        trackingSessionId = "session-1",
        routeId = "deve-ser-ignorado",
        deviceId = "11111111-1111-4111-8111-111111111111",
        sequence = 1,
        latitude = -23.55,
        longitude = -46.63,
        accuracy = 8f,
        speed = 0f,
        bearing = null,
        altitude = null,
        recordedAt = "2026-10-13T13:00:00.000Z",
        createdAt = 1L,
    )

    private fun credentialBody(token: String) =
        """{"device_id":"11111111-1111-4111-8111-111111111111","device_status":"ACTIVE","device_access_token":"$token","expires_at":"2027-01-11T13:00:00Z","refresh_after":"2026-12-12T13:00:00Z"}"""

    private fun json(body: String, key: String): String {
        val value = JsonParser.parseString(body).asJsonObject.get(key).asJsonPrimitive
        return if (value.isNumber) value.asLong.toString() else value.asString
    }
}

private class ScriptedTransport : TrackerTransport {
    val calls = mutableListOf<RecordedCall>()
    private val responses = ArrayDeque<Response>()

    fun enqueue(code: Int, body: String) {
        responses.addLast(Response(code, body))
    }

    fun failNext(reason: String) {
        responses.addLast(Response(-1, reason))
    }

    override suspend fun post(path: String, authorization: String?, json: String): TransportResponse {
        calls += RecordedCall(path, authorization, json)
        val next = responses.removeFirstOrNull() ?: return TransportResponse(500, """{"code":"empty"}""")
        if (next.code < 0) throw TrackerNetworkException(next.body)
        return TransportResponse(next.code, next.body)
    }

    private data class Response(val code: Int, val body: String)
}

private data class RecordedCall(val path: String, val authorization: String?, val body: String)
