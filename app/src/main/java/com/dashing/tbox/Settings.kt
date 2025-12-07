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
import kotlinx.coroutines.flow.distinctUntilChanged

private const val DATASTORE_NAME = "com.dashing.tbox.settings"

// Используем extension property для DataStore
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

class SettingsManager(private val context: Context) {

    companion object {
        private const val KEY_PREFIX = "com.dashing.tbox."

        // Boolean настройки
        private val AUTO_MODEM_RESTART_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_modem_restart")
        private val AUTO_TBOX_REBOOT_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_tbox_reboot")
        private val AUTO_SUSPEND_TBOX_APP_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_suspend_tbox_app")
        private val AUTO_STOP_TBOX_APP_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_stop_tbox_app")
        private val AUTO_PREVENT_TBOX_RESTART_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_prevent_tbox_restart")
        private val GET_VOLTAGES_KEY = booleanPreferencesKey("${KEY_PREFIX}get_voltages")
        private val GET_CAN_FRAME_KEY = booleanPreferencesKey("${KEY_PREFIX}get_can_frame")
        private val GET_CYCLE_SIGNAL_KEY = booleanPreferencesKey("${KEY_PREFIX}get_cycle_signal")
        private val GET_LOC_DATA_KEY = booleanPreferencesKey("${KEY_PREFIX}get_loc_data")
        private val WIDGET_SHOW_INDICATOR = booleanPreferencesKey("${KEY_PREFIX}widget_show_indicator")
        private val WIDGET_SHOW_LOC_INDICATOR = booleanPreferencesKey("${KEY_PREFIX}widget_show_loc_indicator")
        private val MOCK_LOCATION = booleanPreferencesKey("${KEY_PREFIX}mock_location")
        private val EXPERT_MODE = booleanPreferencesKey("${KEY_PREFIX}expert_mode")

        private val SELECTED_TAB_KEY = stringPreferencesKey("${KEY_PREFIX}selected_tab")

        // String настройки
        private val LOG_LEVEL_KEY = stringPreferencesKey("${KEY_PREFIX}log_level")
        private val TBOX_IP_KEY = stringPreferencesKey("${KEY_PREFIX}tbox_ip")

        // Значения по умолчанию
        private const val DEFAULT_LOG_LEVEL = "DEBUG"
        private const val DEFAULT_TBOX_IP = "192.168.225.1"

        // Кэш ключей для производительности
        private val stringKeysCache = mutableMapOf<String, Preferences.Key<String>>()

        // Ключ для сохранения конфигурации виджетов
        private val DASHBOARD_WIDGETS_KEY = stringPreferencesKey("${KEY_PREFIX}dashboard_widgets")
    }

    // Flow для конфигурации виджетов
    val dashboardWidgetsFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[DASHBOARD_WIDGETS_KEY] ?: "" }
        .distinctUntilChanged()

    val autoModemRestartFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_MODEM_RESTART_KEY] ?: false }
        .distinctUntilChanged()

    val widgetShowIndicatorFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[WIDGET_SHOW_INDICATOR] ?: false }
        .distinctUntilChanged()

    val widgetShowLocIndicatorFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[WIDGET_SHOW_LOC_INDICATOR] ?: false }
        .distinctUntilChanged()

    val mockLocationFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MOCK_LOCATION] ?: false }
        .distinctUntilChanged()

    val autoTboxRebootFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_TBOX_REBOOT_KEY] ?: false }
        .distinctUntilChanged()

    val autoSuspendTboxAppFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_SUSPEND_TBOX_APP_KEY] ?: false }
        .distinctUntilChanged()

    val autoStopTboxAppFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_STOP_TBOX_APP_KEY] ?: false }
        .distinctUntilChanged()

    val autoPreventTboxRestartFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_PREVENT_TBOX_RESTART_KEY] ?: false }
        .distinctUntilChanged()

    val getVoltagesFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_VOLTAGES_KEY] ?: false }
        .distinctUntilChanged()

    val getCanFrameFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_CAN_FRAME_KEY] ?: false }
        .distinctUntilChanged()

    val getCycleSignalFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_CYCLE_SIGNAL_KEY] ?: false }
        .distinctUntilChanged()

    val getLocDataFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_LOC_DATA_KEY] ?: false }
        .distinctUntilChanged()

    val expertModeFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[EXPERT_MODE] ?: false }
        .distinctUntilChanged()

    // String flows
    val logLevelFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[LOG_LEVEL_KEY] ?: DEFAULT_LOG_LEVEL }
        .distinctUntilChanged()

    val tboxIPFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[TBOX_IP_KEY] ?: DEFAULT_TBOX_IP }
        .distinctUntilChanged()

    val selectedTabFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SELECTED_TAB_KEY]?.toIntOrNull() ?: 0
        }

    // Suspend функции для сохранения настроек

    // Сохранение конфигурации виджетов
    suspend fun saveDashboardWidgets(config: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_WIDGETS_KEY] = config
        }
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

    suspend fun saveWidgetShowLocIndicatorSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[WIDGET_SHOW_LOC_INDICATOR] = enabled
        }
    }

    suspend fun saveMockLocationSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MOCK_LOCATION] = enabled
        }
    }

    suspend fun saveLogLevel(level: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[LOG_LEVEL_KEY] = level
        }
    }

    suspend fun saveAutoSuspendTboxAppSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_SUSPEND_TBOX_APP_KEY] = enabled
        }
    }

    suspend fun saveAutoStopTboxAppSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_STOP_TBOX_APP_KEY] = enabled
        }
    }

    suspend fun saveAutoPreventTboxRestartSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_PREVENT_TBOX_RESTART_KEY] = enabled
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

    suspend fun saveExpertModeSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[EXPERT_MODE] = enabled
        }
    }

    suspend fun saveTboxIP(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[TBOX_IP_KEY] = value
        }
    }

    // Улучшенные методы для кастомных строк
    suspend fun saveCustomString(key: String, value: String) {
        val preferencesKey = getStringKey(key)
        context.settingsDataStore.edit { preferences ->
            preferences[preferencesKey] = value
        }
    }

    fun getStringFlow(key: String, defaultValue: String = ""): Flow<String> {
        val preferencesKey = getStringKey(key)
        return context.settingsDataStore.data
            .map { preferences -> preferences[preferencesKey] ?: defaultValue }
            .distinctUntilChanged()
    }

    private fun getStringKey(key: String): Preferences.Key<String> {
        return stringKeysCache.getOrPut(key) {
            stringPreferencesKey("${KEY_PREFIX}$key")
        }
    }

    suspend fun saveSelectedTab(tabIndex: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SELECTED_TAB_KEY] = tabIndex.toString()
        }
    }

    suspend fun clearAllSettings() {
        context.settingsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun resetToDefaults() {
        context.settingsDataStore.edit { preferences ->
            // Boolean настройки сбрасываются в false
            preferences[AUTO_MODEM_RESTART_KEY] = false
            preferences[AUTO_TBOX_REBOOT_KEY] = false
            preferences[AUTO_STOP_TBOX_APP_KEY] = false
            preferences[AUTO_SUSPEND_TBOX_APP_KEY] = false
            preferences[AUTO_PREVENT_TBOX_RESTART_KEY] = false
            preferences[GET_VOLTAGES_KEY] = false
            preferences[GET_CAN_FRAME_KEY] = false
            preferences[GET_CYCLE_SIGNAL_KEY] = false
            preferences[GET_LOC_DATA_KEY] = false
            preferences[MOCK_LOCATION] = false
            preferences[WIDGET_SHOW_INDICATOR] = false
            preferences[WIDGET_SHOW_LOC_INDICATOR] = false

            // String настройки сбрасываются в значения по умолчанию
            preferences[LOG_LEVEL_KEY] = DEFAULT_LOG_LEVEL
            preferences[TBOX_IP_KEY] = DEFAULT_TBOX_IP

            preferences[DASHBOARD_WIDGETS_KEY] = ""
        }
    }
}