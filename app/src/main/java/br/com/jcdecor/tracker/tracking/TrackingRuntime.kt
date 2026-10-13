package br.com.jcdecor.tracker.tracking

import kotlinx.coroutines.flow.MutableStateFlow

class TrackingRuntime : UploadDiagnostics, TrackerStatus {
    val lastFix = MutableStateFlow<LastFixSnapshot?>(null)
    val playServicesAvailable = MutableStateFlow(true)
    val serviceActive = MutableStateFlow(false)
    val locationUpdatesActive = MutableStateFlow(false)
    val heartbeatActive = MutableStateFlow(false)
    val appInForeground = MutableStateFlow(true)
    val lastUploadAtEpochMs = MutableStateFlow<Long?>(null)
    val lastHeartbeatAtEpochMs = MutableStateFlow<Long?>(null)
    val lastHttpError = MutableStateFlow<String?>(null)
    val credentialState = MutableStateFlow(CredentialState.MISSING)
    val deviceRegistered = MutableStateFlow(false)

    override fun onUploadSuccess(atEpochMs: Long) {
        lastUploadAtEpochMs.value = atEpochMs
        lastHttpError.value = null
    }

    override fun onHttpError(message: String) {
        lastHttpError.value = DebugSanitizer.sanitize(message)
    }

    override fun onCredential(state: CredentialState, registered: Boolean) {
        credentialState.value = state
        deviceRegistered.value = registered
    }

    override fun onHeartbeatSuccess(atEpochMs: Long) {
        lastHeartbeatAtEpochMs.value = atEpochMs
    }
}
