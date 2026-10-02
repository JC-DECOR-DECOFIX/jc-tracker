package br.com.jcdecor.tracker.tracking

import kotlinx.coroutines.flow.MutableStateFlow

class TrackingRuntime : UploadDiagnostics {
    val lastFix = MutableStateFlow<LastFixSnapshot?>(null)
    val playServicesAvailable = MutableStateFlow(true)
    val serviceActive = MutableStateFlow(false)
    val locationUpdatesActive = MutableStateFlow(false)
    val heartbeatActive = MutableStateFlow(false)
    val appInForeground = MutableStateFlow(true)
    val lastUploadAtEpochMs = MutableStateFlow<Long?>(null)
    val lastHttpError = MutableStateFlow<String?>(null)

    override fun onUploadSuccess(atEpochMs: Long) {
        lastUploadAtEpochMs.value = atEpochMs
        lastHttpError.value = null
    }

    override fun onHttpError(message: String) {
        lastHttpError.value = message
    }
}
