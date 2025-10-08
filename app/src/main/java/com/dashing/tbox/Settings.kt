package com.dashing.tbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val AUTO_MODEM_RESTART_KEY = booleanPreferencesKey("auto_modem_restart")
        private val AUTO_TBOX_REBOOT_KEY = booleanPreferencesKey("auto_tbox_reboot")
        private val LOG_LEVEL = stringPreferencesKey("log_level")
        private val AUTO_STOP_TBOX_APP_KEY = booleanPreferencesKey("auto_stop_tbox_app")
        private val AUTO_PREVENT_TBOX_RESTART_KEY = booleanPreferencesKey("auto_prevent_tbox_restart")
    }

    val autoModemRestartFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_MODEM_RESTART_KEY] ?: false
        }

    val autoTboxRebootFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_TBOX_REBOOT_KEY] ?: false
        }

    val autoStopTboxApp: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_STOP_TBOX_APP_KEY] ?: false
        }

    val autoPreventTboxRestart: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_PREVENT_TBOX_RESTART_KEY] ?: false
        }

    val logLevel: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[LOG_LEVEL] ?: "DEBUG"
        }

    suspend fun getAutoModemRestartSetting(): Boolean {
        return context.dataStore.data
            .map { preferences ->
                preferences[AUTO_MODEM_RESTART_KEY] ?: false
            }
            .first()
    }

    suspend fun saveAutoModemRestartSetting(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_MODEM_RESTART_KEY] = enabled
        }
    }

    suspend fun getAutoTboxRebootSetting(): Boolean {
        return context.dataStore.data
            .map { preferences ->
                preferences[AUTO_TBOX_REBOOT_KEY] ?: false
            }
            .first()
    }

    suspend fun saveAutoTboxRebootSetting(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_TBOX_REBOOT_KEY] = enabled
        }
    }

    suspend fun getLogLevel(): String {
        return context.dataStore.data
            .map { preferences ->
                preferences[LOG_LEVEL] ?: "DEBUG"
            }
            .first()
    }

    suspend fun saveLogLevel(level: String) {
        context.dataStore.edit { preferences ->
            preferences[LOG_LEVEL] = level
        }
    }

    suspend fun getAutoStopTboxAppSetting(): Boolean {
        return context.dataStore.data
            .map { preferences ->
                preferences[AUTO_STOP_TBOX_APP_KEY] ?: false
            }
            .first()
    }

    suspend fun saveAutoStopTboxAppSetting(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_STOP_TBOX_APP_KEY] = enabled
        }
    }

    suspend fun getAutoPreventTboxRestartSetting(): Boolean {
        return context.dataStore.data
            .map { preferences ->
                preferences[AUTO_PREVENT_TBOX_RESTART_KEY] ?: false
            }
            .first()
    }

    suspend fun saveAutoPreventTboxRestartSetting(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_PREVENT_TBOX_RESTART_KEY] = enabled
        }
    }
}