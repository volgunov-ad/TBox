package vad.dashing.tbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.Boolean

class SettingsViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    companion object {
        private const val DEFAULT_LOG_LEVEL = "DEBUG"
        private const val DEFAULT_TBOX_IP = "192.168.225.1"
    }

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

    val isFloatingDashboardEnabled = settingsManager.floatingDashboardFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val floatingDashboardWidgetsConfig = settingsManager.floatingDashboardWidgetsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val floatingDashboardRows = settingsManager.floatingDashboardRowsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1
        )

    val floatingDashboardCols = settingsManager.floatingDashboardColsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1
        )

    val floatingDashboardHeight = settingsManager.floatingDashboardHeightFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 100
        )

    val floatingDashboardWidth = settingsManager.floatingDashboardWidthFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 100
        )

    val floatingDashboardStartX = settingsManager.floatingDashboardStartXFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 50
        )

    val floatingDashboardStartY = settingsManager.floatingDashboardStartYFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 50
        )

    val isFloatingDashboardBackground = settingsManager.floatingDashboardBackgroundFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isFloatingDashboardClickAction = settingsManager.floatingDashboardClickActionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
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

    fun saveFloatingDashboardSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveFloatingDashboardSetting(enabled)
        }
    }

    fun saveFloatingDashboardWidgets(config: String) {
        viewModelScope.launch {
            settingsManager.saveFloatingDashboardWidgets(config)
        }
    }

    fun saveFloatingDashboardRows(rows: Int) {
        if (rows in 1..6) {
            viewModelScope.launch {
                settingsManager.saveFloatingDashboardRows(rows)
            }
        }
    }

    fun saveFloatingDashboardCols(cols: Int) {
        if (cols in 1..6) {
            viewModelScope.launch {
                settingsManager.saveFloatingDashboardCols(cols)
            }
        }
    }

    fun saveFloatingDashboardWidth(width: Int) {
        viewModelScope.launch {
            settingsManager.saveFloatingDashboardWidth(width)
        }
    }

    fun saveFloatingDashboardHeight(height: Int) {
        viewModelScope.launch {
            settingsManager.saveFloatingDashboardHeight(height)
        }
    }

    fun saveFloatingDashboardStartX(x: Int) {
        viewModelScope.launch {
            settingsManager.saveFloatingDashboardStartX(x)
        }
    }

    fun saveFloatingDashboardStartY(y: Int) {
        viewModelScope.launch {
            settingsManager.saveFloatingDashboardStartY(y)
        }
    }

    fun saveFloatingDashboardBackground(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveFloatingDashboardBackground(enabled)
        }
    }

    fun saveFloatingDashboardClickAction(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveFloatingDashboardClickAction(enabled)
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