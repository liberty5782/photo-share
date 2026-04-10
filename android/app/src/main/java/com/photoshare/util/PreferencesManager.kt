package com.photoshare.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "photoshare_prefs")

data class AppPrefs(
    val deviceId: String,
    val deviceName: String,
    val serverUrl: String,
)

class PreferencesManager(private val context: Context) {

    companion object {
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val DEVICE_NAME = stringPreferencesKey("device_name")
        private val SERVER_URL = stringPreferencesKey("server_url")
    }

    val deviceId: Flow<String?> = context.dataStore.data.map { it[DEVICE_ID] }
    val deviceName: Flow<String?> = context.dataStore.data.map { it[DEVICE_NAME] }
    val serverUrl: Flow<String?> = context.dataStore.data.map { it[SERVER_URL] }

    /** Emits non-null only when all three prefs are present. */
    val appPrefs: Flow<AppPrefs?> = combine(deviceId, deviceName, serverUrl) { id, name, url ->
        if (id != null && name != null && url != null) AppPrefs(id, name, url) else null
    }

    suspend fun save(deviceId: String, deviceName: String, serverUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[DEVICE_ID] = deviceId
            prefs[DEVICE_NAME] = deviceName
            prefs[SERVER_URL] = serverUrl.trimEnd('/')
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
