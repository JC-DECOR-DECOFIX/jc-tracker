package br.com.jcdecor.tracker.data.remote

import br.com.jcdecor.tracker.tracking.HeartbeatClient
import br.com.jcdecor.tracker.tracking.LocationUploader
import br.com.jcdecor.tracker.tracking.PendingPoint
import br.com.jcdecor.tracker.tracking.TrackerLog
import br.com.jcdecor.tracker.tracking.UploadResult
import br.com.jcdecor.tracker.util.Formats

class MockLocationUploader(
    private val log: TrackerLog,
) : LocationUploader {
    override suspend fun upload(point: PendingPoint): UploadResult {
        log.info(
            "MOCK location route=${point.routeId} seq=${point.sequence} " +
                "lat=${Formats.coordinate(point.latitude)} lng=${Formats.coordinate(point.longitude)} " +
                "accuracy=${Formats.accuracy(point.accuracy)} speed=${point.speed} " +
                "bearing=${point.bearing} altitude=${point.altitude} recorded_at=${point.recordedAt} " +
                "device_id=${point.deviceId}",
        )
        return UploadResult.Success
    }
}

class MockHeartbeatClient(
    private val log: TrackerLog,
) : HeartbeatClient {
    override suspend fun send(routeId: String, deviceId: String, recordedAt: String) {
        log.info("tracking heartbeat route=$routeId")
    }
}

class RetryableUploader(
    private val reason: String,
) : LocationUploader {
    override suspend fun upload(point: PendingPoint): UploadResult = UploadResult.Retryable(reason)
}

class RetryableHeartbeat : HeartbeatClient {
    override suspend fun send(routeId: String, deviceId: String, recordedAt: String) = Unit
}
