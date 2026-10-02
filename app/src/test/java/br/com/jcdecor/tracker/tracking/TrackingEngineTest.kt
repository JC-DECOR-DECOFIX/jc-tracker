package br.com.jcdecor.tracker.tracking

import br.com.jcdecor.tracker.util.Iso8601
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingEngineTest {
    @Test
    fun sequenceIncrementsOnlyForAcceptedFixes() = runBlocking {
        val harness = harness()
        harness.engine.start(deviceId = "dev-1", trackingSessionId = "session-1")
        harness.engine.onLocation(fix(accuracy = 6f, lat = 0.0), networkAvailable = true)
        harness.engine.onLocation(fix(accuracy = 132f, lat = 0.2), networkAvailable = true)
        harness.engine.onLocation(fix(accuracy = 12f, lat = 0.2), networkAvailable = true)

        assertEquals(2L, harness.engine.session?.sequence)
        assertTrue(harness.log.lines.contains("LOCATION_ACCEPTED seq=1"))
        assertTrue(harness.log.lines.contains("LOCATION_REJECTED accuracy=132"))
        assertTrue(harness.log.lines.contains("LOCATION_ACCEPTED seq=2"))
        assertEquals(0, harness.store.count())
    }

    @Test
    fun buffersWhenOfflineAndKeepsThePointAfterStop() = runBlocking {
        val harness = harness()
        harness.engine.start(deviceId = "dev-1", trackingSessionId = "session-1")
        val outcome = harness.engine.onLocation(fix(accuracy = 8f), networkAvailable = false)
        harness.engine.beginStop()
        harness.engine.finishStop()

        assertTrue(outcome is LocationOutcome.Accepted)
        assertEquals(1, harness.store.count())
        val stored = harness.store.listInSendOrder().single()
        assertEquals(1L, stored.sequence)
        assertNull(stored.routeId)
        assertEquals("session-1", stored.trackingSessionId)
        assertEquals("dev-1", stored.deviceId)
        assertEquals(Iso8601.formatUtc(harness.now), stored.recordedAt)
        assertTrue(harness.log.lines.contains("LOCATION_BUFFERED seq=1 reason=NETWORK"))
        assertTrue(harness.log.lines.contains("TRACKING_STOPPED session=session-1"))
    }

    @Test
    fun removesPointOnlyAfterConfirmedUpload() = runBlocking {
        val harness = harness(results = ArrayDeque(listOf(UploadResult.Retryable("HTTP_500"), UploadResult.Success)))
        harness.engine.start(deviceId = "dev-1", trackingSessionId = "session-1")
        harness.engine.onLocation(fix(accuracy = 5f), networkAvailable = true)
        assertEquals(1, harness.store.count())
        assertTrue(harness.log.lines.contains("LOCATION_BUFFERED seq=1 reason=HTTP_500"))

        harness.now += TrackingConfig.BACKOFF_BASE_MS
        val sent = harness.engine.flush(networkAvailable = true)

        assertEquals(1, sent)
        assertEquals(0, harness.store.count())
        assertTrue(harness.log.lines.contains("LOCATION_SENT seq=1"))
    }

    @Test
    fun doesNotSendLaterPointsBeforeAnOlderFailure() = runBlocking {
        val harness = harness(results = ArrayDeque(listOf(UploadResult.Retryable("HTTP_503"))))
        harness.engine.start(deviceId = "dev-1", trackingSessionId = "session-88")
        harness.engine.onLocation(fix(accuracy = 4f, lat = -23.5), networkAvailable = true)
        harness.engine.onLocation(fix(accuracy = 4f, lat = -23.6), networkAvailable = true)

        assertEquals(listOf(1L), harness.uploader.uploaded)
        assertEquals(2, harness.store.count())
        assertTrue(harness.log.lines.contains("LOCATION_BUFFERED seq=2 reason=ORDER"))

        harness.now += 60_000
        harness.uploader.enqueue(UploadResult.Success, UploadResult.Success)
        val sent = harness.engine.flush(networkAvailable = true)

        assertEquals(2, sent)
        assertEquals(listOf(1L, 1L, 2L), harness.uploader.uploaded)
        assertEquals(0, harness.store.count())
    }

    @Test
    fun pendingBufferSurvivesANewEngineInstance() = runBlocking {
        val store = InMemoryPendingLocationStore()
        val first = engine(store, FakeUploader())
        first.engine.start(deviceId = "dev-1", trackingSessionId = "session-1")
        first.engine.onLocation(fix(accuracy = 9f, recordedAt = first.clock.now), networkAvailable = false)
        val saved = first.engine.session

        val second = engine(store, FakeUploader())
        second.engine.restore(saved)
        assertEquals(1, second.store.count())
        assertEquals(1L, second.engine.session?.sequence)
        assertEquals("session-1", second.engine.session?.trackingSessionId)
        assertNull(second.engine.session?.routeId)
    }

    @Test
    fun interruptedSessionIsRestoredAndBlocksASecondSession() = runBlocking {
        val store = InMemoryPendingLocationStore()
        val first = engine(store, FakeUploader())
        first.engine.start(deviceId = "dev-1", trackingSessionId = "session-1")
        first.engine.onLocation(fix(accuracy = 7f, lat = 1.0, recordedAt = first.clock.now), networkAvailable = true)
        first.engine.markInterrupted()
        val saved = first.engine.session
        assertTrue(saved!!.interrupted)
        assertEquals(1L, saved.sequence)

        val restored = engine(store, FakeUploader())
        restored.engine.restore(saved)
        val blocked = restored.engine.start(deviceId = "dev-1", trackingSessionId = "other-session")
        assertTrue(blocked is StartOutcome.InterruptedPending)
        assertEquals("session-1", restored.engine.session?.trackingSessionId)
        assertNull(restored.engine.session?.routeId)

        val resumed = restored.engine.resume()
        assertTrue(resumed is StartOutcome.Resumed)
        assertFalse(restored.engine.session!!.interrupted)
        restored.engine.onLocation(fix(accuracy = 7f, lat = 1.2, recordedAt = restored.clock.now), networkAvailable = true)
        assertEquals(2L, restored.engine.session?.sequence)
    }

    @Test
    fun refusesTwoSimultaneousSessionsUntilTheFirstStops() = runBlocking {
        val harness = harness()
        val started = harness.engine.start(deviceId = "dev-1", trackingSessionId = "session-a")
        val second = harness.engine.start(deviceId = "dev-2", trackingSessionId = "session-b")

        assertTrue(started is StartOutcome.Started)
        assertTrue(second is StartOutcome.AlreadyActive)
        assertEquals("session-a", harness.engine.session?.trackingSessionId)
        assertNull(harness.engine.session?.routeId)
        assertNull(harness.engine.session?.driverId)
        assertEquals(SessionStatus.STARTING, harness.engine.session?.status)

        harness.engine.beginStop()
        harness.engine.finishStop()
        assertEquals(SessionStatus.STOPPED, harness.engine.session?.status)
        assertTrue(harness.engine.session?.stoppedAtEpochMs != null)
        val again = harness.engine.start(deviceId = "dev-1", trackingSessionId = "session-c")
        assertTrue(again is StartOutcome.Started)
        assertEquals("session-c", harness.engine.session?.trackingSessionId)
        assertEquals(0L, harness.engine.session?.sequence)
    }

    @Test
    fun endingAnInterruptedSessionAllowsANewOneWithoutDeletingTheBuffer() = runBlocking {
        val harness = harness()
        harness.engine.start(deviceId = "dev-1", trackingSessionId = "session-1")
        harness.engine.onLocation(fix(accuracy = 6f), networkAvailable = false)
        harness.engine.markInterrupted()
        harness.engine.dismissInterrupted()
        assertEquals(SessionStatus.STOPPED, harness.engine.session?.status)
        assertEquals(1, harness.store.count())

        val started = harness.engine.start(deviceId = "dev-1", trackingSessionId = "session-2")
        assertTrue(started is StartOutcome.Started)
        assertEquals(1, harness.store.count())
    }

    @Test
    fun startsWithoutRouteIdAndLogsTheSession() = runBlocking {
        val harness = harness()
        val started = harness.engine.start(deviceId = "dev-1", trackingSessionId = "session-1")
        assertTrue(started is StartOutcome.Started)
        assertNull(harness.engine.session?.routeId)
        assertNull(harness.engine.session?.driverId)
        assertEquals("session-1", harness.engine.session?.trackingSessionId)
        assertTrue(harness.log.lines.contains("TRACKING_STARTED session=session-1"))
    }

    @Test
    fun restoredActiveSessionSurvivesActivityRecreation() = runBlocking {
        val store = InMemoryPendingLocationStore()
        val first = engine(store, FakeUploader())
        first.engine.start(deviceId = "dev-1", trackingSessionId = "session-live")
        first.engine.markTracking()
        val saved = first.engine.session
        assertEquals(SessionStatus.TRACKING, saved?.status)

        val recreated = engine(store, FakeUploader())
        recreated.engine.restore(saved)
        assertEquals(SessionStatus.TRACKING, recreated.engine.session?.status)
        assertEquals("session-live", recreated.engine.session?.trackingSessionId)
        assertTrue(recreated.engine.session!!.status.isActive())
        val second = recreated.engine.start(deviceId = "dev-1", trackingSessionId = "session-new")
        assertTrue(second is StartOutcome.AlreadyActive)
        assertEquals("session-live", recreated.engine.session?.trackingSessionId)
    }

    @Test
    fun holdsUploadUntilARouteExists() = runBlocking {
        val harness = harness(results = ArrayDeque(listOf(UploadResult.WaitingForRoute)))
        harness.engine.start(deviceId = "dev-1", trackingSessionId = "session-1")
        harness.engine.onLocation(fix(accuracy = 5f), networkAvailable = true)
        assertEquals(1, harness.store.count())
        assertNull(harness.store.listInSendOrder().single().routeId)
        assertTrue(harness.log.lines.contains("LOCATION_BUFFERED seq=1 reason=NO_ROUTE"))
    }

    private fun harness(results: ArrayDeque<UploadResult> = ArrayDeque()): Harness {
        val uploader = FakeUploader(results)
        val built = engine(InMemoryPendingLocationStore(), uploader)
        return Harness(built.engine, built.store, built.log, uploader, built.clock)
    }

    private fun engine(store: InMemoryPendingLocationStore, uploader: FakeUploader): Built {
        var clockNow = 1_700_000_000_000L
        val log = MemoryLog()
        val engine = TrackingEngine(
            store = store,
            uploader = uploader,
            clock = { clockNow },
            log = log,
        )
        return Built(engine, store, log, uploader, object : MutableClock {
            override var now: Long
                get() = clockNow
                set(value) { clockNow = value }
        })
    }

    private fun fix(
        accuracy: Float,
        lat: Double = -23.5505,
        recordedAt: Long = 1_700_000_000_000L,
    ) = RawFix(
        latitude = lat,
        longitude = -46.6333,
        accuracyMeters = accuracy,
        speedMetersPerSecond = 4f,
        bearingDegrees = 90f,
        altitudeMeters = 760.0,
        recordedAtEpochMs = recordedAt,
    )

    private class Harness(
        val engine: TrackingEngine,
        val store: InMemoryPendingLocationStore,
        val log: MemoryLog,
        val uploader: FakeUploader,
        private val clock: MutableClock,
    ) {
        var now: Long
            get() = clock.now
            set(value) { clock.now = value }
    }

    private class Built(
        val engine: TrackingEngine,
        val store: InMemoryPendingLocationStore,
        val log: MemoryLog,
        val uploader: FakeUploader,
        val clock: MutableClock,
    )

    private interface MutableClock {
        var now: Long
    }
}

private class FakeUploader(
    private val results: ArrayDeque<UploadResult> = ArrayDeque(),
) : LocationUploader {
    val uploaded = mutableListOf<Long>()

    fun enqueue(vararg next: UploadResult) {
        next.forEach { results.addLast(it) }
    }

    override suspend fun upload(point: PendingPoint): UploadResult {
        uploaded += point.sequence
        return if (results.isEmpty()) UploadResult.Success else results.removeFirst()
    }
}
