package vad.dashing.tbox

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

class SettingsViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    companion object {
        private const val DEFAULT_LOG_LEVEL = "DEBUG"
        private const val DEFAULT_TBOX_IP = "192.168.225.1"
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
        private const val DEFAULT_FLOATING_DASHBOARD_HIDE_ON_KEYBOARD = false
        private val DEFAULT_FLOATING_DASHBOARD_WIDGETS = emptyList<FloatingDashboardWidgetConfig>()
    }

    private val floatingDashboardConfigStates =
        mutableMapOf<String, StateFlow<FloatingDashboardConfig>>()
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

    val isFloatingDashboardBackground = activeFloatingDashboardConfig
        .map { it.background }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_FLOATING_DASHBOARD_BACKGROUND
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

    val tboxIP = settingsManager.tboxIPFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_TBOX_IP
        )

    val tboxIPRotation = settingsManager.tboxIPRotationFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
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

    fun saveTboxIPRotationSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveTboxIPRotationSetting(enabled)
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

    fun saveFloatingDashboardSetting(enabled: Boolean) {
        updateSelectedFloatingDashboard { it.copy(enabled = enabled) }
    }

    fun saveFloatingDashboardSetting(panelId: String, enabled: Boolean) {
        updateFloatingDashboard(panelId) { it.copy(enabled = enabled) }
    }

    fun saveFloatingDashboardWidgets(config: List<FloatingDashboardWidgetConfig>) {
        updateSelectedFloatingDashboard { it.copy(widgetsConfig = config) }
    }

    fun saveFloatingDashboardWidgets(
        panelId: String,
        config: List<FloatingDashboardWidgetConfig>
    ) {
        updateFloatingDashboard(panelId) { it.copy(widgetsConfig = config) }
    }

    fun saveFloatingDashboardRows(rows: Int) {
        if (rows in 1..6) {
            updateSelectedFloatingDashboard { it.copy(rows = rows) }
        }
    }

    fun saveFloatingDashboardRows(panelId: String, rows: Int) {
        if (rows in 1..6) {
            updateFloatingDashboard(panelId) { it.copy(rows = rows) }
        }
    }

    fun saveFloatingDashboardCols(cols: Int) {
        if (cols in 1..6) {
            updateSelectedFloatingDashboard { it.copy(cols = cols) }
        }
    }

    fun saveFloatingDashboardCols(panelId: String, cols: Int) {
        if (cols in 1..6) {
            updateFloatingDashboard(panelId) { it.copy(cols = cols) }
        }
    }

    fun saveFloatingDashboardWidth(width: Int) {
        updateSelectedFloatingDashboard { it.copy(width = width) }
    }

    fun saveFloatingDashboardWidth(panelId: String, width: Int) {
        updateFloatingDashboard(panelId) { it.copy(width = width) }
    }

    fun saveFloatingDashboardHeight(height: Int) {
        updateSelectedFloatingDashboard { it.copy(height = height) }
    }

    fun saveFloatingDashboardHeight(panelId: String, height: Int) {
        updateFloatingDashboard(panelId) { it.copy(height = height) }
    }

    fun saveFloatingDashboardSize(width: Int, height: Int) {
        updateSelectedFloatingDashboard { it.copy(width = width, height = height) }
    }

    fun saveFloatingDashboardSize(panelId: String, width: Int, height: Int) {
        updateFloatingDashboard(panelId) { it.copy(width = width, height = height) }
    }

    fun saveFloatingDashboardStartX(x: Int) {
        updateSelectedFloatingDashboard { it.copy(startX = x) }
    }

    fun saveFloatingDashboardStartX(panelId: String, x: Int) {
        updateFloatingDashboard(panelId) { it.copy(startX = x) }
    }

    fun saveFloatingDashboardStartY(y: Int) {
        updateSelectedFloatingDashboard { it.copy(startY = y) }
    }

    fun saveFloatingDashboardStartY(panelId: String, y: Int) {
        updateFloatingDashboard(panelId) { it.copy(startY = y) }
    }

    fun saveFloatingDashboardPosition(x: Int, y: Int) {
        updateSelectedFloatingDashboard { it.copy(startX = x, startY = y) }
    }

    fun saveFloatingDashboardPosition(panelId: String, x: Int, y: Int) {
        updateFloatingDashboard(panelId) { it.copy(startX = x, startY = y) }
    }

    fun saveFloatingDashboardBackground(enabled: Boolean) {
        updateSelectedFloatingDashboard { it.copy(background = enabled) }
    }

    fun saveFloatingDashboardBackground(panelId: String, enabled: Boolean) {
        updateFloatingDashboard(panelId) { it.copy(background = enabled) }
    }

    fun saveFloatingDashboardClickAction(enabled: Boolean) {
        updateSelectedFloatingDashboard { it.copy(clickAction = enabled) }
    }

    fun saveFloatingDashboardClickAction(panelId: String, enabled: Boolean) {
        updateFloatingDashboard(panelId) { it.copy(clickAction = enabled) }
    }

    fun saveFloatingDashboardShowTboxDisconnectIndicator(enabled: Boolean) {
        updateSelectedFloatingDashboard { it.copy(showTboxDisconnectIndicator = enabled) }
    }

    fun saveFloatingDashboardShowTboxDisconnectIndicator(panelId: String, enabled: Boolean) {
        updateFloatingDashboard(panelId) { it.copy(showTboxDisconnectIndicator = enabled) }
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