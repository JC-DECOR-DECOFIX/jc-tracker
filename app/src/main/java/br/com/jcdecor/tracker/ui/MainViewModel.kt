package br.com.jcdecor.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.jcdecor.tracker.TrackerApplication
import br.com.jcdecor.tracker.tracking.DebugSnapshot
import br.com.jcdecor.tracker.tracking.LastFixSnapshot
import br.com.jcdecor.tracker.tracking.TrackingSession
import br.com.jcdecor.tracker.tracking.buildDebugReport
import br.com.jcdecor.tracker.tracking.isActive as isSessionActive
import br.com.jcdecor.tracker.util.BatteryStatus
import br.com.jcdecor.tracker.util.LocationStatus
import br.com.jcdecor.tracker.util.NetworkStatus
import br.com.jcdecor.tracker.util.PermissionStatus
import br.com.jcdecor.tracker.util.PlayServicesStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TrackerUiState(
    val session: TrackingSession? = null,
    val lastFix: LastFixSnapshot? = null,
    val gpsAvailable: Boolean = false,
    val internetAvailable: Boolean = false,
    val hasFineLocation: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val playServicesAvailable: Boolean = true,
    val pendingCount: Int = 0,
    val message: String? = null,
    val nowEpochMs: Long = System.currentTimeMillis(),
    val showDebug: Boolean = false,
    val serviceActive: Boolean = false,
    val locationUpdatesActive: Boolean = false,
    val heartbeatActive: Boolean = false,
    val appInForeground: Boolean = true,
    val batteryRestricted: Boolean = false,
    val lastHttpError: String? = null,
    val lastUploadAtEpochMs: Long? = null,
    val trackingMode: String = "",
    val apiBaseUrl: String = "",
) {
    val running: Boolean
        get() = session?.let { it.status.isSessionActive() && !it.interrupted } == true

    val interrupted: Boolean
        get() = session?.let { it.interrupted && it.status.isSessionActive() } == true
}

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val graph = (application as TrackerApplication).graph
    private val _ui = MutableStateFlow(
        TrackerUiState(
            trackingMode = graph.trackingMode,
            apiBaseUrl = graph.apiBaseUrl,
        ),
    )
    val ui: StateFlow<TrackerUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            graph.repository.observeSession().collect { session ->
                _ui.update { state ->
                    val tracking = session != null && session.status.isSessionActive() && !session.interrupted
                    state.copy(session = session, message = if (tracking) null else state.message)
                }
            }
        }
        viewModelScope.launch {
            graph.repository.observeLastFix().collect { stored -> publishFix(stored) }
        }
        viewModelScope.launch {
            graph.runtime.lastFix.collect { live -> publishFix(live) }
        }
        viewModelScope.launch {
            graph.repository.observePendingCount().collect { count ->
                _ui.update { it.copy(pendingCount = count) }
            }
        }
        viewModelScope.launch {
            graph.runtime.playServicesAvailable.collect { available ->
                _ui.update { it.copy(playServicesAvailable = available) }
            }
        }
        viewModelScope.launch {
            graph.runtime.serviceActive.collect { active -> _ui.update { it.copy(serviceActive = active) } }
        }
        viewModelScope.launch {
            graph.runtime.locationUpdatesActive.collect { active ->
                _ui.update { it.copy(locationUpdatesActive = active) }
            }
        }
        viewModelScope.launch {
            graph.runtime.heartbeatActive.collect { active -> _ui.update { it.copy(heartbeatActive = active) } }
        }
        viewModelScope.launch {
            graph.runtime.appInForeground.collect { foreground ->
                _ui.update { it.copy(appInForeground = foreground) }
            }
        }
        viewModelScope.launch {
            graph.runtime.lastHttpError.collect { error -> _ui.update { it.copy(lastHttpError = error) } }
        }
        viewModelScope.launch {
            graph.runtime.lastUploadAtEpochMs.collect { at -> _ui.update { it.copy(lastUploadAtEpochMs = at) } }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshStatus()
                _ui.update { it.copy(nowEpochMs = System.currentTimeMillis()) }
                delay(1_000)
            }
        }
        viewModelScope.launch {
            graph.repository.flush(NetworkStatus.isOnline(getApplication()))
        }
    }

    fun showMessage(message: String) {
        _ui.update { it.copy(message = message) }
    }

    fun toggleDebug() {
        _ui.update { it.copy(showDebug = !it.showDebug) }
    }

    fun setAppInForeground(foreground: Boolean) {
        graph.runtime.appInForeground.value = foreground
    }

    fun refreshStatus() {
        val context = getApplication<Application>()
        _ui.update {
            it.copy(
                gpsAvailable = LocationStatus.isEnabled(context),
                internetAvailable = NetworkStatus.isOnline(context),
                hasFineLocation = PermissionStatus.hasFineLocation(context),
                hasNotificationPermission = PermissionStatus.hasNotification(context),
                playServicesAvailable = PlayServicesStatus.isAvailable(context) && graph.runtime.playServicesAvailable.value,
                batteryRestricted = BatteryStatus.optimizationRestricts(context),
            )
        }
    }

    fun endInterrupted() {
        viewModelScope.launch {
            graph.repository.dismissInterrupted()
            graph.repository.flush(NetworkStatus.isOnline(getApplication()))
        }
    }

    fun debugText(): String {
        val state = _ui.value
        val ageSeconds = state.lastFix?.receivedAtEpochMs?.let { received ->
            ((state.nowEpochMs - received) / 1000L).coerceAtLeast(0L)
        }
        return buildDebugReport(
            DebugSnapshot(
                trackingState = when {
                    state.interrupted -> "INTERRUPTED"
                    state.session != null -> state.session.status.name
                    else -> "IDLE"
                },
                serviceActive = state.serviceActive,
                trackingSessionId = state.session?.trackingSessionId,
                routeId = state.session?.routeId,
                deviceId = state.session?.deviceId,
                lastLocationAgeSeconds = ageSeconds,
                accuracyMeters = state.lastFix?.accuracyMeters,
                networkOnline = state.internetAvailable,
                pendingLocations = state.pendingCount,
                batteryOptimization = state.batteryRestricted,
                lastHttpError = state.lastHttpError,
            ),
        )
    }

    private fun publishFix(fix: LastFixSnapshot?) {
        if (fix == null) return
        _ui.update { state ->
            val current = state.lastFix
            if (current == null || fix.receivedAtEpochMs >= current.receivedAtEpochMs) {
                state.copy(lastFix = fix)
            } else {
                state
            }
        }
    }

    companion object {
        fun factory(application: TrackerApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(application) as T
                }
            }
    }
}
