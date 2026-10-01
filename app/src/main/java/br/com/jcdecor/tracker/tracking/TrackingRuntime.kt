package br.com.jcdecor.tracker.tracking

import kotlinx.coroutines.flow.MutableStateFlow

class TrackingRuntime {
    val lastFix = MutableStateFlow<LastFixSnapshot?>(null)
    val playServicesAvailable = MutableStateFlow(true)
}
