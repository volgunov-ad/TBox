package vad.dashing.tbox

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val DATASTORE_NAME = "vad.dashing.tbox.settings"

// Используем extension property для DataStore
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

data class FloatingDashboardWidgetConfig(
    val dataKey: String,
    val showTitle: Boolean = false,
    val showUnit: Boolean = true,
    /** When true, composite two-metric widgets show both values on one line (em-space separated). */
    val singleLineDualMetrics: Boolean = false,
    val scale: Float = 1.0f,
    val shape: Int = 0,
    val textColorLight: Int = DEFAULT_WIDGET_TEXT_COLOR_LIGHT,
    val textColorDark: Int = DEFAULT_WIDGET_TEXT_COLOR_DARK,
    val backgroundColorLight: Int? = null,
    val backgroundColorDark: Int? = null,
    val mediaPlayers: List<String> = emptyList(),
    val mediaSelectedPlayer: String = "",
    val mediaAutoPlayOnInit: Boolean = false,
    /** If true (and [mediaAutoPlayOnInit]), delay auto-play until engine RPM is greater than zero. */
    val mediaAutoPlayOnlyWhenEngineRunning: Boolean = false,
    /** Package name of the app to launch (only for `appLauncherWidget`). */
    val launcherAppPackage: String = "",
    /** System app-widget id when the tile shows a third-party app widget (`externalAppWidget`). */
    val appWidgetId: Int? = null
)

/** Normalized top-left of the MainScreen settings button: x,y in [0,1] vs usable width/height. */
data class MainScreenSettingsButtonPosition(
    val x: Float,
    val y: Float
) {
    companion object {
        /** Top-right area (similar to previous fixed layout). */
        val Default = MainScreenSettingsButtonPosition(0.92f, 0.04f)
    }
}

/** Normalized top-left of the MainScreen "+" add-panel control (same coordinate space as [MainScreenSettingsButtonPosition]). */
data class MainScreenAddButtonPosition(
    val x: Float,
    val y: Float
) {
    companion object {
        /** Top-right row, immediately left of [MainScreenSettingsButtonPosition.Default] (same Y). */
        val Default = MainScreenAddButtonPosition(0.84f, 0.04f)
    }
}

/**
 * Dashboard panel on the in-app MainScreen (not a system overlay).
 * Position uses the same convention as [MainScreenSettingsButtonPosition]: normalized against
 * `(containerSize - panelSize)` along each axis. [relWidth] / [relHeight] are fractions of the full container.
 */
data class MainScreenPanelConfig(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val widgetsConfig: List<FloatingDashboardWidgetConfig>,
    val rows: Int,
    val cols: Int,
    val relX: Float,
    val relY: Float,
    val relWidth: Float,
    val relHeight: Float,
    val background: Boolean,
    val clickAction: Boolean,
    val showTboxDisconnectIndicator: Boolean = false
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
    val clickAction: Boolean,
    val showTboxDisconnectIndicator: Boolean = true
)

class SettingsManager(private val context: Context) {

    companion object {
        /** Tab index that shows the home [vad.dashing.tbox.ui.MainScreen] instead of [vad.dashing.tbox.ui.TboxScreen]. */
        const val MAIN_SCREEN_SELECTED_TAB_INDEX = 100

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
        private val MAIN_SCREEN_OPEN_ON_BOOT_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_open_on_boot")
        private val MAIN_SCREEN_WALLPAPER_LIGHT_SET_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_light")
        private val MAIN_SCREEN_WALLPAPER_DARK_SET_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_dark")
        private val MAIN_SCREEN_WALLPAPER_CROP_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_crop")

        private val SELECTED_TAB_KEY = stringPreferencesKey("${KEY_PREFIX}selected_tab")

        private val DASHBOARD_ROWS_KEY = intPreferencesKey("${KEY_PREFIX}dashboard_rows")
        private val DASHBOARD_COLS_KEY = intPreferencesKey("${KEY_PREFIX}dashboard_cols")
        private val DASHBOARD_CHART_KEY = booleanPreferencesKey("${KEY_PREFIX}dashboard_chart")
        private val CAN_DATA_SAVE_COUNT_KEY = intPreferencesKey("${KEY_PREFIX}can_data_save_count")
        private val FUEL_TANK_LITERS_KEY = intPreferencesKey("${KEY_PREFIX}fuel_tank_liters")
        private val SPLIT_TRIP_TIME_MINUTES_KEY = intPreferencesKey("${KEY_PREFIX}split_trip_time_minutes")

        // String настройки
        private val LOG_LEVEL_KEY = stringPreferencesKey("${KEY_PREFIX}log_level")
        // Значения по умолчанию
        private const val DEFAULT_LOG_LEVEL = "DEBUG"
        private const val DEFAULT_FLOATING_DASHBOARD_ROWS = 1
        private const val DEFAULT_FLOATING_DASHBOARD_COLS = 1
        private const val DEFAULT_FLOATING_DASHBOARD_WIDTH = 100
        private const val DEFAULT_FLOATING_DASHBOARD_HEIGHT = 100
        private const val DEFAULT_FLOATING_DASHBOARD_START_X = 50
        private const val DEFAULT_FLOATING_DASHBOARD_START_Y = 50
        private const val DEFAULT_FLOATING_DASHBOARD_ENABLED = false
        private const val DEFAULT_FLOATING_DASHBOARD_BACKGROUND = false
        private const val DEFAULT_FLOATING_DASHBOARD_CLICK_ACTION = true
        private const val DEFAULT_FLOATING_DASHBOARD_SHOW_TBOX_DISCONNECT_INDICATOR = true
        private val DEFAULT_FLOATING_DASHBOARD_WIDGETS = emptyList<FloatingDashboardWidgetConfig>()
        private const val DEFAULT_MAIN_SCREEN_PANEL_ROWS = 1
        private const val DEFAULT_MAIN_SCREEN_PANEL_COLS = 1
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_X = 0.05f
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_Y = 0.1f
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_WIDTH = 0.4f
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_HEIGHT = 0.3f
        private const val DEFAULT_MAIN_SCREEN_PANEL_ENABLED = true
        private const val DEFAULT_MAIN_SCREEN_PANEL_BACKGROUND = false
        private const val DEFAULT_MAIN_SCREEN_PANEL_CLICK_ACTION = true
        private const val DEFAULT_MAIN_SCREEN_PANEL_SHOW_TBOX_DISCONNECT = false
        private const val FLOATING_DASHBOARDS_LIST_KEY = "floating_dashboards"
        private const val MAIN_SCREEN_DASHBOARDS_LIST_KEY = "main_screen_dashboards"
        private const val MAIN_SCREEN_SETTINGS_BUTTON_KEY = "main_screen_settings_button"
        private const val MAIN_SCREEN_ADD_BUTTON_KEY = "main_screen_add_button"

        /** Copied image for MainScreen when global app theme is light (theme != 2). */
        const val MAIN_SCREEN_WALLPAPER_LIGHT_FILE = "main_screen_wallpaper/light"
        /** Copied image for MainScreen when global app theme is dark (theme == 2). */
        const val MAIN_SCREEN_WALLPAPER_DARK_FILE = "main_screen_wallpaper/dark"
        private const val DEFAULT_CAN_DATA_SAVE_COUNT = 5
        private const val DEFAULT_FUEL_TANK_LITERS = 57
        private const val DEFAULT_SPLIT_TRIP_TIME_MINUTES = 5

        // Кэш ключей для производительности
        private val stringKeysCache = mutableMapOf<String, Preferences.Key<String>>()

        // Ключ для сохранения конфигурации виджетов
        private val DASHBOARD_WIDGETS_KEY = stringPreferencesKey("${KEY_PREFIX}dashboard_widgets")

    }

    // Flow для конфигурации виджетов
    val dashboardWidgetsFlow: Flow<List<FloatingDashboardWidgetConfig>> = context.settingsDataStore.data
        .map { preferences ->
            parseWidgetConfigsFromString(preferences[DASHBOARD_WIDGETS_KEY] ?: "")
        }
        .distinctUntilChanged()

    val floatingDashboardsFlow: Flow<List<FloatingDashboardConfig>> = context.settingsDataStore.data
        .map { preferences ->
            val rawJson = preferences[getStringKey(FLOATING_DASHBOARDS_LIST_KEY)] ?: ""
            parseFloatingDashboardsJson(rawJson)
        }
        .distinctUntilChanged()

    val mainScreenDashboardsFlow: Flow<List<MainScreenPanelConfig>> = context.settingsDataStore.data
        .map { preferences ->
            val rawJson = preferences[getStringKey(MAIN_SCREEN_DASHBOARDS_LIST_KEY)] ?: ""
            parseMainScreenDashboardsJson(rawJson)
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

    val selectedTabFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SELECTED_TAB_KEY]?.toIntOrNull()
                ?: MAIN_SCREEN_SELECTED_TAB_INDEX
        }
        .distinctUntilChanged()

    val mainScreenSettingsButtonFlow: Flow<MainScreenSettingsButtonPosition> =
        context.settingsDataStore.data
            .map { preferences ->
                parseMainScreenSettingsButtonJson(
                    preferences[getStringKey(MAIN_SCREEN_SETTINGS_BUTTON_KEY)] ?: ""
                )
            }
            .distinctUntilChanged()

    val mainScreenAddButtonFlow: Flow<MainScreenAddButtonPosition> =
        context.settingsDataStore.data
            .map { preferences ->
                parseMainScreenAddButtonJson(
                    preferences[getStringKey(MAIN_SCREEN_ADD_BUTTON_KEY)] ?: ""
                )
            }
            .distinctUntilChanged()

    /** After device boot, open [MainActivity] on the main home screen (tab 100) when enabled. */
    val mainScreenOpenOnBootFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_OPEN_ON_BOOT_KEY] ?: false }
        .distinctUntilChanged()

    val mainScreenWallpaperLightSetFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_LIGHT_SET_KEY] ?: false }
        .distinctUntilChanged()

    val mainScreenWallpaperDarkSetFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_DARK_SET_KEY] ?: false }
        .distinctUntilChanged()

    /** `true`: fill screen with Crop; `false`: Fit (whole image, possible side bars). */
    val mainScreenWallpaperCropFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_CROP_KEY] ?: false }
        .distinctUntilChanged()

    // String flows
    val logLevelFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[LOG_LEVEL_KEY] ?: DEFAULT_LOG_LEVEL }
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

    val fuelTankLitersFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FUEL_TANK_LITERS_KEY] ?: DEFAULT_FUEL_TANK_LITERS }
        .distinctUntilChanged()

    val splitTripTimeMinutesFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[SPLIT_TRIP_TIME_MINUTES_KEY] ?: DEFAULT_SPLIT_TRIP_TIME_MINUTES }
        .distinctUntilChanged()

    // Suspend функции для сохранения настроек

    // Сохранение конфигурации виджетов
    suspend fun saveDashboardWidgets(config: List<FloatingDashboardWidgetConfig>) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_WIDGETS_KEY] = serializeWidgetConfigs(config)
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

    suspend fun saveSelectedTab(tabIndex: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SELECTED_TAB_KEY] = tabIndex.toString()
        }
    }

    /**
     * One-time: after adding the Trips tab, shift stored indices 5–9 by +1 so the same
     * screen stays selected (Main screen index 100 is unchanged).
     */
    suspend fun migrateSelectedTabIndexIfNeeded() {
        val raw = context.settingsDataStore.data.first()[SELECTED_TAB_KEY]?.toIntOrNull()
            ?: return
        if (raw == MAIN_SCREEN_SELECTED_TAB_INDEX) return
        if (raw in 5..9) {
            saveSelectedTab(raw + 1)
        }
    }

    suspend fun saveMainScreenSettingsButton(position: MainScreenSettingsButtonPosition) {
        val obj = JSONObject()
        obj.put("x", position.x.coerceIn(0f, 1f).toDouble())
        obj.put("y", position.y.coerceIn(0f, 1f).toDouble())
        saveCustomString(MAIN_SCREEN_SETTINGS_BUTTON_KEY, obj.toString())
    }

    suspend fun saveMainScreenAddButton(position: MainScreenAddButtonPosition) {
        val obj = JSONObject()
        obj.put("x", position.x.coerceIn(0f, 1f).toDouble())
        obj.put("y", position.y.coerceIn(0f, 1f).toDouble())
        saveCustomString(MAIN_SCREEN_ADD_BUTTON_KEY, obj.toString())
    }

    suspend fun saveMainScreenOpenOnBoot(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_OPEN_ON_BOOT_KEY] = enabled
        }
    }

    suspend fun setMainScreenWallpaperLight(sourceUri: Uri?) {
        setMainScreenWallpaper(
            sourceUri = sourceUri,
            relativePath = MAIN_SCREEN_WALLPAPER_LIGHT_FILE,
            prefKey = MAIN_SCREEN_WALLPAPER_LIGHT_SET_KEY
        )
    }

    suspend fun setMainScreenWallpaperDark(sourceUri: Uri?) {
        setMainScreenWallpaper(
            sourceUri = sourceUri,
            relativePath = MAIN_SCREEN_WALLPAPER_DARK_FILE,
            prefKey = MAIN_SCREEN_WALLPAPER_DARK_SET_KEY
        )
    }

    suspend fun saveMainScreenWallpaperCrop(crop: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_WALLPAPER_CROP_KEY] = crop
        }
    }

    private suspend fun setMainScreenWallpaper(
        sourceUri: Uri?,
        relativePath: String,
        prefKey: Preferences.Key<Boolean>
    ) {
        withContext(Dispatchers.IO) {
            val dest = File(context.filesDir, relativePath)
            dest.parentFile?.mkdirs()
            if (sourceUri == null) {
                if (dest.exists()) dest.delete()
                context.settingsDataStore.edit { preferences ->
                    preferences[prefKey] = false
                }
                return@withContext
            }
            val copiedOk = runCatching {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.exists() && dest.length() > 0L
            }.getOrElse {
                if (dest.exists()) dest.delete()
                false
            }
            context.settingsDataStore.edit { preferences ->
                preferences[prefKey] = copiedOk
            }
        }
    }

    suspend fun saveMainScreenDashboards(configs: List<MainScreenPanelConfig>) {
        val normalized = configs
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .map {
                it.copy(
                    rows = it.rows.coerceIn(1, 6),
                    cols = it.cols.coerceIn(1, 6),
                    relX = it.relX.coerceIn(0f, 1f),
                    relY = it.relY.coerceIn(0f, 1f),
                    relWidth = it.relWidth.coerceIn(0.08f, 1f),
                    relHeight = it.relHeight.coerceIn(0.08f, 1f)
                )
            }
        saveCustomString(MAIN_SCREEN_DASHBOARDS_LIST_KEY, serializeMainScreenDashboards(normalized))
    }

    suspend fun ensureDefaultFloatingDashboards() {
        // Historical API: empty floating panel list is valid; no default injection.
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

    suspend fun saveFuelTankLiters(liters: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FUEL_TANK_LITERS_KEY] = liters.coerceIn(1, 500)
        }
    }

    suspend fun saveSplitTripTimeMinutes(minutes: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SPLIT_TRIP_TIME_MINUTES_KEY] = minutes.coerceIn(1, 120)
        }
    }

    private fun parseMainScreenSettingsButtonJson(raw: String): MainScreenSettingsButtonPosition {
        if (raw.isBlank()) return MainScreenSettingsButtonPosition.Default
        return try {
            val o = JSONObject(raw)
            MainScreenSettingsButtonPosition(
                x = o.optDouble("x", MainScreenSettingsButtonPosition.Default.x.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f),
                y = o.optDouble("y", MainScreenSettingsButtonPosition.Default.y.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)
            )
        } catch (_: Exception) {
            MainScreenSettingsButtonPosition.Default
        }
    }

    private fun parseMainScreenAddButtonJson(raw: String): MainScreenAddButtonPosition {
        if (raw.isBlank()) return MainScreenAddButtonPosition.Default
        return try {
            val o = JSONObject(raw)
            MainScreenAddButtonPosition(
                x = o.optDouble("x", MainScreenAddButtonPosition.Default.x.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f),
                y = o.optDouble("y", MainScreenAddButtonPosition.Default.y.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)
            )
        } catch (_: Exception) {
            MainScreenAddButtonPosition.Default
        }
    }

    private fun parseMainScreenDashboardsJson(json: String): List<MainScreenPanelConfig> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val configs = mutableListOf<MainScreenPanelConfig>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val config = parseMainScreenPanelConfig(obj) ?: continue
                configs.add(config)
            }
            configs
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseMainScreenPanelConfig(obj: JSONObject): MainScreenPanelConfig? {
        val id = obj.optString("id").trim()
        if (id.isEmpty()) return null
        val name = obj.optString("name").ifBlank { id }
        return MainScreenPanelConfig(
            id = id,
            name = name,
            enabled = obj.optBoolean("enabled", DEFAULT_MAIN_SCREEN_PANEL_ENABLED),
            widgetsConfig = parseWidgetConfigsFromAny(obj.opt("widgetsConfig")),
            rows = obj.optInt("rows", DEFAULT_MAIN_SCREEN_PANEL_ROWS).coerceIn(1, 6),
            cols = obj.optInt("cols", DEFAULT_MAIN_SCREEN_PANEL_COLS).coerceIn(1, 6),
            relX = obj.optDouble("relX", DEFAULT_MAIN_SCREEN_PANEL_REL_X.toDouble()).toFloat()
                .coerceIn(0f, 1f),
            relY = obj.optDouble("relY", DEFAULT_MAIN_SCREEN_PANEL_REL_Y.toDouble()).toFloat()
                .coerceIn(0f, 1f),
            relWidth = obj.optDouble("relWidth", DEFAULT_MAIN_SCREEN_PANEL_REL_WIDTH.toDouble())
                .toFloat().coerceIn(0.08f, 1f),
            relHeight = obj.optDouble("relHeight", DEFAULT_MAIN_SCREEN_PANEL_REL_HEIGHT.toDouble())
                .toFloat().coerceIn(0.08f, 1f),
            background = obj.optBoolean("background", DEFAULT_MAIN_SCREEN_PANEL_BACKGROUND),
            clickAction = obj.optBoolean("clickAction", DEFAULT_MAIN_SCREEN_PANEL_CLICK_ACTION),
            showTboxDisconnectIndicator = obj.optBoolean(
                "showTboxDisconnectIndicator",
                DEFAULT_MAIN_SCREEN_PANEL_SHOW_TBOX_DISCONNECT
            )
        )
    }

    private fun serializeMainScreenDashboards(configs: List<MainScreenPanelConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            val o = JSONObject()
            o.put("id", config.id)
            o.put("name", config.name)
            o.put("enabled", config.enabled)
            o.put("widgetsConfig", serializeWidgetConfigsToJsonArray(config.widgetsConfig))
            o.put("rows", config.rows)
            o.put("cols", config.cols)
            o.put("relX", config.relX.toDouble())
            o.put("relY", config.relY.toDouble())
            o.put("relWidth", config.relWidth.toDouble())
            o.put("relHeight", config.relHeight.toDouble())
            o.put("background", config.background)
            o.put("clickAction", config.clickAction)
            o.put("showTboxDisconnectIndicator", config.showTboxDisconnectIndicator)
            array.put(o)
        }
        return array.toString()
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

    private fun parseFloatingDashboardConfig(obj: JSONObject): FloatingDashboardConfig? {
        val id = obj.optString("id").trim()
        if (id.isEmpty()) return null
        val name = obj.optString("name").ifBlank { id }
        return FloatingDashboardConfig(
            id = id,
            name = name,
            enabled = obj.optBoolean("enabled", DEFAULT_FLOATING_DASHBOARD_ENABLED),
            widgetsConfig = parseWidgetConfigsFromAny(obj.opt("widgetsConfig")),
            rows = obj.optInt("rows", DEFAULT_FLOATING_DASHBOARD_ROWS).coerceIn(1, 6),
            cols = obj.optInt("cols", DEFAULT_FLOATING_DASHBOARD_COLS).coerceIn(1, 6),
            width = obj.optInt("width", DEFAULT_FLOATING_DASHBOARD_WIDTH),
            height = obj.optInt("height", DEFAULT_FLOATING_DASHBOARD_HEIGHT),
            startX = obj.optInt("startX", DEFAULT_FLOATING_DASHBOARD_START_X),
            startY = obj.optInt("startY", DEFAULT_FLOATING_DASHBOARD_START_Y),
            background = obj.optBoolean("background", DEFAULT_FLOATING_DASHBOARD_BACKGROUND),
            clickAction = obj.optBoolean("clickAction", DEFAULT_FLOATING_DASHBOARD_CLICK_ACTION),
            showTboxDisconnectIndicator = obj.optBoolean(
                "showTboxDisconnectIndicator",
                DEFAULT_FLOATING_DASHBOARD_SHOW_TBOX_DISCONNECT_INDICATOR
            )
        )
    }

    private fun serializeFloatingDashboards(configs: List<FloatingDashboardConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            val obj = JSONObject()
            obj.put("id", config.id)
            obj.put("name", config.name)
            obj.put("enabled", config.enabled)
            obj.put("widgetsConfig", serializeWidgetConfigsToJsonArray(config.widgetsConfig))
            obj.put("rows", config.rows)
            obj.put("cols", config.cols)
            obj.put("width", config.width)
            obj.put("height", config.height)
            obj.put("startX", config.startX)
            obj.put("startY", config.startY)
            obj.put("background", config.background)
            obj.put("clickAction", config.clickAction)
            obj.put("showTboxDisconnectIndicator", config.showTboxDisconnectIndicator)
            array.put(obj)
        }
        return array.toString()
    }

}