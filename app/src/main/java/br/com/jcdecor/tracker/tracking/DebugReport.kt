package br.com.jcdecor.tracker.tracking

import br.com.jcdecor.tracker.util.Formats

data class DebugSnapshot(
    val trackingState: String,
    val serviceActive: Boolean,
    val trackingSessionId: String?,
    val routeId: String?,
    val deviceId: String?,
    val lastLocationAgeSeconds: Long?,
    val accuracyMeters: Float?,
    val networkOnline: Boolean,
    val pendingLocations: Int,
    val batteryOptimization: Boolean,
    val lastHttpError: String?,
)

fun buildDebugReport(snapshot: DebugSnapshot): String = buildString {
    appendLine("JC TRACKER DEBUG")
    appendLine("tracking_state=${snapshot.trackingState}")
    appendLine("service_active=${snapshot.serviceActive}")
    appendLine("tracking_session_id=${snapshot.trackingSessionId ?: "null"}")
    appendLine("route_id=${snapshot.routeId ?: "null"}")
    appendLine("device_id=${snapshot.deviceId ?: "null"}")
    appendLine("last_location_age=${snapshot.lastLocationAgeSeconds?.let { "${it}s" } ?: "null"}")
    appendLine("accuracy=${snapshot.accuracyMeters?.let(Formats::accuracy) ?: "null"}")
    appendLine("network=${if (snapshot.networkOnline) "ONLINE" else "OFFLINE"}")
    appendLine("pending_locations=${snapshot.pendingLocations}")
    appendLine("battery_optimization=${snapshot.batteryOptimization}")
    append("last_http_error=${snapshot.lastHttpError ?: "null"}")
}
