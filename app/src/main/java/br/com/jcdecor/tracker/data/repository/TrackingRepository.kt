package br.com.jcdecor.tracker.data.repository

import br.com.jcdecor.tracker.data.local.RoomPendingLocationStore
import br.com.jcdecor.tracker.data.local.SessionStore
import br.com.jcdecor.tracker.data.local.DeviceIdStore
import br.com.jcdecor.tracker.tracking.LastFixSnapshot
import br.com.jcdecor.tracker.tracking.LocationOutcome
import br.com.jcdecor.tracker.tracking.RawFix
import br.com.jcdecor.tracker.tracking.RejectReason
import br.com.jcdecor.tracker.tracking.StartOutcome
import br.com.jcdecor.tracker.tracking.TrackingEngine
import br.com.jcdecor.tracker.tracking.TrackingRuntime
import br.com.jcdecor.tracker.tracking.TrackingSession
import br.com.jcdecor.tracker.tracking.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrackingRepository(
    private val engine: TrackingEngine,
    private val sessionStore: SessionStore,
    private val deviceIdStore: DeviceIdStore,
    private val pendingStore: RoomPendingLocationStore,
    private val runtime: TrackingRuntime,
) {
    private val mutex = Mutex()
    private var restored = false

    fun observePendingCount(): Flow<Int> = pendingStore.observeCount()

    fun observeSession(): Flow<TrackingSession?> = sessionStore.session

    fun observeLastFix(): Flow<LastFixSnapshot?> = sessionStore.lastFix

    suspend fun currentSession(): TrackingSession? = mutex.withLock {
        ensureRestoredLocked()
        engine.session
    }

    suspend fun start(routeId: String): StartOutcome = mutex.withLock {
        ensureRestoredLocked()
        val outcome = engine.start(routeId, deviceIdStore.getOrCreate())
        persistLocked(lastFix = null)
        outcome
    }

    suspend fun resume(): StartOutcome = mutex.withLock {
        ensureRestoredLocked()
        val outcome = engine.resume()
        persistLocked(lastFix = null)
        outcome
    }

    suspend fun markTracking() = mutex.withLock {
        ensureRestoredLocked()
        engine.markTracking()
        persistLocked(lastFix = null)
    }

    suspend fun updateDegraded(gpsAvailable: Boolean, networkAvailable: Boolean, lowAccuracy: Boolean) = mutex.withLock {
        ensureRestoredLocked()
        engine.updateDegraded(gpsAvailable, networkAvailable, lowAccuracy)
        persistLocked(lastFix = null)
    }

    suspend fun onLocation(fix: RawFix, networkAvailable: Boolean, receivedAtEpochMs: Long): LocationOutcome = mutex.withLock {
        ensureRestoredLocked()
        val outcome = engine.onLocation(fix, networkAvailable)
        val snapshot = when (outcome) {
            is LocationOutcome.Rejected -> if (outcome.reason == RejectReason.STALE) {
                null
            } else {
                snapshotOf(fix, receivedAtEpochMs, acceptable = false)
            }
            is LocationOutcome.Accepted -> snapshotOf(fix, receivedAtEpochMs, acceptable = true)
            is LocationOutcome.DisplayOnly -> snapshotOf(fix, receivedAtEpochMs, acceptable = true)
            LocationOutcome.NoActiveSession -> null
        }
        if (snapshot != null) {
            runtime.lastFix.value = snapshot
        }
        persistLocked(snapshot)
        outcome
    }

    suspend fun flush(networkAvailable: Boolean): Int = mutex.withLock {
        ensureRestoredLocked()
        engine.flush(networkAvailable)
    }

    suspend fun beginStop() = mutex.withLock {
        ensureRestoredLocked()
        engine.beginStop()
        persistLocked(lastFix = null)
    }

    suspend fun finishStop() = mutex.withLock {
        ensureRestoredLocked()
        engine.finishStop()
        persistLocked(lastFix = null)
    }

    suspend fun markInterruptedFromBoot() = mutex.withLock {
        ensureRestoredLocked()
        engine.markInterrupted()
        persistLocked(lastFix = null)
    }

    suspend fun dismissInterrupted() = mutex.withLock {
        ensureRestoredLocked()
        engine.dismissInterrupted()
        persistLocked(lastFix = null)
    }

    suspend fun isActivelyTracking(): Boolean = mutex.withLock {
        ensureRestoredLocked()
        val current = engine.session
        current != null && current.status.isActive() && !current.interrupted
    }

    private suspend fun ensureRestoredLocked() {
        if (restored) return
        engine.restore(sessionStore.current())
        runtime.lastFix.value = sessionStore.currentLastFix()
        restored = true
    }

    private suspend fun persistLocked(lastFix: LastFixSnapshot?) {
        sessionStore.save(engine.session, lastFix)
    }

    private fun snapshotOf(fix: RawFix, receivedAtEpochMs: Long, acceptable: Boolean) = LastFixSnapshot(
        latitude = fix.latitude,
        longitude = fix.longitude,
        accuracyMeters = fix.accuracyMeters,
        speedMetersPerSecond = fix.speedMetersPerSecond,
        recordedAtEpochMs = fix.recordedAtEpochMs,
        receivedAtEpochMs = receivedAtEpochMs,
        acceptable = acceptable,
    )
}
