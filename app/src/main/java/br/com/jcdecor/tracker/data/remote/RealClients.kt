package br.com.jcdecor.tracker.data.remote

import br.com.jcdecor.tracker.data.local.DeviceCredentialStore
import br.com.jcdecor.tracker.tracking.TrackerLog
import br.com.jcdecor.tracker.tracking.TrackerStatus
import br.com.jcdecor.tracker.tracking.TrackingConfig
import br.com.jcdecor.tracker.tracking.TrackingSession
import br.com.jcdecor.tracker.tracking.UploadDiagnostics
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpTrackerTransport(
    private val baseUrl: String,
    private val client: OkHttpClient,
) : TrackerTransport {
    override suspend fun post(path: String, authorization: String?, json: String): TransportResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(json.toRequestBody(JSON))
            .header("Accept", "application/json")
            .apply {
                if (!authorization.isNullOrBlank()) header("Authorization", authorization)
            }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                TransportResponse(response.code, response.body?.string().orEmpty())
            }
        } catch (exception: IOException) {
            throw TrackerNetworkException(exception.javaClass.simpleName)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

object TrackingClients {
    fun realTracker(
        baseUrl: String,
        allowCleartext: Boolean,
        bootstrapToken: String,
        credentials: DeviceCredentialStore,
        profile: DeviceProfile,
        log: TrackerLog,
        diagnostics: UploadDiagnostics?,
        status: TrackerStatus,
        sessionLookup: () -> TrackingSession?,
    ): LogisticsTracker {
        val transport = transportFor(baseUrl, allowCleartext, log)
        return LogisticsTracker(
            transport = transport,
            credentials = credentials,
            bootstrapToken = bootstrapToken,
            profile = profile,
            log = log,
            diagnostics = diagnostics,
            status = status,
            sessionLookup = sessionLookup,
        )
    }

    fun transportFor(baseUrl: String, allowCleartext: Boolean, log: TrackerLog): TrackerTransport {
        val normalized = baseUrl.trim()
        val allowed = normalized.startsWith("https://") || (allowCleartext && normalized.startsWith("http://"))
        if (!allowed) {
            log.info(if (allowCleartext) "API_BASE_URL invalida" else "API_BASE_URL recusada: use HTTPS")
            return FailingTransport("CONFIG")
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
            OkHttpTrackerTransport(withSlash, client)
        } catch (exception: IllegalArgumentException) {
            log.info("API_BASE_URL invalida (${exception.javaClass.simpleName})")
            FailingTransport("CONFIG")
        }
    }

    fun logTag(): String = TrackingConfig.LOG_TAG
}

private class FailingTransport(private val reason: String) : TrackerTransport {
    override suspend fun post(path: String, authorization: String?, json: String): TransportResponse {
        throw TrackerNetworkException(reason)
    }
}
