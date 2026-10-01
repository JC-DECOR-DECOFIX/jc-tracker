package br.com.jcdecor.tracker.tracking

class LocationQualityFilter(
    val maxAcceptableAccuracyMeters: Float = TrackingConfig.MAX_ACCEPTABLE_ACCURACY_METERS,
    val maxAgeMillis: Long = TrackingConfig.MAX_LOCATION_AGE_MS,
    val maxFutureSkewMillis: Long = TrackingConfig.MAX_FUTURE_SKEW_MS,
) {
    /**
     * accuracy <= limite é aceitável. Acima disso, ou leitura antiga/futura, é rejeitada.
     * accuracy negativa significa que o provedor não informou precisão.
     */
    fun rejection(accuracyMeters: Float, recordedAtEpochMs: Long, nowEpochMs: Long): RejectReason? {
        if (accuracyMeters < 0f || accuracyMeters > maxAcceptableAccuracyMeters) {
            return RejectReason.LOW_ACCURACY
        }
        val age = nowEpochMs - recordedAtEpochMs
        if (age > maxAgeMillis || recordedAtEpochMs - nowEpochMs > maxFutureSkewMillis) {
            return RejectReason.STALE
        }
        return null
    }
}
