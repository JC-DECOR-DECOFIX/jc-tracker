package br.com.jcdecor.tracker.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugReportTest {
    @Test
    fun debugReportShowsOperationalStateAndHidesSecrets() {
        val bootstrap = "jcsvc_examplepublic_not-a-real-secret"
        val device = "jctdev_examplepublic_not-a-real-secret"
        val text = buildDebugReport(
            DebugSnapshot(
                trackingMode = "REAL",
                deviceRegistered = true,
                deviceId = "dev-1",
                credentialState = "valid",
                trackingState = "TRACKING",
                serviceActive = true,
                trackingSessionId = "session-1",
                routeId = null,
                lastLocationAgeSeconds = 4,
                accuracyMeters = 8.4f,
                lastGps = "-23.5,-46.6",
                networkOnline = true,
                pendingLocations = 0,
                batteryOptimization = false,
                lastUploadAtEpochMs = 1_700_000_000_000L,
                lastHeartbeatAtEpochMs = 1_700_000_000_000L,
                lastHttpError = "Authorization: Bearer $bootstrap $device",
            ),
        )
        assertTrue(text.contains("tracking_mode=REAL"))
        assertTrue(text.contains("device_registered=yes"))
        assertTrue(text.contains("device_id=dev-1"))
        assertTrue(text.contains("credential_state=valid"))
        assertTrue(text.contains("tracking_session_id=session-1"))
        assertTrue(text.contains("session_status=TRACKING"))
        assertTrue(text.contains("network=ONLINE"))
        assertTrue(text.contains("pending_locations=0"))
        assertTrue(text.contains("last_gps=-23.5,-46.6"))
        assertTrue(text.contains("last_successful_upload="))
        assertTrue(text.contains("last_heartbeat="))
        assertTrue(text.contains("last_api_error="))
        assertTrue(text.contains("jcsvc_<redacted>"))
        assertTrue(text.contains("jctdev_<redacted>"))
        assertFalse(text.contains(bootstrap))
        assertFalse(text.contains(device))
        assertFalse(text.contains("BOOTSTRAP_TOKEN"))
        assertFalse(text.contains("API_TOKEN"))
    }
}
