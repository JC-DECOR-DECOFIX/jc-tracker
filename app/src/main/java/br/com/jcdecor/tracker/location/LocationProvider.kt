package br.com.jcdecor.tracker.location

import br.com.jcdecor.tracker.tracking.RawFix

enum class LocationStartResult {
    STARTED,
    MISSING_PERMISSION,
    PLAY_SERVICES_UNAVAILABLE,
}

interface LocationProvider {
    fun start(onUpdate: (RawFix) -> Unit): LocationStartResult
    fun requestSingle(onUpdate: (RawFix) -> Unit)
    fun stop()
}
