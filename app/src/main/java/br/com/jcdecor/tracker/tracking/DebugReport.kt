package br.com.jcdecor.tracker.tracking

import br.com.jcdecor.tracker.util.Formats
import br.com.jcdecor.tracker.util.Iso8601

data class DebugSnapshot(
    val trackingMode: String,
    val deviceRegistered: Boolean,
    val deviceId: String?,
    val credentialState: String,
    val trackingState: String,
    val serviceActive: Boolean,
    val trackingSessionId: String?,
    val routeId: String?,
    val lastLocationAgeSeconds: Long?,
    val accuracyMeters: Float?,
    val lastGps: String?,
    val networkOnline: Boolean,
    val pendingLocations: Int,
    val batteryOptimization: Boolean,
    val lastUploadAtEpochMs: Long?,
    val lastHeartbeatAtEpochMs: Long?,
    val lastHttpError: String?,
)

fun buildDebugReport(snapshot: DebugSnapshot): String = DebugSanitizer.sanitize(buildString {
    appendLine("JC TRACKER DEBUG")
    appendLine("tracking_mode=${snapshot.trackingMode.ifBlank { "MOCK" }}")
    appendLine("device_registered=${if (snapshot.deviceRegistered) "yes" else "no"}")
    appendLine("device_id=${snapshot.deviceId ?: "null"}")
    appendLine("credential_state=${snapshot.credentialState}")
    appendLine("tracking_session_id=${snapshot.trackingSessionId ?: "null"}")
    appendLine("session_status=${snapshot.trackingState}")
    appendLine("tracking_state=${snapshot.trackingState}")
    appendLine("service_active=${snapshot.serviceActive}")
    appendLine("route_id=${snapshot.routeId ?: "null"}")
    appendLine("last_gps=${snapshot.lastGps ?: "null"}")
    appendLine("last_location_age=${snapshot.lastLocationAgeSeconds?.let { "${it}s" } ?: "null"}")
    appendLine("accuracy=${snapshot.accuracyMeters?.let(Formats::accuracy) ?: "null"}")
    appendLine("network=${if (snapshot.networkOnline) "ONLINE" else "OFFLINE"}")
    appendLine("pending_locations=${snapshot.pendingLocations}")
    appendLine("last_successful_upload=${snapshot.lastUploadAtEpochMs?.let(Iso8601::formatUtc) ?: "null"}")
    appendLine("last_heartbeat=${snapshot.lastHeartbeatAtEpochMs?.let(Iso8601::formatUtc) ?: "null"}")
    appendLine("battery_optimization=${snapshot.batteryOptimization}")
    append("last_api_error=${snapshot.lastHttpError ?: "null"}")
})
