package br.com.jcdecor.tracker

import android.content.Context
import androidx.room.Room
import android.os.Build
import br.com.jcdecor.tracker.data.local.DeviceIdStore
import br.com.jcdecor.tracker.data.local.EncryptedDeviceCredentialStore
import br.com.jcdecor.tracker.data.local.RoomPendingLocationStore
import br.com.jcdecor.tracker.data.local.SessionStore
import br.com.jcdecor.tracker.data.local.TrackerDatabase
import br.com.jcdecor.tracker.data.local.deviceDataStore
import br.com.jcdecor.tracker.data.local.sessionDataStore
import br.com.jcdecor.tracker.data.remote.DeviceProfile
import br.com.jcdecor.tracker.data.remote.LogisticsTracker
import br.com.jcdecor.tracker.data.remote.TrackingClients
import br.com.jcdecor.tracker.data.repository.TrackingRepository
import br.com.jcdecor.tracker.location.AndroidLocationProvider
import br.com.jcdecor.tracker.tracking.HeartbeatClient
import br.com.jcdecor.tracker.tracking.LocationUploader
import br.com.jcdecor.tracker.tracking.TrackingEngine
import br.com.jcdecor.tracker.tracking.TrackingRuntime
import br.com.jcdecor.tracker.tracking.TrackingSession
import br.com.jcdecor.tracker.util.AndroidTrackerLog

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    val log = AndroidTrackerLog()
    val runtime = TrackingRuntime()
    private val database = Room.databaseBuilder(appContext, TrackerDatabase::class.java, "jc-tracker.db")
        .fallbackToDestructiveMigration()
        .build()
    val pendingStore = RoomPendingLocationStore(database.pendingLocationDao())
    val sessionStore = SessionStore(appContext.sessionDataStore)
    val deviceIdStore = DeviceIdStore(appContext.deviceDataStore)
    val trackingMode = BuildConfig.TRACKING_MODE
    val apiBaseUrl = BuildConfig.API_BASE_URL
    private val realMode = trackingMode.equals("REAL", ignoreCase = true)
    private val sessionMirror = SessionMirror()
    private val credentials = EncryptedDeviceCredentialStore(appContext)
    private val tracker: LogisticsTracker? = if (realMode) {
        TrackingClients.realTracker(
            baseUrl = apiBaseUrl,
            allowCleartext = BuildConfig.DEBUG,
            bootstrapToken = BuildConfig.BOOTSTRAP_TOKEN,
            credentials = credentials,
            profile = DeviceProfile(
                appVersion = BuildConfig.VERSION_NAME,
                androidVersion = Build.VERSION.RELEASE ?: "",
                deviceModel = Build.MODEL ?: "",
            ),
            log = log,
            diagnostics = runtime,
            status = runtime,
            sessionLookup = { sessionMirror.current },
        )
    } else {
        null
    }
    val heartbeat: HeartbeatClient = tracker ?: br.com.jcdecor.tracker.data.remote.MockHeartbeatClient(log)
    private val uploader: LocationUploader = tracker
        ?: br.com.jcdecor.tracker.data.remote.MockLocationUploader(log, runtime)
    val locationProvider = AndroidLocationProvider(appContext)
    val repository = TrackingRepository(
        engine = TrackingEngine(
            store = pendingStore,
            uploader = uploader,
            log = log,
        ),
        sessionStore = sessionStore,
        deviceIdStore = deviceIdStore,
        pendingStore = pendingStore,
        runtime = runtime,
        outbound = tracker,
        onSession = { sessionMirror.current = it },
    )
}

private class SessionMirror {
    @Volatile var current: TrackingSession? = null
}
