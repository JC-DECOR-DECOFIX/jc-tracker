package br.com.jcdecor.tracker.tracking

/**
 * Constantes de rastreamento. Ajuste aqui o filtro, o intervalo e a distância mínima.
 */
object TrackingConfig {
    const val LOG_TAG = "JCTracker"

    const val LOCATION_INTERVAL_MS = 5_000L
    const val MIN_UPDATE_INTERVAL_MS = 5_000L
    const val MIN_DISTANCE_METERS = 10f
    const val MAX_ACCEPTABLE_ACCURACY_METERS = 50f
    const val MAX_LOCATION_AGE_MS = 30_000L
    const val MAX_FUTURE_SKEW_MS = 5_000L

    /** Reenvia ponto parado se a última aceita for mais antiga que isto. */
    const val MIN_RESEND_INTERVAL_MS = 20_000L

    const val HEARTBEAT_INTERVAL_MS = 30_000L
    const val FLUSH_INTERVAL_MS = 15_000L
    const val STATIONARY_REFRESH_MS = 20_000L
    const val STOP_FLUSH_TIMEOUT_MS = 8_000L

    const val NOTIFICATION_ID = 42
    const val NOTIFICATION_CHANNEL_ID = "jc_tracker_tracking"

    const val BACKOFF_BASE_MS = 1_000L
    const val BACKOFF_MAX_MS = 60_000L
}
