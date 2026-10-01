package br.com.jcdecor.tracker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "jc_tracker_session")
val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "jc_tracker_device")
