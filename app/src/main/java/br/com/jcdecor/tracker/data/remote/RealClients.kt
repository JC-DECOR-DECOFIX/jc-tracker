package br.com.jcdecor.tracker.data.remote

import br.com.jcdecor.tracker.tracking.HeartbeatClient
import br.com.jcdecor.tracker.tracking.LocationUploader
import br.com.jcdecor.tracker.tracking.PendingPoint
import br.com.jcdecor.tracker.tracking.TrackerLog
import br.com.jcdecor.tracker.tracking.TrackingConfig
import br.com.jcdecor.tracker.tracking.UploadDiagnostics
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
    private val diagnostics: UploadDiagnostics? = null,
) : LocationUploader {
    override suspend fun upload(point: PendingPoint): UploadResult {
        if (!RouteGate.canPost(point.routeId)) {
            return UploadResult.WaitingForRoute
        }
        val routeId = point.routeId ?: return UploadResult.WaitingForRoute
        return try {
            val response = api.postLocation(
                routeId = routeId,
                body = LocationBody(
                    deviceId = point.deviceId,
                    trackingSessionId = point.trackingSessionId,
                    routeId = point.routeId,
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
            if (result is UploadResult.Success) {
                diagnostics?.onUploadSuccess(System.currentTimeMillis())
            } else {
                val reason = (result as? UploadResult.Retryable)?.reason ?: "HTTP_${response.code()}"
                diagnostics?.onHttpError(reason)
                log.info("HTTP location seq=${point.sequence} code=${response.code()}")
            }
            result
        } catch (exception: IOException) {
            diagnostics?.onHttpError(exception.javaClass.simpleName)
            log.info("HTTP location seq=${point.sequence} failure=${exception.javaClass.simpleName}")
            UploadResults.network()
        }
    }
}

class RealHeartbeatClient(
    private val api: TrackingApi,
    private val token: String,
    private val log: TrackerLog,
    private val diagnostics: UploadDiagnostics? = null,
) : HeartbeatClient {
    override suspend fun send(
        trackingSessionId: String,
        routeId: String?,
        deviceId: String,
        recordedAt: String,
    ) {
        val postedRoute = routeId?.takeIf { RouteGate.canPost(it) } ?: return
        try {
            val response = api.heartbeat(
                routeId = postedRoute,
                body = HeartbeatBody(deviceId = deviceId, recordedAt = recordedAt),
                authorization = ApiAuth.bearer(token),
            )
            if (!response.isSuccessful) {
                diagnostics?.onHttpError("HTTP_${response.code()}")
                log.info("HEARTBEAT_FAILED session=$trackingSessionId code=${response.code()}")
            }
        } catch (exception: IOException) {
            diagnostics?.onHttpError(exception.javaClass.simpleName)
            log.info("HEARTBEAT_FAILED session=$trackingSessionId failure=${exception.javaClass.simpleName}")
        }
    }
}

object TrackingClients {
    fun locationUploader(
        mode: String,
        baseUrl: String,
        token: String,
        log: TrackerLog,
        diagnostics: UploadDiagnostics? = null,
    ): LocationUploader {
        if (!mode.equals("REAL", ignoreCase = true)) {
            return MockLocationUploader(log, diagnostics)
        }
        val api = createApi(baseUrl, log) ?: return RetryableUploader("CONFIG")
        return RealLocationUploader(api, token, log, diagnostics)
    }

    fun heartbeat(
        mode: String,
        baseUrl: String,
        token: String,
        log: TrackerLog,
        diagnostics: UploadDiagnostics? = null,
    ): HeartbeatClient {
        if (!mode.equals("REAL", ignoreCase = true)) {
            return MockHeartbeatClient(log)
        }
        val api = createApi(baseUrl, log) ?: return RetryableHeartbeat()
        return RealHeartbeatClient(api, token, log, diagnostics)
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
