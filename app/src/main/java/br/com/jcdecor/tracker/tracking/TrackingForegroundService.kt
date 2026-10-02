package br.com.jcdecor.tracker.tracking

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import br.com.jcdecor.tracker.TrackerApplication
import br.com.jcdecor.tracker.location.LocationStartResult
import br.com.jcdecor.tracker.util.Iso8601
import br.com.jcdecor.tracker.util.LocationStatus
import br.com.jcdecor.tracker.util.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class TrackingForegroundService : Service() {
    private val graph by lazy { (application as TrackerApplication).graph }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notifier by lazy { TrackingNotifier(this) }
    private val jobs = mutableListOf<Job>()
    private var updatesStarted = false
    private var locationReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { stopTracking() }
                return START_NOT_STICKY
            }
            else -> {
                if (!enterForeground()) {
                    graph.runtime.serviceActive.value = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                graph.runtime.serviceActive.value = true
                scope.launch {
                    when (intent?.action) {
                        ACTION_RESUME -> begin(resume = true)
                        ACTION_START -> begin(resume = false)
                        else -> restoreAfterRestart()
                    }
                }
                return START_STICKY
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Fechar a activity ou arrastar o app dos recentes não encerra o tracking.
    }

    override fun onDestroy() {
        graph.runtime.serviceActive.value = false
        graph.runtime.locationUpdatesActive.value = false
        graph.runtime.heartbeatActive.value = false
        graph.locationProvider.stop()
        stopLoops()
        unregisterWatchers()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun begin(resume: Boolean) {
        val outcome = if (resume) graph.repository.resume() else graph.repository.start()
        when (outcome) {
            is StartOutcome.Started, is StartOutcome.Resumed, is StartOutcome.AlreadyActive -> {
                val session = graph.repository.currentSession()
                if (session == null || session.interrupted) {
                    shutdownQuietly()
                    return
                }
                graph.repository.markTracking()
                startUpdates()
                startLoops()
                registerWatchers()
                refreshHealth()
                updateNotification()
            }
            is StartOutcome.InterruptedPending, is StartOutcome.InvalidRoute -> {
                if (!graph.repository.isActivelyTracking()) {
                    shutdownQuietly()
                }
            }
        }
    }

    private suspend fun restoreAfterRestart() {
        val session = graph.repository.currentSession()
        if (session != null && session.status.isActive() && !session.interrupted) {
            graph.repository.markTracking()
            startUpdates()
            startLoops()
            registerWatchers()
            refreshHealth()
            updateNotification()
        } else {
            shutdownQuietly()
        }
    }

    private fun startUpdates() {
        if (updatesStarted) return
        when (graph.locationProvider.start { fix ->
            scope.launch {
                try {
                    graph.repository.onLocation(
                        fix = fix,
                        networkAvailable = NetworkStatus.isOnline(this@TrackingForegroundService),
                        receivedAtEpochMs = System.currentTimeMillis(),
                    )
                    refreshHealth()
                    updateNotification()
                } catch (exception: Exception) {
                    graph.log.info("LOCATION_PIPELINE_FAILED ${exception.javaClass.simpleName}")
                }
            }
        }) {
            LocationStartResult.STARTED -> {
                updatesStarted = true
                graph.runtime.locationUpdatesActive.value = true
                graph.runtime.playServicesAvailable.value = true
            }
            LocationStartResult.PLAY_SERVICES_UNAVAILABLE -> {
                graph.runtime.playServicesAvailable.value = false
                graph.log.info("PLAY_SERVICES_UNAVAILABLE")
            }
            LocationStartResult.MISSING_PERMISSION -> {
                graph.runtime.locationUpdatesActive.value = false
                graph.log.info("LOCATION_PERMISSION_MISSING")
            }
        }
    }

    private fun startLoops() {
        if (jobs.isNotEmpty()) return
        graph.runtime.heartbeatActive.value = true
        jobs += scope.launch {
            while (isActive) {
                delay(TrackingConfig.HEARTBEAT_INTERVAL_MS)
                val session = graph.repository.currentSession() ?: break
                if (!session.status.isActive() || session.interrupted) break
                graph.heartbeat.send(
                    trackingSessionId = session.trackingSessionId,
                    routeId = session.routeId,
                    deviceId = session.deviceId,
                    recordedAt = Iso8601.formatUtc(System.currentTimeMillis()),
                )
            }
        }
        jobs += scope.launch {
            while (isActive) {
                delay(TrackingConfig.FLUSH_INTERVAL_MS)
                if (!graph.repository.isActivelyTracking()) break
                graph.repository.flush(NetworkStatus.isOnline(this@TrackingForegroundService))
                updateNotification()
            }
        }
        jobs += scope.launch {
            while (isActive) {
                delay(TrackingConfig.STATIONARY_REFRESH_MS)
                if (!graph.repository.isActivelyTracking()) break
                graph.locationProvider.requestSingle { fix ->
                    scope.launch {
                        graph.repository.onLocation(
                            fix = fix,
                            networkAvailable = NetworkStatus.isOnline(this@TrackingForegroundService),
                            receivedAtEpochMs = System.currentTimeMillis(),
                        )
                        updateNotification()
                    }
                }
                refreshHealth()
                updateNotification()
            }
        }
    }

    private suspend fun stopTracking() {
        updatesStarted = false
        graph.runtime.locationUpdatesActive.value = false
        graph.runtime.heartbeatActive.value = false
        graph.locationProvider.stop()
        stopLoops()
        unregisterWatchers()
        graph.repository.beginStop()
        withTimeoutOrNull(TrackingConfig.STOP_FLUSH_TIMEOUT_MS) {
            graph.repository.flush(NetworkStatus.isOnline(this@TrackingForegroundService))
        }
        graph.repository.finishStop()
        removeForeground()
        stopSelf()
    }

    private suspend fun refreshHealth() {
        val gps = LocationStatus.isEnabled(this)
        val online = NetworkStatus.isOnline(this)
        val lowAccuracy = graph.runtime.lastFix.value?.acceptable == false
        graph.repository.updateDegraded(gps, online, lowAccuracy)
    }

    private suspend fun updateNotification() {
        val session = graph.repository.currentSession() ?: return
        if (!session.status.isActive() || session.interrupted) return
        val notification = notifier.build(
            lastFixAtEpochMs = graph.runtime.lastFix.value?.receivedAtEpochMs,
            nowEpochMs = System.currentTimeMillis(),
        )
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(TrackingConfig.NOTIFICATION_ID, notification)
    }

    private fun enterForeground(): Boolean {
        return try {
            notifier.ensureChannel()
            val notification = notifier.build(null, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TrackingConfig.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(TrackingConfig.NOTIFICATION_ID, notification)
            }
            true
        } catch (exception: Exception) {
            graph.log.info("FGS_START_FAILED ${exception.javaClass.simpleName}")
            false
        }
    }

    private fun removeForeground() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (exception: Exception) {
            graph.log.info("FGS_STOP_FAILED ${exception.javaClass.simpleName}")
        }
    }

    private suspend fun shutdownQuietly() {
        updatesStarted = false
        graph.runtime.serviceActive.value = false
        graph.runtime.locationUpdatesActive.value = false
        graph.runtime.heartbeatActive.value = false
        graph.locationProvider.stop()
        stopLoops()
        unregisterWatchers()
        removeForeground()
        stopSelf()
    }

    private fun stopLoops() {
        graph.runtime.heartbeatActive.value = false
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private fun registerWatchers() {
        if (locationReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    scope.launch {
                        refreshHealth()
                        updateNotification()
                    }
                }
            }
            locationReceiver = receiver
            val filter = IntentFilter(LocationManager.MODE_CHANGED_ACTION)
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        if (networkCallback == null) {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        refreshHealth()
                        graph.repository.flush(NetworkStatus.isOnline(this@TrackingForegroundService))
                        updateNotification()
                    }
                }

                override fun onLost(network: Network) {
                    scope.launch {
                        refreshHealth()
                        updateNotification()
                    }
                }
            }
            networkCallback = callback
            val manager = getSystemService(ConnectivityManager::class.java)
            runCatching { manager.registerDefaultNetworkCallback(callback) }
                .onFailure { graph.log.info("NETWORK_CALLBACK_FAILED ${it.javaClass.simpleName}") }
        }
    }

    private fun unregisterWatchers() {
        locationReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        locationReceiver = null
        networkCallback?.let { callback ->
            val manager = getSystemService(ConnectivityManager::class.java)
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
    }

    companion object {
        const val ACTION_START = "br.com.jcdecor.tracker.action.START"
        const val ACTION_RESUME = "br.com.jcdecor.tracker.action.RESUME"
        const val ACTION_STOP = "br.com.jcdecor.tracker.action.STOP"
    }
}
