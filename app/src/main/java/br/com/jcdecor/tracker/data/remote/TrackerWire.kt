package br.com.jcdecor.tracker.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.time.format.DateTimeParseException

object TrackerPaths {
    const val REGISTER = "api/app-logistica/tracking/devices/register/"
    const val REFRESH = "api/app-logistica/tracking/devices/refresh/"
    const val SESSIONS = "api/app-logistica/tracking/sessions/"
    const val LOCATIONS = "api/app-logistica/tracking/locations/"
    const val HEARTBEAT = "api/app-logistica/tracking/heartbeat/"

    fun stop(trackingSessionId: String): String =
        "api/app-logistica/tracking/sessions/$trackingSessionId/stop/"
}

fun trackerJson(vararg pairs: Pair<String, Any?>): String {
    val obj = JsonObject()
    pairs.forEach { (key, value) ->
        when (value) {
            null -> Unit
            is String -> obj.addProperty(key, value)
            is Boolean -> obj.addProperty(key, value)
            is Int -> obj.addProperty(key, value)
            is Long -> obj.addProperty(key, value)
            is Float -> obj.addProperty(key, value)
            is Double -> obj.addProperty(key, value)
            else -> obj.addProperty(key, value.toString())
        }
    }
    return obj.toString()
}

fun errorCodeOf(body: String): String? = try {
    val element = JsonParser.parseString(body)
    if (!element.isJsonObject) null
    else {
        val obj = element.asJsonObject
        obj.get("code")?.takeIf { it.isJsonPrimitive }?.asString
            ?: obj.get("detail")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("code")?.takeIf { it.isJsonPrimitive }?.asString
    }
} catch (_: Exception) {
    null
}

fun stringField(body: String, key: String): String? = try {
    val obj = JsonParser.parseString(body).asJsonObject
    obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
} catch (_: Exception) {
    null
}

fun parseIsoEpochMs(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

data class TransportResponse(
    val code: Int,
    val body: String,
)

class TrackerNetworkException(message: String) : Exception(message)

interface TrackerTransport {
    suspend fun post(path: String, authorization: String?, json: String): TransportResponse
}
