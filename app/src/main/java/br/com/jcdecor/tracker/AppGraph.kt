package br.com.jcdecor.tracker

import android.content.Context
import androidx.room.Room
import br.com.jcdecor.tracker.data.local.DeviceIdStore
import br.com.jcdecor.tracker.data.local.RoomPendingLocationStore
import br.com.jcdecor.tracker.data.local.SessionStore
import br.com.jcdecor.tracker.data.local.TrackerDatabase
import br.com.jcdecor.tracker.data.local.deviceDataStore
import br.com.jcdecor.tracker.data.local.sessionDataStore
import br.com.jcdecor.tracker.data.remote.TrackingClients
import br.com.jcdecor.tracker.data.repository.TrackingRepository
import br.com.jcdecor.tracker.location.AndroidLocationProvider
import br.com.jcdecor.tracker.tracking.HeartbeatClient
import br.com.jcdecor.tracker.tracking.TrackingEngine
import br.com.jcdecor.tracker.tracking.TrackingRuntime
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
    private val mode = BuildConfig.TRACKING_MODE
    private val baseUrl = BuildConfig.API_BASE_URL
    private val token = BuildConfig.API_TOKEN
    val heartbeat: HeartbeatClient = TrackingClients.heartbeat(mode, baseUrl, token, log)
    val locationProvider = AndroidLocationProvider(appContext)
    val repository = TrackingRepository(
        engine = TrackingEngine(
            store = pendingStore,
            uploader = TrackingClients.locationUploader(mode, baseUrl, token, log),
            log = log,
        ),
        sessionStore = sessionStore,
        deviceIdStore = deviceIdStore,
        pendingStore = pendingStore,
        runtime = runtime,
    )
}
