package br.com.jcdecor.tracker.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeviceIdStore(
    private val dataStore: DataStore<Preferences>,
) {
    private val mutex = Mutex()

    suspend fun getOrCreate(): String = mutex.withLock {
        val existing = dataStore.data.first()[KEY]
        if (!existing.isNullOrBlank()) {
            return@withLock existing
        }
        val created = UUID.randomUUID().toString()
        dataStore.edit { prefs -> prefs[KEY] = created }
        created
    }

    private companion object {
        val KEY = stringPreferencesKey("device_id")
    }
}
