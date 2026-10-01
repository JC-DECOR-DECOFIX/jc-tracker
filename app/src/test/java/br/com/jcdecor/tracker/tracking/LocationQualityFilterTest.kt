package br.com.jcdecor.tracker.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationQualityFilterTest {
    private val now = 1_700_000_000_000L
    private val filter = LocationQualityFilter()

    @Test
    fun acceptsAccuracyAtTheLimit() {
        assertNull(filter.rejection(50f, now, now))
        assertNull(filter.rejection(4.8f, now, now))
        assertNull(filter.rejection(0f, now, now))
    }

    @Test
    fun rejectsAccuracyAboveFiftyMeters() {
        assertEquals(RejectReason.LOW_ACCURACY, filter.rejection(50.1f, now, now))
        assertEquals(RejectReason.LOW_ACCURACY, filter.rejection(96f, now, now))
        assertEquals(RejectReason.LOW_ACCURACY, filter.rejection(132f, now, now))
    }

    @Test
    fun rejectsMissingAccuracy() {
        assertEquals(RejectReason.LOW_ACCURACY, filter.rejection(-1f, now, now))
    }

    @Test
    fun rejectsClearlyOldReadings() {
        val old = now - TrackingConfig.MAX_LOCATION_AGE_MS - 1
        assertEquals(RejectReason.STALE, filter.rejection(8f, old, now))
    }

    @Test
    fun acceptsReadingInsideTheAgeWindow() {
        val recent = now - TrackingConfig.MAX_LOCATION_AGE_MS
        assertNull(filter.rejection(8f, recent, now))
    }

    @Test
    fun rejectsReadingTooFarInTheFuture() {
        val future = now + TrackingConfig.MAX_FUTURE_SKEW_MS + 1
        assertEquals(RejectReason.STALE, filter.rejection(8f, future, now))
    }
}
