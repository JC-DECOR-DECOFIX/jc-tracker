package br.com.jcdecor.tracker.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

data class LocationBody(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("tracking_session_id") val trackingSessionId: String,
    @SerializedName("route_id") val routeId: String?,
    val sequence: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float?,
    val bearing: Float?,
    val altitude: Double?,
    @SerializedName("recorded_at") val recordedAt: String,
)

data class HeartbeatBody(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("recorded_at") val recordedAt: String,
)

interface TrackingApi {
    @POST("api/tracking/routes/{routeId}/locations")
    suspend fun postLocation(
        @Path("routeId") routeId: String,
        @Body body: LocationBody,
        @Header("Authorization") authorization: String?,
    ): Response<Unit>

    @POST("api/tracking/routes/{routeId}/heartbeat")
    suspend fun heartbeat(
        @Path("routeId") routeId: String,
        @Body body: HeartbeatBody,
        @Header("Authorization") authorization: String?,
    ): Response<Unit>
}
