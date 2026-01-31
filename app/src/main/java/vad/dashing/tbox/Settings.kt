package vad.dashing.tbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private const val DATASTORE_NAME = "vad.dashing.tbox.settings"

// Используем extension property для DataStore
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

data class FloatingDashboardWidgetConfig(
    val dataKey: String,
    val showTitle: Boolean = false,
    val showUnit: Boolean = true
)

data class FloatingDashboardConfig(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val widgetsConfig: List<FloatingDashboardWidgetConfig>,
    val rows: Int,
    val cols: Int,
    val width: Int,
    val height: Int,
    val startX: Int,
    val startY: Int,
    val background: Boolean,
    val clickAction: Boolean
)

class SettingsManager(private val context: Context) {

    companion object {
        private const val KEY_PREFIX = "vad.dashing.tbox."

        // Boolean настройки
        private val AUTO_MODEM_RESTART_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_modem_restart")
        private val AUTO_TBOX_REBOOT_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_tbox_reboot")
        private val AUTO_SUSPEND_TBOX_APP_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_suspend_tbox_app")
        private val AUTO_STOP_TBOX_APP_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_stop_tbox_app")
        private val AUTO_STOP_TBOX_MDC_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_stop_tbox_mdc")
        private val AUTO_SUSPEND_TBOX_MDC_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_suspend_tbox_mdc")
        private val AUTO_SUSPEND_TBOX_SWD_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_suspend_tbox_swd")
        private val AUTO_PREVENT_TBOX_RESTART_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_prevent_tbox_restart")
        private val GET_VOLTAGES_KEY = booleanPreferencesKey("${KEY_PREFIX}get_voltages")
        private val GET_CAN_FRAME_KEY = booleanPreferencesKey("${KEY_PREFIX}get_can_frame")
        private val GET_CYCLE_SIGNAL_KEY = booleanPreferencesKey("${KEY_PREFIX}get_cycle_signal")
        private val GET_LOC_DATA_KEY = booleanPreferencesKey("${KEY_PREFIX}get_loc_data")
        private val WIDGET_SHOW_INDICATOR = booleanPreferencesKey("${KEY_PREFIX}widget_show_indicator")
        private val WIDGET_SHOW_LOC_INDICATOR = booleanPreferencesKey("${KEY_PREFIX}widget_show_loc_indicator")
        private val MOCK_LOCATION = booleanPreferencesKey("${KEY_PREFIX}mock_location")
        private val EXPERT_MODE = booleanPreferencesKey("${KEY_PREFIX}expert_mode")
        private val LEFT_MENU_VISIBLE = booleanPreferencesKey("${KEY_PREFIX}left_menu_visible")

        private val SELECTED_TAB_KEY = stringPreferencesKey("${KEY_PREFIX}selected_tab")

        private val DASHBOARD_ROWS_KEY = intPreferencesKey("${KEY_PREFIX}dashboard_rows")
        private val DASHBOARD_COLS_KEY = intPreferencesKey("${KEY_PREFIX}dashboard_cols")
        private val DASHBOARD_CHART_KEY = booleanPreferencesKey("${KEY_PREFIX}dashboard_chart")
        private val CAN_DATA_SAVE_COUNT_KEY = intPreferencesKey("${KEY_PREFIX}can_data_save_count")

        // String настройки
        private val LOG_LEVEL_KEY = stringPreferencesKey("${KEY_PREFIX}log_level")
        private val TBOX_IP_KEY = stringPreferencesKey("${KEY_PREFIX}tbox_ip")
        private val TBOX_IP_ROTATION_KEY = booleanPreferencesKey("${KEY_PREFIX}tbox_ip_rotation")

        // Значения по умолчанию
        private const val DEFAULT_LOG_LEVEL = "DEBUG"
        private const val DEFAULT_TBOX_IP = "192.168.225.1"
        private const val DEFAULT_FLOATING_DASHBOARD_ROWS = 1
        private const val DEFAULT_FLOATING_DASHBOARD_COLS = 1
        private const val DEFAULT_FLOATING_DASHBOARD_WIDTH = 100
        private const val DEFAULT_FLOATING_DASHBOARD_HEIGHT = 100
        private const val DEFAULT_FLOATING_DASHBOARD_START_X = 50
        private const val DEFAULT_FLOATING_DASHBOARD_START_Y = 50
        private const val DEFAULT_FLOATING_DASHBOARD_ENABLED = false
        private const val DEFAULT_FLOATING_DASHBOARD_BACKGROUND = false
        private const val DEFAULT_FLOATING_DASHBOARD_CLICK_ACTION = true
        private val DEFAULT_FLOATING_DASHBOARD_WIDGETS = emptyList<FloatingDashboardWidgetConfig>()
        private const val FLOATING_DASHBOARDS_LIST_KEY = "floating_dashboards"
        private const val FLOATING_DASHBOARD_SELECTED_KEY = "floating_dashboard_selected"
        private const val DEFAULT_FLOATING_DASHBOARD_ID = "floating-1"
        private const val DEFAULT_FLOATING_DASHBOARD_NAME_1 = "Панель 1"
        private const val DEFAULT_FLOATING_DASHBOARD_NAME_2 = "Панель 2"
        private const val DEFAULT_FLOATING_DASHBOARD_NAME_3 = "Панель 3"
        private const val DEFAULT_CAN_DATA_SAVE_COUNT = 5

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

    val floatingDashboardsFlow: Flow<List<FloatingDashboardConfig>> = context.settingsDataStore.data
        .map { preferences ->
            val rawJson = preferences[getStringKey(FLOATING_DASHBOARDS_LIST_KEY)] ?: ""
            parseFloatingDashboardsJson(rawJson)
        }
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

    val autoSuspendTboxMdcFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_SUSPEND_TBOX_MDC_KEY] ?: false }
        .distinctUntilChanged()

    val autoStopTboxMdcFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_STOP_TBOX_MDC_KEY] ?: false }
        .distinctUntilChanged()

    val autoSuspendTboxSwdFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_SUSPEND_TBOX_SWD_KEY] ?: false }
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

    val leftMenuVisibleFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[LEFT_MENU_VISIBLE] ?: true }
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

    val canDataSaveCountFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[CAN_DATA_SAVE_COUNT_KEY] ?: DEFAULT_CAN_DATA_SAVE_COUNT }
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

    suspend fun saveAutoSuspendTboxMdcSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_SUSPEND_TBOX_MDC_KEY] = enabled
        }
    }

    suspend fun saveAutoStopTboxMdcSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_STOP_TBOX_MDC_KEY] = enabled
        }
    }

    suspend fun saveAutoSuspendTboxSwdSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_SUSPEND_TBOX_SWD_KEY] = enabled
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

    suspend fun saveLeftMenuVisibleSetting(visible: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[LEFT_MENU_VISIBLE] = visible
        }
    }

    suspend fun saveFloatingDashboardSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_KEY] = enabled
        }
    }

    suspend fun saveFloatingDashboardWidgets(config: List<FloatingDashboardWidgetConfig>) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_DASHBOARD_WIDGETS_KEY] = serializeWidgetsConfig(config).toString()
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

    suspend fun saveFloatingDashboards(configs: List<FloatingDashboardConfig>) {
        val normalized = configs
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .map {
                it.copy(
                    rows = it.rows.coerceIn(1, 6),
                    cols = it.cols.coerceIn(1, 6)
                )
            }
        saveCustomString(FLOATING_DASHBOARDS_LIST_KEY, serializeFloatingDashboards(normalized))
    }

    suspend fun ensureDefaultFloatingDashboards() {
        val preferences = context.settingsDataStore.data.first()
        val rawJson = preferences[getStringKey(FLOATING_DASHBOARDS_LIST_KEY)] ?: ""
        val configs = parseFloatingDashboardsJson(rawJson)
        val ensured = ensureDefaultFloatingDashboards(configs)
        if (ensured != configs) {
            saveFloatingDashboards(ensured)
        }

        val selectedKey = getStringKey(FLOATING_DASHBOARD_SELECTED_KEY)
        val selectedId = preferences[selectedKey] ?: DEFAULT_FLOATING_DASHBOARD_ID
        val resolvedId = ensured.firstOrNull { it.id == selectedId }?.id
            ?: ensured.firstOrNull()?.id
            ?: DEFAULT_FLOATING_DASHBOARD_ID
        if (resolvedId != selectedId) {
            saveCustomString(FLOATING_DASHBOARD_SELECTED_KEY, resolvedId)
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

    suspend fun saveCanDataSaveCount(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[CAN_DATA_SAVE_COUNT_KEY] = config
        }
    }

    private fun parseFloatingDashboardsJson(json: String): List<FloatingDashboardConfig> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val configs = mutableListOf<FloatingDashboardConfig>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val config = parseFloatingDashboardConfig(obj) ?: continue
                configs.add(config)
            }
            configs
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun ensureDefaultFloatingDashboards(
        configs: List<FloatingDashboardConfig>
    ): List<FloatingDashboardConfig> {
        if (configs.isEmpty()) return defaultFloatingDashboards()
        val existingIds = configs.map { it.id }.toSet()
        val missing = defaultFloatingDashboards().filter { it.id !in existingIds }
        return if (missing.isEmpty()) configs else configs + missing
    }

    private fun defaultFloatingDashboards(): List<FloatingDashboardConfig> {
        return listOf(
            createDefaultFloatingDashboard(
                DEFAULT_FLOATING_DASHBOARD_ID,
                DEFAULT_FLOATING_DASHBOARD_NAME_1
            ),
            createDefaultFloatingDashboard("floating-2", DEFAULT_FLOATING_DASHBOARD_NAME_2),
            createDefaultFloatingDashboard("floating-3", DEFAULT_FLOATING_DASHBOARD_NAME_3)
        )
    }

    private fun createDefaultFloatingDashboard(
        id: String,
        name: String
    ): FloatingDashboardConfig {
        return FloatingDashboardConfig(
            id = id,
            name = name,
            enabled = DEFAULT_FLOATING_DASHBOARD_ENABLED,
            widgetsConfig = DEFAULT_FLOATING_DASHBOARD_WIDGETS,
            rows = DEFAULT_FLOATING_DASHBOARD_ROWS,
            cols = DEFAULT_FLOATING_DASHBOARD_COLS,
            width = DEFAULT_FLOATING_DASHBOARD_WIDTH,
            height = DEFAULT_FLOATING_DASHBOARD_HEIGHT,
            startX = DEFAULT_FLOATING_DASHBOARD_START_X,
            startY = DEFAULT_FLOATING_DASHBOARD_START_Y,
            background = DEFAULT_FLOATING_DASHBOARD_BACKGROUND,
            clickAction = DEFAULT_FLOATING_DASHBOARD_CLICK_ACTION
        )
    }

    private fun parseFloatingDashboardConfig(obj: JSONObject): FloatingDashboardConfig? {
        val id = obj.optString("id").trim()
        if (id.isEmpty()) return null
        val name = obj.optString("name").ifBlank { id }
        return FloatingDashboardConfig(
            id = id,
            name = name,
            enabled = obj.optBoolean("enabled", DEFAULT_FLOATING_DASHBOARD_ENABLED),
            widgetsConfig = parseWidgetsConfig(obj),
            rows = obj.optInt("rows", DEFAULT_FLOATING_DASHBOARD_ROWS).coerceIn(1, 6),
            cols = obj.optInt("cols", DEFAULT_FLOATING_DASHBOARD_COLS).coerceIn(1, 6),
            width = obj.optInt("width", DEFAULT_FLOATING_DASHBOARD_WIDTH),
            height = obj.optInt("height", DEFAULT_FLOATING_DASHBOARD_HEIGHT),
            startX = obj.optInt("startX", DEFAULT_FLOATING_DASHBOARD_START_X),
            startY = obj.optInt("startY", DEFAULT_FLOATING_DASHBOARD_START_Y),
            background = obj.optBoolean("background", DEFAULT_FLOATING_DASHBOARD_BACKGROUND),
            clickAction = obj.optBoolean("clickAction", DEFAULT_FLOATING_DASHBOARD_CLICK_ACTION)
        )
    }

    private fun parseWidgetsConfig(obj: JSONObject): List<FloatingDashboardWidgetConfig> {
        val raw = obj.opt("widgetsConfig") ?: return DEFAULT_FLOATING_DASHBOARD_WIDGETS
        return when (raw) {
            is JSONArray -> parseWidgetsConfigArray(raw)
            is String -> parseWidgetsConfigString(raw)
            else -> DEFAULT_FLOATING_DASHBOARD_WIDGETS
        }
    }

    private fun parseWidgetsConfigArray(array: JSONArray): List<FloatingDashboardWidgetConfig> {
        val configs = mutableListOf<FloatingDashboardWidgetConfig>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            when (item) {
                is JSONObject -> {
                    val dataKey = item.optString("dataKey").ifBlank {
                        item.optString("type")
                    }
                    configs.add(
                        FloatingDashboardWidgetConfig(
                            dataKey = dataKey,
                            showTitle = item.optBoolean("showTitle", false),
                            showUnit = item.optBoolean("showUnit", true)
                        )
                    )
                }
                is String -> {
                    configs.add(FloatingDashboardWidgetConfig(dataKey = item.trim()))
                }
                else -> {
                    configs.add(FloatingDashboardWidgetConfig(dataKey = ""))
                }
            }
        }
        return configs
    }

    private fun parseWidgetsConfigString(value: String): List<FloatingDashboardWidgetConfig> {
        if (value.isBlank()) return DEFAULT_FLOATING_DASHBOARD_WIDGETS
        return value.split("|").map { dataKey ->
            FloatingDashboardWidgetConfig(dataKey = dataKey.trim())
        }
    }

    private fun serializeWidgetsConfig(
        configs: List<FloatingDashboardWidgetConfig>
    ): JSONArray {
        val array = JSONArray()
        configs.forEach { config ->
            val obj = JSONObject()
            obj.put("dataKey", config.dataKey)
            obj.put("showTitle", config.showTitle)
            obj.put("showUnit", config.showUnit)
            array.put(obj)
        }
        return array
    }

    private fun serializeFloatingDashboards(configs: List<FloatingDashboardConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            val obj = JSONObject()
            obj.put("id", config.id)
            obj.put("name", config.name)
            obj.put("enabled", config.enabled)
            obj.put("widgetsConfig", serializeWidgetsConfig(config.widgetsConfig))
            obj.put("rows", config.rows)
            obj.put("cols", config.cols)
            obj.put("width", config.width)
            obj.put("height", config.height)
            obj.put("startX", config.startX)
            obj.put("startY", config.startY)
            obj.put("background", config.background)
            obj.put("clickAction", config.clickAction)
            array.put(obj)
        }
        return array.toString()
    }
}