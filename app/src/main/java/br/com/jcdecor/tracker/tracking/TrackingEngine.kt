package br.com.jcdecor.tracker.tracking

import br.com.jcdecor.tracker.util.Formats
import br.com.jcdecor.tracker.util.Geo
import br.com.jcdecor.tracker.util.Iso8601

class TrackingEngine(
    private val store: PendingLocationStore,
    private val uploader: LocationUploader,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val filter: LocationQualityFilter = LocationQualityFilter(),
    private val log: TrackerLog = NoOpTrackerLog,
    private val formatRecordedAt: (Long) -> String = Iso8601::formatUtc,
    private val minResendIntervalMs: Long = TrackingConfig.MIN_RESEND_INTERVAL_MS,
    private val minDistanceMeters: Double = TrackingConfig.MIN_DISTANCE_METERS.toDouble(),
    private val backoffBaseMs: Long = TrackingConfig.BACKOFF_BASE_MS,
    private val backoffMaxMs: Long = TrackingConfig.BACKOFF_MAX_MS,
) {
    var session: TrackingSession? = null
        private set

    private val nextAttemptAt = mutableMapOf<Long, Long>()

    fun restore(saved: TrackingSession?) {
        session = saved?.takeUnless { it.status == SessionStatus.IDLE || it.status == SessionStatus.STOPPED }
    }

    fun start(deviceId: String, trackingSessionId: String): StartOutcome {
        val current = session
        if (current != null && current.interrupted && current.status.isActive()) {
            return StartOutcome.InterruptedPending(current)
        }
        if (current != null && current.status.isActive()) {
            return StartOutcome.AlreadyActive(current)
        }
        val created = TrackingSession(
            trackingSessionId = trackingSessionId,
            deviceId = deviceId,
            startedAtEpochMs = clock(),
            routeId = null,
            driverId = null,
            status = SessionStatus.STARTING,
        )
        session = created
        log.info("TRACKING_STARTED session=$trackingSessionId")
        return StartOutcome.Started(created)
    }

    fun resume(): StartOutcome {
        val current = session ?: return StartOutcome.InvalidRoute("Nenhuma sessão para retomar.")
        if (!current.interrupted) {
            return if (current.status.isActive()) {
                StartOutcome.AlreadyActive(current)
            } else {
                StartOutcome.InvalidRoute("Nenhuma sessão interrompida.")
            }
        }
        val resumed = current.copy(status = SessionStatus.STARTING, interrupted = false)
        session = resumed
        log.info("TRACKING_STARTED session=${resumed.trackingSessionId}")
        return StartOutcome.Resumed(resumed)
    }

    fun markTracking() {
        val current = session ?: return
        if (current.status == SessionStatus.STARTING) {
            session = current.copy(status = SessionStatus.TRACKING)
        }
    }

    fun updateDegraded(gpsAvailable: Boolean, networkAvailable: Boolean, lowAccuracy: Boolean) {
        val current = session ?: return
        if (current.status != SessionStatus.TRACKING &&
            current.status != SessionStatus.DEGRADED &&
            current.status != SessionStatus.STARTING
        ) {
            return
        }
        val degraded = !gpsAvailable || !networkAvailable || lowAccuracy
        val status = if (degraded) SessionStatus.DEGRADED else SessionStatus.TRACKING
        if (current.status != status) {
            session = current.copy(status = status)
        }
    }

    fun markInterrupted(): TrackingSession? {
        val current = session ?: return null
        if (!current.status.isActive() || current.interrupted) return current
        val updated = current.copy(interrupted = true)
        session = updated
        return updated
    }

    fun dismissInterrupted(): TrackingSession? {
        val current = session ?: return null
        if (current.status == SessionStatus.STOPPED) return current
        val stopped = current.copy(
            status = SessionStatus.STOPPED,
            interrupted = false,
            stoppedAtEpochMs = clock(),
        )
        session = stopped
        log.info("TRACKING_STOPPED session=${stopped.trackingSessionId}")
        return stopped
    }

    fun beginStop(): TrackingSession? {
        val current = session ?: return null
        if (current.status == SessionStatus.STOPPED || current.status == SessionStatus.IDLE) return current
        val stopping = current.copy(status = SessionStatus.STOPPING, interrupted = false)
        session = stopping
        return stopping
    }

    fun finishStop(): TrackingSession? {
        val current = session ?: return null
        if (current.status == SessionStatus.STOPPED) return current
        val stopped = current.copy(
            status = SessionStatus.STOPPED,
            interrupted = false,
            stoppedAtEpochMs = clock(),
        )
        session = stopped
        log.info("TRACKING_STOPPED session=${stopped.trackingSessionId}")
        return stopped
    }

    suspend fun onLocation(fix: RawFix, networkAvailable: Boolean): LocationOutcome {
        val current = session ?: return LocationOutcome.NoActiveSession
        if (current.interrupted || !current.status.isActive() || current.status == SessionStatus.STOPPING) {
            return LocationOutcome.NoActiveSession
        }
        log.info("LOCATION_RECEIVED accuracy=${Formats.accuracy(fix.accuracyMeters)}")
        val now = clock()
        val rejection = filter.rejection(fix.accuracyMeters, fix.recordedAtEpochMs, now)
        if (rejection != null) {
            if (rejection == RejectReason.LOW_ACCURACY) {
                log.info("LOCATION_REJECTED accuracy=${Formats.accuracy(fix.accuracyMeters)}")
                session = current.copy(
                    lastLocationAtEpochMs = now,
                    status = SessionStatus.DEGRADED,
                )
            } else {
                log.info("LOCATION_REJECTED reason=STALE")
                session = current.copy(lastLocationAtEpochMs = now)
            }
            return LocationOutcome.Rejected(rejection, fix.accuracyMeters)
        }
        if (isRedundant(current, fix)) {
            session = current.copy(lastLocationAtEpochMs = now)
            return LocationOutcome.DisplayOnly(reason = "DISTANCE")
        }
        val sequence = current.sequence + 1
        session = current.copy(
            sequence = sequence,
            lastLocationAtEpochMs = now,
            status = if (networkAvailable) SessionStatus.TRACKING else SessionStatus.DEGRADED,
            lastAcceptedLatitude = fix.latitude,
            lastAcceptedLongitude = fix.longitude,
            lastAcceptedAtEpochMs = fix.recordedAtEpochMs,
        )
        log.info("LOCATION_ACCEPTED seq=$sequence")
        val hadOlder = store.listInSendOrder().isNotEmpty()
        store.insert(
            PendingPoint(
                trackingSessionId = session!!.trackingSessionId,
                routeId = session!!.routeId,
                deviceId = session!!.deviceId,
                sequence = sequence,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracy = fix.accuracyMeters,
                speed = fix.speedMetersPerSecond,
                bearing = fix.bearingDegrees,
                altitude = fix.altitudeMeters,
                recordedAt = formatRecordedAt(fix.recordedAtEpochMs),
                createdAt = now,
            ),
        )
        if (!networkAvailable) {
            log.info("LOCATION_BUFFERED seq=$sequence reason=NETWORK")
            return LocationOutcome.Accepted(sequence, sent = false, buffered = true, bufferReason = "NETWORK")
        }
        flush(networkAvailable = true)
        val stillThere = store.listInSendOrder().any {
            it.sequence == sequence && it.trackingSessionId == session?.trackingSessionId
        }
        if (stillThere && hadOlder) {
            log.info("LOCATION_BUFFERED seq=$sequence reason=ORDER")
        }
        return LocationOutcome.Accepted(
            sequence = sequence,
            sent = !stillThere,
            buffered = stillThere,
            bufferReason = when {
                !stillThere -> null
                hadOlder -> "ORDER"
                else -> "BACKEND"
            },
        )
    }

    suspend fun flush(networkAvailable: Boolean): Int {
        if (!networkAvailable) return 0
        var sent = 0
        while (true) {
            val next = store.listInSendOrder().firstOrNull() ?: break
            if (!backoffElapsed(next)) break
            when (val result = uploader.upload(next)) {
                UploadResult.Success -> {
                    store.deleteById(next.id)
                    nextAttemptAt.remove(next.id)
                    log.info("LOCATION_SENT seq=${next.sequence}")
                    sent++
                }
                UploadResult.WaitingForRoute -> {
                    log.info("LOCATION_BUFFERED seq=${next.sequence} reason=NO_ROUTE")
                    break
                }
                is UploadResult.Retryable -> {
                    store.incrementRetry(next.id)
                    val shift = next.retryCount.coerceAtMost(5)
                    val delay = (backoffBaseMs shl shift).coerceAtMost(backoffMaxMs)
                    nextAttemptAt[next.id] = clock() + delay
                    log.info("LOCATION_BUFFERED seq=${next.sequence} reason=${result.reason}")
                    break
                }
            }
        }
        return sent
    }

    private fun backoffElapsed(point: PendingPoint): Boolean {
        val next = nextAttemptAt[point.id] ?: return true
        return clock() >= next
    }

    private fun isRedundant(current: TrackingSession, fix: RawFix): Boolean {
        val lat = current.lastAcceptedLatitude ?: return false
        val lng = current.lastAcceptedLongitude ?: return false
        val at = current.lastAcceptedAtEpochMs ?: return false
        val moved = Geo.distanceMeters(lat, lng, fix.latitude, fix.longitude)
        val elapsed = fix.recordedAtEpochMs - at
        return moved < minDistanceMeters && elapsed < minResendIntervalMs
    }
}
