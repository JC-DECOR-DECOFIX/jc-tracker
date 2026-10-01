package br.com.jcdecor.tracker.data.remote

import br.com.jcdecor.tracker.tracking.HeartbeatClient
import br.com.jcdecor.tracker.tracking.LocationUploader
import br.com.jcdecor.tracker.tracking.PendingPoint
import br.com.jcdecor.tracker.tracking.TrackerLog
import br.com.jcdecor.tracker.tracking.TrackingConfig
import br.com.jcdecor.tracker.tracking.UploadResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.GsonBuilder

class RealLocationUploader(
    private val api: TrackingApi,
    private val token: String,
    private val log: TrackerLog,
) : LocationUploader {
    override suspend fun upload(point: PendingPoint): UploadResult {
        return try {
            val response = api.postLocation(
                routeId = point.routeId,
                body = LocationBody(
                    deviceId = point.deviceId,
                    sequence = point.sequence,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    accuracy = point.accuracy,
                    speed = point.speed,
                    bearing = point.bearing,
                    altitude = point.altitude,
                    recordedAt = point.recordedAt,
                ),
                authorization = ApiAuth.bearer(token),
            )
            val result = UploadResults.fromCode(response.code())
            if (result !is UploadResult.Success) {
                log.info("HTTP location route=${point.routeId} seq=${point.sequence} code=${response.code()}")
            }
            result
        } catch (exception: IOException) {
            log.info("HTTP location route=${point.routeId} seq=${point.sequence} failure=${exception.javaClass.simpleName}")
            UploadResults.network()
        }
    }
}

class RealHeartbeatClient(
    private val api: TrackingApi,
    private val token: String,
    private val log: TrackerLog,
) : HeartbeatClient {
    override suspend fun send(routeId: String, deviceId: String, recordedAt: String) {
        try {
            val response = api.heartbeat(
                routeId = routeId,
                body = HeartbeatBody(deviceId = deviceId, recordedAt = recordedAt),
                authorization = ApiAuth.bearer(token),
            )
            if (!response.isSuccessful) {
                log.info("HEARTBEAT_FAILED route=$routeId code=${response.code()}")
            }
        } catch (exception: IOException) {
            log.info("HEARTBEAT_FAILED route=$routeId failure=${exception.javaClass.simpleName}")
        }
    }
}

object TrackingClients {
    fun locationUploader(mode: String, baseUrl: String, token: String, log: TrackerLog): LocationUploader {
        if (!mode.equals("REAL", ignoreCase = true)) {
            return MockLocationUploader(log)
        }
        val api = createApi(baseUrl, log) ?: return RetryableUploader("CONFIG")
        return RealLocationUploader(api, token, log)
    }

    fun heartbeat(mode: String, baseUrl: String, token: String, log: TrackerLog): HeartbeatClient {
        if (!mode.equals("REAL", ignoreCase = true)) {
            return MockHeartbeatClient(log)
        }
        val api = createApi(baseUrl, log) ?: return RetryableHeartbeat()
        return RealHeartbeatClient(api, token, log)
    }

    private fun createApi(baseUrl: String, log: TrackerLog): TrackingApi? {
        val normalized = baseUrl.trim()
        if (!normalized.startsWith("https://")) {
            log.info("API_BASE_URL recusada: use HTTPS")
            return null
        }
        val withSlash = if (normalized.endsWith("/")) normalized else "$normalized/"
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val response = chain.proceed(request)
                    log.info("HTTP ${request.method} ${request.url.encodedPath} -> ${response.code}")
                    response
                }
                .build()
            Retrofit.Builder()
                .baseUrl(withSlash)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
                .build()
                .create(TrackingApi::class.java)
        } catch (exception: IllegalArgumentException) {
            log.info("API_BASE_URL invalida (${exception.javaClass.simpleName})")
            null
        }
    }

    fun logTag(): String = TrackingConfig.LOG_TAG
}
