package com.dashing.tbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "com.dashing.tbox.settings"

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

class SettingsManager(private val context: Context) {

    companion object {
        private const val KEY_PREFIX = "com.dashing.tbox."

        private val AUTO_MODEM_RESTART_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_modem_restart")
        private val AUTO_TBOX_REBOOT_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_tbox_reboot")
        private val LOG_LEVEL_KEY = stringPreferencesKey("${KEY_PREFIX}log_level")
        private val AUTO_PREVENT_TBOX_RESTART_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_prevent_tbox_restart")
        private val GET_VOLTAGES_KEY = booleanPreferencesKey("${KEY_PREFIX}get_voltages")
        private val GET_CAN_FRAME_KEY = booleanPreferencesKey("${KEY_PREFIX}get_can_frame")
        private val GET_CYCLE_SIGNAL_KEY = booleanPreferencesKey("${KEY_PREFIX}get_cycle_signal")
        private val GET_LOC_DATA_KEY = booleanPreferencesKey("${KEY_PREFIX}get_loc_data")
        private val WIDGET_SHOW_INDICATOR = booleanPreferencesKey("${KEY_PREFIX}widget_show_indicator")
        private val TBOX_IP_KEY = stringPreferencesKey("${KEY_PREFIX}tbox_ip")

        private const val DEFAULT_LOG_LEVEL = "DEBUG"
        private const val DEFAULT_TBOX_IP = "192.168.225.1"
    }

    val autoModemRestartFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[AUTO_MODEM_RESTART_KEY] ?: false
        }

    val widgetShowIndicatorFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[WIDGET_SHOW_INDICATOR] ?: false
        }

    val autoTboxRebootFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[AUTO_TBOX_REBOOT_KEY] ?: false
        }

    val autoPreventTboxRestartFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[AUTO_PREVENT_TBOX_RESTART_KEY] ?: false
        }

    val getVoltagesFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[GET_VOLTAGES_KEY] ?: false
        }

    val getCanFrameFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[GET_CAN_FRAME_KEY] ?: false
        }

    val getCycleSignalFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[GET_CYCLE_SIGNAL_KEY] ?: false
        }

    val getLocDataFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[GET_LOC_DATA_KEY] ?: false
        }

    val logLevelFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LOG_LEVEL_KEY] ?: DEFAULT_LOG_LEVEL
        }

    val tboxIPFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[TBOX_IP_KEY] ?: DEFAULT_TBOX_IP
        }

    suspend fun saveAutoModemRestartSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_MODEM_RESTART_KEY] = enabled
        }
    }

    suspend fun saveAutoTboxRebootSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_TBOX_REBOOT_KEY] = enabled
        }
    }

    suspend fun saveWidgetShowIndicatorSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[WIDGET_SHOW_INDICATOR] = enabled
        }
    }

    suspend fun saveLogLevel(level: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[LOG_LEVEL_KEY] = level
        }
    }

    suspend fun saveAutoPreventTboxRestartSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_PREVENT_TBOX_RESTART_KEY] = enabled
        }
    }

    suspend fun saveCustomString(key: String, value: String) {
        val preferencesKey = stringPreferencesKey("${KEY_PREFIX}$key")
        context.settingsDataStore.edit { preferences ->
            preferences[preferencesKey] = value
        }
    }

    fun getStringFlow(key: String, defaultValue: String = ""): Flow<String> {
        val preferencesKey = stringPreferencesKey("${KEY_PREFIX}$key")
        return context.settingsDataStore.data
            .map { preferences ->
                preferences[preferencesKey] ?: defaultValue
            }
    }

    suspend fun saveGetVoltagesSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[GET_VOLTAGES_KEY] = enabled
        }
    }

    suspend fun saveGetCanFrameSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[GET_CAN_FRAME_KEY] = enabled
        }
    }

    suspend fun saveGetCycleSignalSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[GET_CYCLE_SIGNAL_KEY] = enabled
        }
    }

    suspend fun saveGetLocDataSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[GET_LOC_DATA_KEY] = enabled
        }
    }

    suspend fun saveTboxIP(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[TBOX_IP_KEY] = value
        }
    }
}