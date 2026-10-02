package br.com.jcdecor.tracker.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import br.com.jcdecor.tracker.tracking.LastFixSnapshot
import br.com.jcdecor.tracker.tracking.SessionStatus
import br.com.jcdecor.tracker.tracking.TrackingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SessionStore(
    private val dataStore: DataStore<Preferences>,
) {
    val session: Flow<TrackingSession?> = dataStore.data.map { it.toSession() }
    val lastFix: Flow<LastFixSnapshot?> = dataStore.data.map { it.toLastFix() }

    suspend fun current(): TrackingSession? = session.first()

    suspend fun currentLastFix(): LastFixSnapshot? = lastFix.first()

    suspend fun save(session: TrackingSession?, lastFix: LastFixSnapshot? = null) {
        dataStore.edit { prefs ->
            if (session == null) {
                prefs.clearSession()
            } else {
                prefs[KEY_SESSION] = session.trackingSessionId
                prefs[KEY_DEVICE] = session.deviceId
                prefs[KEY_STARTED] = session.startedAtEpochMs
                putOptionalString(prefs, KEY_ROUTE, session.routeId)
                putOptionalString(prefs, KEY_DRIVER, session.driverId)
                prefs[KEY_STATUS] = session.status.name
                prefs[KEY_SEQUENCE] = session.sequence
                prefs[KEY_INTERRUPTED] = session.interrupted
                putOptionalLong(prefs, KEY_LAST_LOCATION, session.lastLocationAtEpochMs)
                putOptionalLong(prefs, KEY_STOPPED, session.stoppedAtEpochMs)
                putOptionalLong(prefs, KEY_ACCEPTED_AT, session.lastAcceptedAtEpochMs)
                putOptionalString(prefs, KEY_ACCEPTED_LAT, session.lastAcceptedLatitude?.toString())
                putOptionalString(prefs, KEY_ACCEPTED_LNG, session.lastAcceptedLongitude?.toString())
            }
            if (lastFix != null) {
                prefs[KEY_FIX_LAT] = lastFix.latitude.toString()
                prefs[KEY_FIX_LNG] = lastFix.longitude.toString()
                prefs[KEY_FIX_ACC] = lastFix.accuracyMeters
                prefs[KEY_FIX_RECORDED] = lastFix.recordedAtEpochMs
                prefs[KEY_FIX_RECEIVED] = lastFix.receivedAtEpochMs
                prefs[KEY_FIX_OK] = lastFix.acceptable
                putOptionalString(prefs, KEY_FIX_SPEED, lastFix.speedMetersPerSecond?.toString())
                putOptionalString(prefs, KEY_FIX_BEARING, lastFix.bearingDegrees?.toString())
                putOptionalString(prefs, KEY_FIX_ALTITUDE, lastFix.altitudeMeters?.toString())
            }
        }
    }

    private fun putOptionalLong(prefs: MutablePreferences, key: Preferences.Key<Long>, value: Long?) {
        if (value == null) prefs.remove(key) else prefs[key] = value
    }

    private fun putOptionalString(prefs: MutablePreferences, key: Preferences.Key<String>, value: String?) {
        if (value == null) prefs.remove(key) else prefs[key] = value
    }

    private fun MutablePreferences.clearSession() {
        remove(KEY_SESSION)
        remove(KEY_ROUTE)
        remove(KEY_DRIVER)
        remove(KEY_DEVICE)
        remove(KEY_STARTED)
        remove(KEY_STATUS)
        remove(KEY_SEQUENCE)
        remove(KEY_INTERRUPTED)
        remove(KEY_LAST_LOCATION)
        remove(KEY_STOPPED)
        remove(KEY_ACCEPTED_AT)
        remove(KEY_ACCEPTED_LAT)
        remove(KEY_ACCEPTED_LNG)
    }

    private fun Preferences.toSession(): TrackingSession? {
        val sessionId = this[KEY_SESSION] ?: return null
        val device = this[KEY_DEVICE] ?: return null
        val started = this[KEY_STARTED] ?: return null
        val statusName = this[KEY_STATUS] ?: return null
        val status = runCatching { SessionStatus.valueOf(statusName) }.getOrNull() ?: return null
        return TrackingSession(
            trackingSessionId = sessionId,
            deviceId = device,
            startedAtEpochMs = started,
            routeId = this[KEY_ROUTE],
            driverId = this[KEY_DRIVER],
            lastLocationAtEpochMs = this[KEY_LAST_LOCATION],
            sequence = this[KEY_SEQUENCE] ?: 0L,
            status = status,
            interrupted = this[KEY_INTERRUPTED] ?: false,
            stoppedAtEpochMs = this[KEY_STOPPED],
            lastAcceptedLatitude = this[KEY_ACCEPTED_LAT]?.toDoubleOrNull(),
            lastAcceptedLongitude = this[KEY_ACCEPTED_LNG]?.toDoubleOrNull(),
            lastAcceptedAtEpochMs = this[KEY_ACCEPTED_AT],
        )
    }

    private fun Preferences.toLastFix(): LastFixSnapshot? {
        val lat = this[KEY_FIX_LAT]?.toDoubleOrNull() ?: return null
        val lng = this[KEY_FIX_LNG]?.toDoubleOrNull() ?: return null
        val accuracy = this[KEY_FIX_ACC] ?: return null
        val recorded = this[KEY_FIX_RECORDED] ?: return null
        val received = this[KEY_FIX_RECEIVED] ?: recorded
        return LastFixSnapshot(
            latitude = lat,
            longitude = lng,
            accuracyMeters = accuracy,
            speedMetersPerSecond = this[KEY_FIX_SPEED]?.toFloatOrNull(),
            bearingDegrees = this[KEY_FIX_BEARING]?.toFloatOrNull(),
            altitudeMeters = this[KEY_FIX_ALTITUDE]?.toDoubleOrNull(),
            recordedAtEpochMs = recorded,
            receivedAtEpochMs = received,
            acceptable = this[KEY_FIX_OK] ?: false,
        )
    }

    private companion object {
        val KEY_SESSION = stringPreferencesKey("tracking_session_id")
        val KEY_ROUTE = stringPreferencesKey("route_id")
        val KEY_DRIVER = stringPreferencesKey("driver_id")
        val KEY_DEVICE = stringPreferencesKey("device_id")
        val KEY_STARTED = longPreferencesKey("started_at")
        val KEY_STATUS = stringPreferencesKey("status")
        val KEY_SEQUENCE = longPreferencesKey("sequence")
        val KEY_INTERRUPTED = booleanPreferencesKey("interrupted")
        val KEY_LAST_LOCATION = longPreferencesKey("last_location_at")
        val KEY_STOPPED = longPreferencesKey("stopped_at")
        val KEY_ACCEPTED_LAT = stringPreferencesKey("accepted_lat")
        val KEY_ACCEPTED_LNG = stringPreferencesKey("accepted_lng")
        val KEY_ACCEPTED_AT = longPreferencesKey("accepted_at")
        val KEY_FIX_LAT = stringPreferencesKey("fix_lat")
        val KEY_FIX_LNG = stringPreferencesKey("fix_lng")
        val KEY_FIX_ACC = floatPreferencesKey("fix_accuracy")
        val KEY_FIX_SPEED = stringPreferencesKey("fix_speed")
        val KEY_FIX_BEARING = stringPreferencesKey("fix_bearing")
        val KEY_FIX_ALTITUDE = stringPreferencesKey("fix_altitude")
        val KEY_FIX_RECORDED = longPreferencesKey("fix_recorded")
        val KEY_FIX_RECEIVED = longPreferencesKey("fix_received")
        val KEY_FIX_OK = booleanPreferencesKey("fix_ok")
    }
}
