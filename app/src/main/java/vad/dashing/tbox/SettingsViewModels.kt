package vad.dashing.tbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.Boolean

class SettingsViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    companion object {
        private const val DEFAULT_LOG_LEVEL = "DEBUG"
        private const val DEFAULT_TBOX_IP = "192.168.225.1"
        private const val DEFAULT_FLOATING_DASHBOARD_ID = "floating-1"
        private const val FLOATING_DASHBOARD_SELECTED_KEY = "floating_dashboard_selected"
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
    }

    private val defaultFloatingDashboards = listOf(
        createDefaultFloatingDashboard("floating-1", "Панель 1"),
        createDefaultFloatingDashboard("floating-2", "Панель 2"),
        createDefaultFloatingDashboard("floating-3", "Панель 3")
    )
    private val floatingDashboardConfigStates =
        mutableMapOf<String, StateFlow<FloatingDashboardConfig>>()

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
        .map { configs -> ensureDefaultFloatingDashboards(configs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = defaultFloatingDashboards
        )

    private val selectedFloatingDashboardId = settingsManager.getStringFlow(
        FLOATING_DASHBOARD_SELECTED_KEY,
        DEFAULT_FLOATING_DASHBOARD_ID
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DEFAULT_FLOATING_DASHBOARD_ID
    )

    val activeFloatingDashboardId = combine(floatingDashboards, selectedFloatingDashboardId) {
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
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val dashboardWidgetsConfig = settingsManager.dashboardWidgetsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
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
            val ensuredConfigs = ensureDefaultFloatingDashboards(storedConfigs)
            if (ensuredConfigs != storedConfigs) {
                settingsManager.saveFloatingDashboards(ensuredConfigs)
            }

            val selectedId = settingsManager.getStringFlow(
                FLOATING_DASHBOARD_SELECTED_KEY,
                DEFAULT_FLOATING_DASHBOARD_ID
            ).first()
            val resolvedId = ensuredConfigs.firstOrNull { it.id == selectedId }?.id
                ?: ensuredConfigs.firstOrNull()?.id
                ?: DEFAULT_FLOATING_DASHBOARD_ID
            if (resolvedId != selectedId) {
                settingsManager.saveCustomString(FLOATING_DASHBOARD_SELECTED_KEY, resolvedId)
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
            clickAction = DEFAULT_FLOATING_DASHBOARD_CLICK_ACTION
        )
    }

    private fun fallbackFloatingDashboard(id: String): FloatingDashboardConfig {
        return defaultFloatingDashboards.firstOrNull { it.id == id }
            ?: createDefaultFloatingDashboard(id, id)
    }

    private fun ensureDefaultFloatingDashboards(
        configs: List<FloatingDashboardConfig>
    ): List<FloatingDashboardConfig> {
        if (configs.isEmpty()) return defaultFloatingDashboards
        val existingIds = configs.map { it.id }.toSet()
        val missing = defaultFloatingDashboards.filter { it.id !in existingIds }
        return if (missing.isEmpty()) configs else configs + missing
    }

    private fun updateFloatingDashboard(
        panelId: String,
        update: (FloatingDashboardConfig) -> FloatingDashboardConfig
    ) {
        val resolvedId = panelId.ifBlank { DEFAULT_FLOATING_DASHBOARD_ID }
        viewModelScope.launch {
            val baseConfigs = ensureDefaultFloatingDashboards(floatingDashboards.value)
            val updatedConfigs = baseConfigs.toMutableList()
            val index = updatedConfigs.indexOfFirst { it.id == resolvedId }
            val current = if (index >= 0) {
                updatedConfigs[index]
            } else {
                fallbackFloatingDashboard(resolvedId)
            }
            val updated = update(current)
            if (index >= 0) {
                updatedConfigs[index] = updated
            } else {
                updatedConfigs.add(updated)
            }
            settingsManager.saveFloatingDashboards(updatedConfigs)
        }
    }

    private fun updateSelectedFloatingDashboard(
        update: (FloatingDashboardConfig) -> FloatingDashboardConfig
    ) {
        val panelId = activeFloatingDashboardId.value
        updateFloatingDashboard(panelId, update)
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
        viewModelScope.launch {
            settingsManager.saveCustomString(FLOATING_DASHBOARD_SELECTED_KEY, resolvedId)
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

    fun saveFloatingDashboards(configs: List<FloatingDashboardConfig>) {
        viewModelScope.launch {
            settingsManager.saveFloatingDashboards(configs)
        }
    }

    fun saveSelectedTab(tabIndex: Int) {
        viewModelScope.launch {
            settingsManager.saveSelectedTab(tabIndex)
        }
    }

    fun saveDashboardWidgets(config: String) {
        viewModelScope.launch {
            settingsManager.saveDashboardWidgets(config)
        }
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