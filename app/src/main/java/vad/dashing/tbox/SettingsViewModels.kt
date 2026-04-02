package vad.dashing.tbox

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.Boolean

/**
 * Whole-panel fields from the tile dialog, applied in the same persistence write as [widgetsConfig]
 * to avoid lost updates when multiple coroutines read stale [mainScreenDashboards] snapshots.
 */
data class MainScreenWholePanelFieldsForWidgetDialogSave(
    val name: String,
    val rows: Int,
    val cols: Int,
    val showTboxDisconnectIndicator: Boolean,
    val clickAction: Boolean,
)

data class FloatingWholePanelFieldsForWidgetDialogSave(
    val name: String,
    val rows: Int,
    val cols: Int,
    val showTboxDisconnectIndicator: Boolean,
    val clickAction: Boolean,
)

/** Merges widget list and optional whole-panel draft; used by [SettingsViewModel] and unit tests. */
internal fun mergeMainScreenPanelForWidgetDialogSave(
    current: MainScreenPanelConfig,
    widgetsConfig: List<FloatingDashboardWidgetConfig>,
    wholePanelFromWidgetDialog: MainScreenWholePanelFieldsForWidgetDialogSave?,
): MainScreenPanelConfig {
    val base = current.copy(widgetsConfig = widgetsConfig)
    val w = wholePanelFromWidgetDialog ?: return base
    return base.copy(
        name = w.name,
        rows = w.rows.coerceIn(1, 6),
        cols = w.cols.coerceIn(1, 6),
        showTboxDisconnectIndicator = w.showTboxDisconnectIndicator,
        clickAction = w.clickAction
    )
}

internal fun mergeFloatingDashboardForWidgetDialogSave(
    current: FloatingDashboardConfig,
    widgetsConfig: List<FloatingDashboardWidgetConfig>,
    wholePanelFromWidgetDialog: FloatingWholePanelFieldsForWidgetDialogSave?,
): FloatingDashboardConfig {
    val base = current.copy(widgetsConfig = widgetsConfig)
    val w = wholePanelFromWidgetDialog ?: return base
    return base.copy(
        name = w.name,
        rows = w.rows.coerceIn(1, 6),
        cols = w.cols.coerceIn(1, 6),
        showTboxDisconnectIndicator = w.showTboxDisconnectIndicator,
        clickAction = w.clickAction
    )
}

class SettingsViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    companion object {
        private const val DEFAULT_LOG_LEVEL = "DEBUG"
        private const val DEFAULT_FLOATING_DASHBOARD_ID = "floating-1"
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
        private const val DEFAULT_MAIN_SCREEN_PANEL_ID = "main-screen-1"
        private const val DEFAULT_MAIN_SCREEN_PANEL_ROWS = 1
        private const val DEFAULT_MAIN_SCREEN_PANEL_COLS = 1
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_X = 0.05f
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_Y = 0.1f
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_WIDTH = 0.4f
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_HEIGHT = 0.3f
        private const val DEFAULT_MAIN_SCREEN_PANEL_ENABLED = true
        private const val DEFAULT_MAIN_SCREEN_PANEL_BACKGROUND = false
        private const val DEFAULT_MAIN_SCREEN_PANEL_CLICK_ACTION = false
        private const val DEFAULT_MAIN_SCREEN_PANEL_SHOW_TBOX_DISCONNECT = false
        private val DEFAULT_MAIN_SCREEN_PANEL_WIDGETS = emptyList<FloatingDashboardWidgetConfig>()
    }

    private fun createDefaultMainScreenPanel(id: String, name: String): MainScreenPanelConfig {
        return MainScreenPanelConfig(
            id = id,
            name = name,
            enabled = DEFAULT_MAIN_SCREEN_PANEL_ENABLED,
            widgetsConfig = DEFAULT_MAIN_SCREEN_PANEL_WIDGETS,
            rows = DEFAULT_MAIN_SCREEN_PANEL_ROWS,
            cols = DEFAULT_MAIN_SCREEN_PANEL_COLS,
            relX = DEFAULT_MAIN_SCREEN_PANEL_REL_X,
            relY = DEFAULT_MAIN_SCREEN_PANEL_REL_Y,
            relWidth = DEFAULT_MAIN_SCREEN_PANEL_REL_WIDTH,
            relHeight = DEFAULT_MAIN_SCREEN_PANEL_REL_HEIGHT,
            background = DEFAULT_MAIN_SCREEN_PANEL_BACKGROUND,
            clickAction = DEFAULT_MAIN_SCREEN_PANEL_CLICK_ACTION,
            showTboxDisconnectIndicator = DEFAULT_MAIN_SCREEN_PANEL_SHOW_TBOX_DISCONNECT
        )
    }

    private fun fallbackMainScreenPanel(id: String): MainScreenPanelConfig {
        return createDefaultMainScreenPanel(id, id)
    }

    private val floatingDashboardConfigStates =
        mutableMapOf<String, StateFlow<FloatingDashboardConfig>>()
    private val mainScreenPanelConfigStates =
        mutableMapOf<String, StateFlow<MainScreenPanelConfig>>()
    private val selectedMainScreenPanelIdState = MutableStateFlow(DEFAULT_MAIN_SCREEN_PANEL_ID)
    private val _mainScreenPanelDeleteInProgressId = MutableStateFlow<String?>(null)
    val mainScreenPanelDeleteInProgressId: StateFlow<String?> =
        _mainScreenPanelDeleteInProgressId.asStateFlow()
    private var latestDashboardWidgetsConfig: List<FloatingDashboardWidgetConfig> = emptyList()

    val isAutoModemRestartEnabled = settingsManager.autoModemRestartFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isAutoTboxRebootEnabled = settingsManager.autoTboxRebootFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isWidgetShowIndicatorEnabled = settingsManager.widgetShowIndicatorFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isWidgetShowLocIndicatorEnabled = settingsManager.widgetShowLocIndicatorFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isMockLocationEnabled = settingsManager.mockLocationFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isAutoSuspendTboxAppEnabled = settingsManager.autoSuspendTboxAppFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isAutoStopTboxAppEnabled = settingsManager.autoStopTboxAppFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isAutoSuspendTboxMdcEnabled = settingsManager.autoSuspendTboxMdcFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isAutoStopTboxMdcEnabled = settingsManager.autoStopTboxMdcFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isAutoSuspendTboxSwdEnabled = settingsManager.autoSuspendTboxSwdFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isAutoPreventTboxRestartEnabled = settingsManager.autoPreventTboxRestartFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isGetVoltagesEnabled = settingsManager.getVoltagesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isGetCanFrameEnabled = settingsManager.getCanFrameFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isGetCycleSignalEnabled = settingsManager.getCycleSignalFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isGetLocDataEnabled = settingsManager.getLocDataFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isExpertModeEnabled = settingsManager.expertModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isLeftMenuVisible = settingsManager.leftMenuVisibleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val floatingDashboards = settingsManager.floatingDashboardsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val selectedFloatingDashboardIdState = MutableStateFlow(DEFAULT_FLOATING_DASHBOARD_ID)

    private val _floatingPanelDeleteInProgressId = MutableStateFlow<String?>(null)
    val floatingPanelDeleteInProgressId: StateFlow<String?> =
        _floatingPanelDeleteInProgressId.asStateFlow()

    val activeFloatingDashboardId = combine(floatingDashboards, selectedFloatingDashboardIdState) {
            configs, selected ->
        configs.firstOrNull { it.id == selected }?.id
            ?: configs.firstOrNull()?.id
            ?: DEFAULT_FLOATING_DASHBOARD_ID
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DEFAULT_FLOATING_DASHBOARD_ID
    )

    private val activeFloatingDashboardConfig = combine(
        floatingDashboards,
        activeFloatingDashboardId
    ) { configs, id ->
        configs.firstOrNull { it.id == id } ?: fallbackFloatingDashboard(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = fallbackFloatingDashboard(DEFAULT_FLOATING_DASHBOARD_ID)
    )

    val isFloatingDashboardEnabled = activeFloatingDashboardConfig
        .map { it.enabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_ENABLED
        )

    val floatingDashboardWidgetsConfig = activeFloatingDashboardConfig
        .map { it.widgetsConfig }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_WIDGETS
        )

    val floatingDashboardRows = activeFloatingDashboardConfig
        .map { it.rows }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_ROWS
        )

    val floatingDashboardCols = activeFloatingDashboardConfig
        .map { it.cols }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_COLS
        )

    val floatingDashboardHeight = activeFloatingDashboardConfig
        .map { it.height }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_HEIGHT
        )

    val floatingDashboardWidth = activeFloatingDashboardConfig
        .map { it.width }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_WIDTH
        )

    val floatingDashboardStartX = activeFloatingDashboardConfig
        .map { it.startX }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_START_X
        )

    val floatingDashboardStartY = activeFloatingDashboardConfig
        .map { it.startY }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_START_Y
        )

    val isFloatingDashboardClickAction = activeFloatingDashboardConfig
        .map { it.clickAction }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_CLICK_ACTION
        )

    val isFloatingDashboardShowTboxDisconnectIndicator = activeFloatingDashboardConfig
        .map { it.showTboxDisconnectIndicator }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_SHOW_TBOX_DISCONNECT_INDICATOR
        )

    val logLevel = settingsManager.logLevelFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_LOG_LEVEL
        )

    val crtVersion = settingsManager.getStringFlow("crt_version", "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val appVersion = settingsManager.getStringFlow("app_version", "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val mdcVersion = settingsManager.getStringFlow("mdc_version", "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val swdVersion = settingsManager.getStringFlow("swd_version", "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val locVersion = settingsManager.getStringFlow("loc_version", "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val swVersion = settingsManager.getStringFlow("sw_version", "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val hwVersion = settingsManager.getStringFlow("hw_version", "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val vinCode = settingsManager.getStringFlow("vin_code", "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val selectedTab = settingsManager.selectedTabFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsManager.MAIN_SCREEN_SELECTED_TAB_INDEX
        )

    val mainScreenSettingsButtonPosition = settingsManager.mainScreenSettingsButtonFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainScreenSettingsButtonPosition.Default
        )

    val mainScreenAddButtonPosition = settingsManager.mainScreenAddButtonFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainScreenAddButtonPosition.Default
        )

    val isMainScreenOpenOnBootEnabled = settingsManager.mainScreenOpenOnBootFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isMainScreenWallpaperLightSet = settingsManager.mainScreenWallpaperLightSetFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isMainScreenWallpaperDarkSet = settingsManager.mainScreenWallpaperDarkSetFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isMainScreenWallpaperCrop = settingsManager.mainScreenWallpaperCropFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _mainScreenWallpaperEpoch = MutableStateFlow(0L)
    val mainScreenWallpaperEpoch: StateFlow<Long> = _mainScreenWallpaperEpoch.asStateFlow()

    val mainScreenDashboards = settingsManager.mainScreenDashboardsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList<MainScreenPanelConfig>()
        )

    val activeMainScreenPanelId = combine(mainScreenDashboards, selectedMainScreenPanelIdState) {
            configs, selected ->
        configs.firstOrNull { it.id == selected }?.id
            ?: configs.firstOrNull()?.id
            ?: DEFAULT_MAIN_SCREEN_PANEL_ID
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DEFAULT_MAIN_SCREEN_PANEL_ID
    )

    private val activeMainScreenPanelConfig = combine(
        mainScreenDashboards,
        activeMainScreenPanelId
    ) { configs, id ->
        configs.firstOrNull { it.id == id } ?: fallbackMainScreenPanel(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = fallbackMainScreenPanel(DEFAULT_MAIN_SCREEN_PANEL_ID)
    )

    val isMainScreenPanelEnabled = activeMainScreenPanelConfig
        .map { it.enabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_MAIN_SCREEN_PANEL_ENABLED
        )

    val isMainScreenPanelClickAction = activeMainScreenPanelConfig
        .map { it.clickAction }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_MAIN_SCREEN_PANEL_CLICK_ACTION
        )

    val isMainScreenPanelShowTboxDisconnectIndicator = activeMainScreenPanelConfig
        .map { it.showTboxDisconnectIndicator }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_MAIN_SCREEN_PANEL_SHOW_TBOX_DISCONNECT
        )

    val mainScreenPanelRows = activeMainScreenPanelConfig
        .map { it.rows }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_MAIN_SCREEN_PANEL_ROWS
        )

    val mainScreenPanelCols = activeMainScreenPanelConfig
        .map { it.cols }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_MAIN_SCREEN_PANEL_COLS
        )

    val mainScreenPanelRelXPercent = activeMainScreenPanelConfig
        .map { (it.relX * 100f).toInt().coerceIn(0, 100) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = (DEFAULT_MAIN_SCREEN_PANEL_REL_X * 100f).toInt()
        )

    val mainScreenPanelRelYPercent = activeMainScreenPanelConfig
        .map { (it.relY * 100f).toInt().coerceIn(0, 100) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = (DEFAULT_MAIN_SCREEN_PANEL_REL_Y * 100f).toInt()
        )

    val mainScreenPanelRelWidthPercent = activeMainScreenPanelConfig
        .map { (it.relWidth * 100f).toInt().coerceIn(8, 100) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = (DEFAULT_MAIN_SCREEN_PANEL_REL_WIDTH * 100f).toInt()
        )

    val mainScreenPanelRelHeightPercent = activeMainScreenPanelConfig
        .map { (it.relHeight * 100f).toInt().coerceIn(8, 100) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = (DEFAULT_MAIN_SCREEN_PANEL_REL_HEIGHT * 100f).toInt()
        )

    val dashboardWidgetsConfig = settingsManager.dashboardWidgetsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList<FloatingDashboardWidgetConfig>()
        )

    val dashboardRows = settingsManager.dashboardRowsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 3
        )

    val dashboardCols = settingsManager.dashboardColsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 4
        )

    val dashboardChart = settingsManager.dashboardChartFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val canDataSaveCount = settingsManager.canDataSaveCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 5
        )

    val fuelTankLiters = settingsManager.fuelTankLitersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 57
        )

    val splitTripTimeMinutes = settingsManager.splitTripTimeMinutesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 5
        )

    init {
        viewModelScope.launch {
            val storedConfigs = settingsManager.floatingDashboardsFlow.first()
            selectedFloatingDashboardIdState.value =
                storedConfigs.firstOrNull()?.id ?: DEFAULT_FLOATING_DASHBOARD_ID
        }
        viewModelScope.launch {
            floatingDashboards.collect { configs ->
                val cur = selectedFloatingDashboardIdState.value
                if (configs.isEmpty()) {
                    selectedFloatingDashboardIdState.value = DEFAULT_FLOATING_DASHBOARD_ID
                    return@collect
                }
                if (configs.none { it.id == cur }) {
                    selectedFloatingDashboardIdState.value = configs.first().id
                }
            }
        }
        viewModelScope.launch {
            settingsManager.dashboardWidgetsFlow.collect { configs ->
                latestDashboardWidgetsConfig = configs
            }
        }
        viewModelScope.launch {
            val storedMain = settingsManager.mainScreenDashboardsFlow.first()
            selectedMainScreenPanelIdState.value =
                storedMain.firstOrNull()?.id ?: DEFAULT_MAIN_SCREEN_PANEL_ID
        }
        viewModelScope.launch {
            mainScreenDashboards.collect { configs ->
                val cur = selectedMainScreenPanelIdState.value
                if (configs.isEmpty()) {
                    selectedMainScreenPanelIdState.value = DEFAULT_MAIN_SCREEN_PANEL_ID
                    return@collect
                }
                if (configs.none { it.id == cur }) {
                    selectedMainScreenPanelIdState.value = configs.first().id
                }
            }
        }
    }

    fun floatingDashboardConfig(panelId: String): StateFlow<FloatingDashboardConfig> {
        val resolvedId = panelId.ifBlank { DEFAULT_FLOATING_DASHBOARD_ID }
        return floatingDashboardConfigStates.getOrPut(resolvedId) {
            floatingDashboards
                .map { configs ->
                    configs.firstOrNull { it.id == resolvedId }
                        ?: fallbackFloatingDashboard(resolvedId)
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = fallbackFloatingDashboard(resolvedId)
                )
        }
    }

    fun mainScreenPanelConfig(panelId: String): StateFlow<MainScreenPanelConfig> {
        val resolvedId = panelId.ifBlank { DEFAULT_MAIN_SCREEN_PANEL_ID }
        return mainScreenPanelConfigStates.getOrPut(resolvedId) {
            mainScreenDashboards
                .map { configs ->
                    configs.firstOrNull { it.id == resolvedId }
                        ?: fallbackMainScreenPanel(resolvedId)
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = fallbackMainScreenPanel(resolvedId)
                )
        }
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
            clickAction = DEFAULT_FLOATING_DASHBOARD_CLICK_ACTION,
            showTboxDisconnectIndicator = DEFAULT_FLOATING_DASHBOARD_SHOW_TBOX_DISCONNECT_INDICATOR
        )
    }

    private fun fallbackFloatingDashboard(id: String): FloatingDashboardConfig {
        return createDefaultFloatingDashboard(id, id)
    }

    private suspend fun applyMainScreenPanelUpdate(
        panelId: String,
        update: (MainScreenPanelConfig) -> MainScreenPanelConfig
    ) {
        val resolvedId = panelId.ifBlank { DEFAULT_MAIN_SCREEN_PANEL_ID }
        val updatedConfigs = mainScreenDashboards.value.toMutableList()
        val index = updatedConfigs.indexOfFirst { it.id == resolvedId }
        if (index < 0) return
        val updated = update(updatedConfigs[index])
        updatedConfigs[index] = updated
        settingsManager.saveMainScreenDashboards(updatedConfigs)
    }

    private fun updateMainScreenPanel(
        panelId: String,
        update: (MainScreenPanelConfig) -> MainScreenPanelConfig
    ) {
        viewModelScope.launch {
            applyMainScreenPanelUpdate(panelId, update)
        }
    }

    private fun updateSelectedMainScreenPanel(
        update: (MainScreenPanelConfig) -> MainScreenPanelConfig
    ) {
        viewModelScope.launch {
            val configs = mainScreenDashboards.value
            if (configs.isEmpty()) return@launch
            val panelId = activeMainScreenPanelId.value
            if (configs.none { it.id == panelId }) return@launch
            applyMainScreenPanelUpdate(panelId, update)
        }
    }

    private suspend fun applyFloatingDashboardUpdate(
        panelId: String,
        update: (FloatingDashboardConfig) -> FloatingDashboardConfig
    ) {
        val resolvedId = panelId.ifBlank { DEFAULT_FLOATING_DASHBOARD_ID }
        val updatedConfigs = floatingDashboards.value.toMutableList()
        val index = updatedConfigs.indexOfFirst { it.id == resolvedId }
        if (index < 0) return
        val updated = update(updatedConfigs[index])
        updatedConfigs[index] = updated
        settingsManager.saveFloatingDashboards(updatedConfigs)
    }

    private fun updateFloatingDashboard(
        panelId: String,
        update: (FloatingDashboardConfig) -> FloatingDashboardConfig
    ) {
        viewModelScope.launch {
            applyFloatingDashboardUpdate(panelId, update)
        }
    }

    private fun updateSelectedFloatingDashboard(
        update: (FloatingDashboardConfig) -> FloatingDashboardConfig
    ) {
        viewModelScope.launch {
            val configs = floatingDashboards.value
            if (configs.isEmpty()) return@launch
            val panelId = activeFloatingDashboardId.value
            if (configs.none { it.id == panelId }) return@launch
            applyFloatingDashboardUpdate(panelId, update)
        }
    }

    fun saveAutoRestartSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveAutoModemRestartSetting(enabled)
        }
    }

    fun saveAutoTboxRebootSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveAutoTboxRebootSetting(enabled)
        }
    }

    fun saveWidgetShowIndicatorSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveWidgetShowIndicatorSetting(enabled)
        }
    }

    fun saveWidgetShowLocIndicatorSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveWidgetShowLocIndicatorSetting(enabled)
        }
    }

    fun saveMockLocationSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveMockLocationSetting(enabled)
        }
    }

    fun saveAutoSuspendTboxAppSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveAutoSuspendTboxAppSetting(enabled)
        }
    }

    fun saveAutoStopTboxAppSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveAutoStopTboxAppSetting(enabled)
        }
    }

    fun saveAutoSuspendTboxMdcSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveAutoSuspendTboxMdcSetting(enabled)
        }
    }

    fun saveAutoStopTboxMdcSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveAutoStopTboxMdcSetting(enabled)
        }
    }

    fun saveAutoSuspendTboxSwdSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveAutoSuspendTboxSwdSetting(enabled)
        }
    }

    fun saveAutoPreventTboxRestartSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveAutoPreventTboxRestartSetting(enabled)
        }
    }

    fun saveLogLevel(level: String) {
        viewModelScope.launch {
            settingsManager.saveLogLevel(level)
        }
    }

    fun saveGetVoltagesSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveGetVoltagesSetting(enabled)
        }
    }

    fun saveGetCanFrameSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveGetCanFrameSetting(enabled)
        }
    }

    fun saveGetCycleSignalSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveGetCycleSignalSetting(enabled)
        }
    }

    fun saveGetLocDataSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveGetLocDataSetting(enabled)
        }
    }

    fun saveExpertModeSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveExpertModeSetting(enabled)
        }
    }

    fun saveLeftMenuVisibleSetting(visible: Boolean) {
        viewModelScope.launch {
            settingsManager.saveLeftMenuVisibleSetting(visible)
        }
    }

    fun saveSelectedFloatingDashboardId(panelId: String) {
        val resolvedId = panelId.ifBlank { DEFAULT_FLOATING_DASHBOARD_ID }
        selectedFloatingDashboardIdState.value = resolvedId
    }

    fun onSettingsTabSelected() {
        val list = floatingDashboards.value
        if (list.isEmpty()) return
        selectedFloatingDashboardIdState.value = list.first().id
    }

    fun saveSelectedMainScreenPanelId(panelId: String) {
        val resolvedId = panelId.ifBlank { DEFAULT_MAIN_SCREEN_PANEL_ID }
        selectedMainScreenPanelIdState.value = resolvedId
    }

    fun onMainScreenSettingsTabSelected() {
        val list = mainScreenDashboards.value
        if (list.isEmpty()) return
        selectedMainScreenPanelIdState.value = list.first().id
    }

    fun saveSelectedTab(tabIndex: Int) {
        viewModelScope.launch {
            settingsManager.saveSelectedTab(tabIndex)
        }
    }

    fun saveMainScreenSettingsButton(position: MainScreenSettingsButtonPosition) {
        viewModelScope.launch {
            settingsManager.saveMainScreenSettingsButton(position)
        }
    }

    fun saveMainScreenAddButton(position: MainScreenAddButtonPosition) {
        viewModelScope.launch {
            settingsManager.saveMainScreenAddButton(position)
        }
    }

    fun saveMainScreenOpenOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveMainScreenOpenOnBoot(enabled)
        }
    }

    fun setMainScreenWallpaperLight(sourceUri: Uri?) {
        viewModelScope.launch {
            settingsManager.setMainScreenWallpaperLight(sourceUri)
            _mainScreenWallpaperEpoch.value = _mainScreenWallpaperEpoch.value + 1L
        }
    }

    fun setMainScreenWallpaperDark(sourceUri: Uri?) {
        viewModelScope.launch {
            settingsManager.setMainScreenWallpaperDark(sourceUri)
            _mainScreenWallpaperEpoch.value = _mainScreenWallpaperEpoch.value + 1L
        }
    }

    fun saveMainScreenWallpaperCrop(crop: Boolean) {
        viewModelScope.launch {
            settingsManager.saveMainScreenWallpaperCrop(crop)
        }
    }

    fun saveMainScreenDashboardWidgets(
        panelId: String,
        config: List<FloatingDashboardWidgetConfig>,
        wholePanelFromWidgetDialog: MainScreenWholePanelFieldsForWidgetDialogSave? = null
    ) {
        viewModelScope.launch {
            applyMainScreenPanelUpdate(panelId) { cur ->
                mergeMainScreenPanelForWidgetDialogSave(cur, config, wholePanelFromWidgetDialog)
            }
        }
    }

    fun saveMainScreenPanelLayout(
        panelId: String,
        relX: Float,
        relY: Float,
        relWidth: Float,
        relHeight: Float
    ) {
        updateMainScreenPanel(panelId) {
            it.copy(
                relX = relX.coerceIn(0f, 1f),
                relY = relY.coerceIn(0f, 1f),
                relWidth = relWidth.coerceIn(0.08f, 1f),
                relHeight = relHeight.coerceIn(0.08f, 1f)
            )
        }
    }

    fun addMainScreenDashboard(defaultName: String) {
        viewModelScope.launch {
            val base = mainScreenDashboards.value.toMutableList()
            val newId = "main-screen-" + java.util.UUID.randomUUID().toString().take(8)
            val newPanel = createDefaultMainScreenPanel(newId, defaultName)
            base.add(newPanel)
            settingsManager.saveMainScreenDashboards(base)
            selectedMainScreenPanelIdState.value = newId
        }
    }

    /** Removes a main-screen panel immediately (e.g. from the widget dialog). */
    fun deleteMainScreenDashboard(panelId: String) {
        viewModelScope.launch {
            val remaining = mainScreenDashboards.value.toMutableList()
            val idx = remaining.indexOfFirst { it.id == panelId }
            if (idx < 0) return@launch
            remaining.removeAt(idx)
            settingsManager.saveMainScreenDashboards(remaining)
            if (selectedMainScreenPanelIdState.value == panelId) {
                selectedMainScreenPanelIdState.value =
                    remaining.firstOrNull()?.id ?: DEFAULT_MAIN_SCREEN_PANEL_ID
            }
        }
    }

    /** Same delayed delete as floating panels when removing from settings. */
    fun deleteMainScreenPanelFromSettings(panelId: String) {
        viewModelScope.launch {
            if (_mainScreenPanelDeleteInProgressId.value != null) return@launch
            val base = mainScreenDashboards.value
            val panelConfig = base.firstOrNull { it.id == panelId } ?: return@launch
            val wasAlreadyOff = !panelConfig.enabled
            _mainScreenPanelDeleteInProgressId.value = panelId
            try {
                if (!wasAlreadyOff) {
                    applyMainScreenPanelUpdate(panelId) { it.copy(enabled = false) }
                }
                delay(if (wasAlreadyOff) 1000 else 2000)
                val remaining = mainScreenDashboards.value.toMutableList()
                val idx = remaining.indexOfFirst { it.id == panelId }
                if (idx < 0) return@launch
                remaining.removeAt(idx)
                settingsManager.saveMainScreenDashboards(remaining)
                if (selectedMainScreenPanelIdState.value == panelId) {
                    selectedMainScreenPanelIdState.value =
                        remaining.firstOrNull()?.id ?: DEFAULT_MAIN_SCREEN_PANEL_ID
                }
            } finally {
                _mainScreenPanelDeleteInProgressId.value = null
            }
        }
    }

    fun saveMainScreenPanelSetting(enabled: Boolean) {
        updateSelectedMainScreenPanel { it.copy(enabled = enabled) }
    }

    fun saveMainScreenPanelClickAction(enabled: Boolean, panelId: String? = null) {
        val update: (MainScreenPanelConfig) -> MainScreenPanelConfig =
            { it.copy(clickAction = enabled) }
        if (panelId != null) {
            updateMainScreenPanel(panelId, update)
        } else {
            updateSelectedMainScreenPanel(update)
        }
    }

    fun saveMainScreenPanelShowTboxDisconnectIndicator(
        enabled: Boolean,
        panelId: String? = null
    ) {
        val update: (MainScreenPanelConfig) -> MainScreenPanelConfig =
            { it.copy(showTboxDisconnectIndicator = enabled) }
        if (panelId != null) {
            updateMainScreenPanel(panelId, update)
        } else {
            updateSelectedMainScreenPanel(update)
        }
    }

    fun saveMainScreenPanelRows(rows: Int, panelId: String? = null) {
        if (rows !in 1..6) return
        val update: (MainScreenPanelConfig) -> MainScreenPanelConfig =
            { it.copy(rows = rows) }
        if (panelId != null) {
            updateMainScreenPanel(panelId, update)
        } else {
            updateSelectedMainScreenPanel(update)
        }
    }

    fun saveMainScreenPanelCols(cols: Int, panelId: String? = null) {
        if (cols !in 1..6) return
        val update: (MainScreenPanelConfig) -> MainScreenPanelConfig =
            { it.copy(cols = cols) }
        if (panelId != null) {
            updateMainScreenPanel(panelId, update)
        } else {
            updateSelectedMainScreenPanel(update)
        }
    }

    fun saveMainScreenPanelRelXPercent(percent: Int) {
        updateSelectedMainScreenPanel {
            it.copy(relX = (percent.coerceIn(0, 100)) / 100f)
        }
    }

    fun saveMainScreenPanelRelYPercent(percent: Int) {
        updateSelectedMainScreenPanel {
            it.copy(relY = (percent.coerceIn(0, 100)) / 100f)
        }
    }

    fun saveMainScreenPanelRelWidthPercent(percent: Int) {
        updateSelectedMainScreenPanel {
            it.copy(relWidth = (percent.coerceIn(8, 100)) / 100f)
        }
    }

    fun saveMainScreenPanelRelHeightPercent(percent: Int) {
        updateSelectedMainScreenPanel {
            it.copy(relHeight = (percent.coerceIn(8, 100)) / 100f)
        }
    }

    fun saveMainScreenPanelName(panelId: String, name: String) {
        updateMainScreenPanel(panelId) { it.copy(name = name) }
    }

    fun saveFloatingDashboardSetting(enabled: Boolean) {
        updateSelectedFloatingDashboard { it.copy(enabled = enabled) }
    }

    fun saveFloatingDashboardWidgets(
        panelId: String,
        config: List<FloatingDashboardWidgetConfig>,
        wholePanelFromWidgetDialog: FloatingWholePanelFieldsForWidgetDialogSave? = null
    ) {
        viewModelScope.launch {
            applyFloatingDashboardUpdate(panelId) { cur ->
                mergeFloatingDashboardForWidgetDialogSave(cur, config, wholePanelFromWidgetDialog)
            }
        }
    }

    fun saveFloatingDashboardRows(rows: Int, panelId: String? = null) {
        if (rows !in 1..6) return
        val update: (FloatingDashboardConfig) -> FloatingDashboardConfig =
            { it.copy(rows = rows) }
        if (panelId != null) {
            updateFloatingDashboard(panelId, update)
        } else {
            updateSelectedFloatingDashboard(update)
        }
    }

    fun saveFloatingDashboardCols(cols: Int, panelId: String? = null) {
        if (cols !in 1..6) return
        val update: (FloatingDashboardConfig) -> FloatingDashboardConfig =
            { it.copy(cols = cols) }
        if (panelId != null) {
            updateFloatingDashboard(panelId, update)
        } else {
            updateSelectedFloatingDashboard(update)
        }
    }

    fun saveFloatingDashboardWidth(width: Int) {
        updateSelectedFloatingDashboard { it.copy(width = width) }
    }

    fun saveFloatingDashboardHeight(height: Int) {
        updateSelectedFloatingDashboard { it.copy(height = height) }
    }

    fun saveFloatingDashboardSize(panelId: String, width: Int, height: Int) {
        updateFloatingDashboard(panelId) { it.copy(width = width, height = height) }
    }

    fun saveFloatingDashboardStartX(x: Int) {
        updateSelectedFloatingDashboard { it.copy(startX = x) }
    }

    fun saveFloatingDashboardStartY(y: Int) {
        updateSelectedFloatingDashboard { it.copy(startY = y) }
    }

    fun saveFloatingDashboardPosition(panelId: String, x: Int, y: Int) {
        updateFloatingDashboard(panelId) { it.copy(startX = x, startY = y) }
    }

    fun saveFloatingDashboardClickAction(enabled: Boolean, panelId: String? = null) {
        val update: (FloatingDashboardConfig) -> FloatingDashboardConfig =
            { it.copy(clickAction = enabled) }
        if (panelId != null) {
            updateFloatingDashboard(panelId, update)
        } else {
            updateSelectedFloatingDashboard(update)
        }
    }

    fun saveFloatingDashboardShowTboxDisconnectIndicator(
        enabled: Boolean,
        panelId: String? = null
    ) {
        val update: (FloatingDashboardConfig) -> FloatingDashboardConfig =
            { it.copy(showTboxDisconnectIndicator = enabled) }
        if (panelId != null) {
            updateFloatingDashboard(panelId, update)
        } else {
            updateSelectedFloatingDashboard(update)
        }
    }

    fun saveFloatingDashboardName(panelId: String, name: String) {
        updateFloatingDashboard(panelId) { it.copy(name = name) }
    }

    fun addFloatingDashboard(defaultName: String) {
        viewModelScope.launch {
            val base = floatingDashboards.value.toMutableList()
            val newId = "floating-" + java.util.UUID.randomUUID().toString().take(8)
            val newPanel = createDefaultFloatingDashboard(newId, defaultName)
            base.add(newPanel)
            settingsManager.saveFloatingDashboards(base)
            selectedFloatingDashboardIdState.value = newId
        }
    }

    fun deleteFloatingDashboard(panelId: String) {
        viewModelScope.launch {
            if (_floatingPanelDeleteInProgressId.value != null) return@launch
            val base = floatingDashboards.value
            val panelConfig = base.firstOrNull { it.id == panelId } ?: return@launch
            val wasAlreadyOff = !panelConfig.enabled
            _floatingPanelDeleteInProgressId.value = panelId
            try {
                if (!wasAlreadyOff) {
                    applyFloatingDashboardUpdate(panelId) { it.copy(enabled = false) }
                }
                delay(if (wasAlreadyOff) 1000 else 2000)
                val remaining = floatingDashboards.value.toMutableList()
                val idx = remaining.indexOfFirst { it.id == panelId }
                if (idx < 0) return@launch
                remaining.removeAt(idx)
                settingsManager.saveFloatingDashboards(remaining)
                if (selectedFloatingDashboardIdState.value == panelId) {
                    selectedFloatingDashboardIdState.value =
                        remaining.firstOrNull()?.id ?: DEFAULT_FLOATING_DASHBOARD_ID
                }
            } finally {
                _floatingPanelDeleteInProgressId.value = null
            }
        }
    }

    fun saveFloatingDashboards(configs: List<FloatingDashboardConfig>) {
        viewModelScope.launch {
            settingsManager.saveFloatingDashboards(configs)
        }
    }

    fun saveDashboardWidgets(config: List<FloatingDashboardWidgetConfig>) {
        latestDashboardWidgetsConfig = config
        viewModelScope.launch {
            settingsManager.saveDashboardWidgets(config)
        }
    }

    fun saveDashboardMediaSelectedPlayer(
        widgetIndex: Int,
        widgetCount: Int,
        selectedPackage: String
    ) {
        val normalizedConfigs = normalizeWidgetConfigs(
            configs = latestDashboardWidgetsConfig,
            widgetCount = widgetCount
        ).toMutableList()
        val currentConfig = normalizedConfigs.getOrNull(widgetIndex) ?: return
        if (currentConfig.dataKey != MUSIC_WIDGET_DATA_KEY) return
        if (currentConfig.mediaSelectedPlayer == selectedPackage) return

        normalizedConfigs[widgetIndex] = currentConfig.copy(
            mediaSelectedPlayer = selectedPackage
        )
        saveDashboardWidgets(normalizedConfigs)
    }

    fun saveDashboardRows(config: Int) {
        if (config in 1..6) {
            viewModelScope.launch {
                settingsManager.saveDashboardRows(config)
            }
        }
    }

    fun saveDashboardCols(config: Int) {
        if (config in 1..6) {
            viewModelScope.launch {
                settingsManager.saveDashboardCols(config)
            }
        }
    }

    fun saveDashboardChart(config: Boolean) {
        viewModelScope.launch {
            settingsManager.saveDashboardChart(config)
        }
    }

    fun saveCanDataSaveCount(config: Int) {
        viewModelScope.launch {
            settingsManager.saveCanDataSaveCount(config)
        }
    }

    fun saveFuelTankLiters(liters: Int) {
        viewModelScope.launch {
            settingsManager.saveFuelTankLiters(liters)
        }
    }

    fun saveSplitTripTimeMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsManager.saveSplitTripTimeMinutes(minutes)
        }
    }
}

class SettingsViewModelFactory(private val settingsManager: SettingsManager) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsManager) as T
        }
        throw IllegalArgumentException("Unknown Settings ViewModel class")
    }
}