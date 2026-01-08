package vad.dashing.tbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

private const val DATASTORE_NAME = "vad.dashing.tbox.settings"

// Используем extension property для DataStore
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

class SettingsManager(private val context: Context) {

    companion object {
        private const val KEY_PREFIX = "vad.dashing.tbox."

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

        private val DASHBOARD_ROWS_KEY = intPreferencesKey("${KEY_PREFIX}dashboard_rows")
        private val DASHBOARD_COLS_KEY = intPreferencesKey("${KEY_PREFIX}dashboard_cols")
        private val DASHBOARD_CHART_KEY = booleanPreferencesKey("${KEY_PREFIX}dashboard_chart")

        // String настройки
        private val LOG_LEVEL_KEY = stringPreferencesKey("${KEY_PREFIX}log_level")
        private val TBOX_IP_KEY = stringPreferencesKey("${KEY_PREFIX}tbox_ip")
        private val TBOX_IP_ROTATION_KEY = booleanPreferencesKey("${KEY_PREFIX}tbox_ip_rotation")

        // Значения по умолчанию
        private const val DEFAULT_LOG_LEVEL = "DEBUG"
        private const val DEFAULT_TBOX_IP = "192.168.225.1"

        // Кэш ключей для производительности
        private val stringKeysCache = mutableMapOf<String, Preferences.Key<String>>()

        // Ключ для сохранения конфигурации виджетов
        private val DASHBOARD_WIDGETS_KEY = stringPreferencesKey("${KEY_PREFIX}dashboard_widgets")

        private val FLOATING_DASHBOARD_KEY = booleanPreferencesKey("${KEY_PREFIX}floating_dashboard")
        private val FLOATING_DASHBOARD_WIDGETS_KEY = stringPreferencesKey("${KEY_PREFIX}floating_dashboard_widgets")
        private val FLOATING_DASHBOARD_WIDTH_KEY = intPreferencesKey("${KEY_PREFIX}floating_dashboard_width")
        private val FLOATING_DASHBOARD_HEIGHT_KEY = intPreferencesKey("${KEY_PREFIX}floating_dashboard_height")
        private val FLOATING_DASHBOARD_START_X_KEY = intPreferencesKey("${KEY_PREFIX}floating_dashboard_start_x")
        private val FLOATING_DASHBOARD_START_Y_KEY = intPreferencesKey("${KEY_PREFIX}floating_dashboard_start_y")
        private val FLOATING_DASHBOARD_ROWS_KEY = intPreferencesKey("${KEY_PREFIX}floating_dashboard_rows")
        private val FLOATING_DASHBOARD_COLS_KEY = intPreferencesKey("${KEY_PREFIX}floating_dashboard_cols")
        private val FLOATING_DASHBOARD_BACKGROUND_KEY = booleanPreferencesKey("${KEY_PREFIX}floating_dashboard_background")
        private val FLOATING_DASHBOARD_CLICK_ACTION_KEY = booleanPreferencesKey("${KEY_PREFIX}floating_dashboard_click_action")
    }

    // Flow для конфигурации виджетов
    val dashboardWidgetsFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[DASHBOARD_WIDGETS_KEY] ?: "" }
        .distinctUntilChanged()

    val floatingDashboardFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[FLOATING_DASHBOARD_KEY] ?: false }
        .distinctUntilChanged()

    val floatingDashboardWidgetsFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[FLOATING_DASHBOARD_WIDGETS_KEY] ?: "" }
        .distinctUntilChanged()

    val floatingDashboardRowsFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FLOATING_DASHBOARD_ROWS_KEY] ?: 1 }
        .distinctUntilChanged()

    val floatingDashboardColsFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FLOATING_DASHBOARD_COLS_KEY] ?: 1 }
        .distinctUntilChanged()

    val floatingDashboardHeightFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FLOATING_DASHBOARD_HEIGHT_KEY] ?: 100 }
        .distinctUntilChanged()

    val floatingDashboardWidthFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FLOATING_DASHBOARD_WIDTH_KEY] ?: 100 }
        .distinctUntilChanged()

    val floatingDashboardStartXFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FLOATING_DASHBOARD_START_X_KEY] ?: 50 }
        .distinctUntilChanged()

    val floatingDashboardStartYFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FLOATING_DASHBOARD_START_Y_KEY] ?: 50 }
        .distinctUntilChanged()

    val floatingDashboardBackgroundFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[FLOATING_DASHBOARD_BACKGROUND_KEY] ?: false }
        .distinctUntilChanged()

    val floatingDashboardClickActionFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[FLOATING_DASHBOARD_CLICK_ACTION_KEY] ?: true }
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
        .map { preferences -> preferences[GET_CAN_FRAME_KEY] ?: true }
        .distinctUntilChanged()

    val getCycleSignalFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_CYCLE_SIGNAL_KEY] ?: false }
        .distinctUntilChanged()

    val getLocDataFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_LOC_DATA_KEY] ?: true }
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

    val tboxIPRotationFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[TBOX_IP_ROTATION_KEY] ?: false }
        .distinctUntilChanged()

    val selectedTabFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SELECTED_TAB_KEY]?.toIntOrNull() ?: 0
        }
        .distinctUntilChanged()

    val dashboardRowsFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[DASHBOARD_ROWS_KEY] ?: 3 }
        .distinctUntilChanged()

    val dashboardColsFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[DASHBOARD_COLS_KEY] ?: 4 }
        .distinctUntilChanged()

    val dashboardChartFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[DASHBOARD_CHART_KEY] ?: false }
        .distinctUntilChanged()

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

    suspend fun saveFloatingDashboardSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_KEY] = enabled
        }
    }

    suspend fun saveFloatingDashboardWidgets(config: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_WIDGETS_KEY] = config
        }
    }

    suspend fun saveFloatingDashboardRows(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_ROWS_KEY] = config
        }
    }

    suspend fun saveFloatingDashboardCols(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_COLS_KEY] = config
        }
    }

    suspend fun saveFloatingDashboardHeight(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_HEIGHT_KEY] = config
        }
    }

    suspend fun saveFloatingDashboardWidth(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_WIDTH_KEY] = config
        }
    }

    suspend fun saveFloatingDashboardStartX(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_START_X_KEY] = config
        }
    }

    suspend fun saveFloatingDashboardStartY(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_START_Y_KEY] = config
        }
    }

    suspend fun saveFloatingDashboardBackground(config: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_BACKGROUND_KEY] = config
        }
    }

    suspend fun saveFloatingDashboardClickAction(config: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_CLICK_ACTION_KEY] = config
        }
    }

    suspend fun saveTboxIP(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[TBOX_IP_KEY] = value
        }
    }

    suspend fun saveTboxIPRotationSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[TBOX_IP_ROTATION_KEY] = enabled
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

    suspend fun saveDashboardRows(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_ROWS_KEY] = config
        }
    }

    suspend fun saveDashboardCols(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_COLS_KEY] = config
        }
    }

    suspend fun saveDashboardChart(config: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_CHART_KEY] = config
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
            preferences[GET_CAN_FRAME_KEY] = true
            preferences[GET_CYCLE_SIGNAL_KEY] = false
            preferences[GET_LOC_DATA_KEY] = true
            preferences[MOCK_LOCATION] = false
            preferences[WIDGET_SHOW_INDICATOR] = false
            preferences[WIDGET_SHOW_LOC_INDICATOR] = false

            // String настройки сбрасываются в значения по умолчанию
            preferences[LOG_LEVEL_KEY] = DEFAULT_LOG_LEVEL
            preferences[TBOX_IP_KEY] = DEFAULT_TBOX_IP

            preferences[DASHBOARD_WIDGETS_KEY] = ""

            preferences[DASHBOARD_ROWS_KEY] = 3
            preferences[DASHBOARD_COLS_KEY] = 4
            preferences[DASHBOARD_CHART_KEY] = false
        }
    }
}