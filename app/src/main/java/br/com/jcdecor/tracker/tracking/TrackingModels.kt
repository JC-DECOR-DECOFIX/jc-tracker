package br.com.jcdecor.tracker.tracking

enum class SessionStatus {
    IDLE,
    STARTING,
    TRACKING,
    DEGRADED,
    STOPPING,
    STOPPED,
}

fun SessionStatus.isActive(): Boolean =
    this == SessionStatus.STARTING ||
        this == SessionStatus.TRACKING ||
        this == SessionStatus.DEGRADED ||
        this == SessionStatus.STOPPING

data class TrackingSession(
    val routeId: String,
    val deviceId: String,
    val startedAtEpochMs: Long,
    val lastLocationAtEpochMs: Long?,
    val sequence: Long,
    val status: SessionStatus,
    val interrupted: Boolean = false,
    val stoppedAtEpochMs: Long? = null,
    val lastAcceptedLatitude: Double? = null,
    val lastAcceptedLongitude: Double? = null,
    val lastAcceptedAtEpochMs: Long? = null,
)

data class RawFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val altitudeMeters: Double?,
    val recordedAtEpochMs: Long,
)

data class LastFixSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val recordedAtEpochMs: Long,
    val receivedAtEpochMs: Long,
    val acceptable: Boolean,
)

data class PendingPoint(
    val id: Long = 0,
    val routeId: String,
    val deviceId: String,
    val sequence: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float?,
    val bearing: Float?,
    val altitude: Double?,
    val recordedAt: String,
    val createdAt: Long,
    val retryCount: Int = 0,
)

enum class RejectReason {
    LOW_ACCURACY,
    STALE,
}

sealed class StartOutcome {
    data class Started(val session: TrackingSession) : StartOutcome()
    data class Resumed(val session: TrackingSession) : StartOutcome()
    data class AlreadyActive(val session: TrackingSession) : StartOutcome()
    data class InvalidRoute(val message: String) : StartOutcome()
    data class InterruptedPending(val session: TrackingSession) : StartOutcome()
}

sealed class LocationOutcome {
    data class Accepted(
        val sequence: Long,
        val sent: Boolean,
        val buffered: Boolean,
        val bufferReason: String?,
    ) : LocationOutcome()

    data class Rejected(val reason: RejectReason, val accuracyMeters: Float) : LocationOutcome()
    data class DisplayOnly(val reason: String) : LocationOutcome()
    data object NoActiveSession : LocationOutcome()
}

sealed class UploadResult {
    data object Success : UploadResult()
    data class Retryable(val reason: String) : UploadResult()
}

interface LocationUploader {
    suspend fun upload(point: PendingPoint): UploadResult
}

interface PendingLocationStore {
    suspend fun insert(point: PendingPoint): PendingPoint
    suspend fun listInSendOrder(): List<PendingPoint>
    suspend fun deleteById(id: Long)
    suspend fun incrementRetry(id: Long)
    suspend fun count(): Int
}

interface HeartbeatClient {
    suspend fun send(routeId: String, deviceId: String, recordedAt: String)
}

interface TrackerLog {
    fun info(message: String)
}

object NoOpTrackerLog : TrackerLog {
    override fun info(message: String) = Unit
}
