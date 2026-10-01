package br.com.jcdecor.tracker.data.remote

import br.com.jcdecor.tracker.tracking.MemoryLog
import br.com.jcdecor.tracker.tracking.PendingPoint
import br.com.jcdecor.tracker.tracking.UploadResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun httpSuccessDoesNotRetry() {
        assertTrue(UploadResults.fromCode(200) is UploadResult.Success)
        assertTrue(UploadResults.fromCode(204) is UploadResult.Success)
    }

    @Test
    fun httpFailuresStayRetryable() {
        assertEquals("TIMEOUT", (UploadResults.fromCode(408) as UploadResult.Retryable).reason)
        assertEquals("HTTP_500", (UploadResults.fromCode(500) as UploadResult.Retryable).reason)
        assertEquals("HTTP_503", (UploadResults.fromCode(503) as UploadResult.Retryable).reason)
        assertEquals("HTTP_401", (UploadResults.fromCode(401) as UploadResult.Retryable).reason)
        assertEquals("NETWORK", (UploadResults.network() as UploadResult.Retryable).reason)
    }

    @Test
    fun bearerHeaderIsOmittedWhenTokenIsBlank() {
        assertNull(ApiAuth.bearer("  "))
        assertEquals("Bearer abc", ApiAuth.bearer("abc"))
    }

    @Test
    fun mockHeartbeatUsesTheExpectedLogLine() = runBlocking {
        val log = MemoryLog()
        MockHeartbeatClient(log).send("1234", "dev", "2026-10-01T00:00:00.000Z")
        assertEquals("tracking heartbeat route=1234", log.lines.single())
    }

    @Test
    fun mockUploadLogsThePayloadAndNotAToken() = runBlocking {
        val log = MemoryLog()
        val result = MockLocationUploader(log).upload(
            PendingPoint(
                routeId = "1234",
                deviceId = "dev-1",
                sequence = 21,
                latitude = -23.5,
                longitude = -46.6,
                accuracy = 4.8f,
                speed = 1.2f,
                bearing = 10f,
                altitude = 700.0,
                recordedAt = "2026-10-01T12:00:00.000Z",
                createdAt = 10L,
            ),
        )
        assertTrue(result is UploadResult.Success)
        val line = log.lines.single()
        assertTrue(line.contains("seq=21"))
        assertTrue(line.contains("route=1234"))
        assertTrue(line.contains("recorded_at=2026-10-01T12:00:00.000Z"))
        assertTrue(!line.contains("Bearer"))
        assertTrue(!line.contains("API_TOKEN"))
    }
}
