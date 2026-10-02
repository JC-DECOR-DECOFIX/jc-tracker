package br.com.jcdecor.tracker.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugReportTest {
    @Test
    fun debugReportDoesNotExposeApiToken() {
        val text = buildDebugReport(
            DebugSnapshot(
                trackingState = "TRACKING",
                serviceActive = true,
                trackingSessionId = "session-1",
                routeId = null,
                deviceId = "dev-1",
                lastLocationAgeSeconds = 4,
                accuracyMeters = 8.4f,
                networkOnline = true,
                pendingLocations = 0,
                batteryOptimization = false,
                lastHttpError = null,
            ),
        )
        assertTrue(text.contains("tracking_state=TRACKING"))
        assertTrue(text.contains("service_active=true"))
        assertTrue(text.contains("tracking_session_id=session-1"))
        assertTrue(text.contains("route_id=null"))
        assertTrue(text.contains("device_id=dev-1"))
        assertTrue(text.contains("last_location_age=4s"))
        assertTrue(text.contains("accuracy=8.4"))
        assertTrue(text.contains("network=ONLINE"))
        assertTrue(text.contains("pending_locations=0"))
        assertTrue(text.contains("battery_optimization=false"))
        assertTrue(text.contains("last_http_error=null"))
        assertFalse(text.contains("API_TOKEN"))
        assertFalse(text.contains("Authorization"))
        assertFalse(text.contains("Bearer"))
    }
}
