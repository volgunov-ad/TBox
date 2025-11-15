package com.dashing.tbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

class TboxViewModel : ViewModel() {
    val logs: StateFlow<List<String>> = TboxRepository.logs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /*val didDataCSV: StateFlow<List<String>> = TboxRepository.didDataCSV
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("")
        )*/

    val ipList: StateFlow<List<String>> = TboxRepository.ipList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /*val canFramesList: StateFlow<List<String>> = TboxRepository.canFramesList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("")
        )*/

    val tboxConnected: StateFlow<Boolean> = TboxRepository.tboxConnected
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val preventRestartSend: StateFlow<Boolean> = TboxRepository.preventRestartSend
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val suspendTboxAppSend: StateFlow<Boolean> = TboxRepository.suspendTboxAppSend
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val tboxConnectionTime: StateFlow<Date> = TboxRepository.tboxConnectionTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Date()
        )

    val serviceStartTime: StateFlow<Date> = TboxRepository.serviceStartTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Date()
        )

    /*val locationSubscribed: StateFlow<Boolean> = TboxRepository.locationSubscribed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )*/

    val modemStatus: StateFlow<Int> = TboxRepository.modemStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val netState: StateFlow<NetState> = TboxRepository.netState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NetState()
        )

    val netValues: StateFlow<NetValues> = TboxRepository.netValues
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NetValues()
        )

    val apn1State: StateFlow<APNState> = TboxRepository.apnState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = APNState()
        )

    val apn2State: StateFlow<APNState> = TboxRepository.apn2State
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = APNState()
        )

    val locValues: StateFlow<LocValues> = TboxRepository.locValues
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LocValues()
        )

    val currentTheme: StateFlow<Int> = TboxRepository.currentTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1
        )

    val voltages: StateFlow<VoltagesState> = TboxRepository.voltages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VoltagesState()
        )

    val hdm: StateFlow<HdmData> = TboxRepository.hdm
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HdmData()
        )

    val odometer: StateFlow<Int?> = TboxRepository.odometer
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val engineRPM: StateFlow<Float?> = TboxRepository.engineRPM
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val carSpeed: StateFlow<Float?> = TboxRepository.carSpeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val voltage: StateFlow<Float?> = TboxRepository.voltage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val fuelLevelPercentage: StateFlow<UInt?> = TboxRepository.fuelLevelPercentage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val cruiseSetSpeed: StateFlow<UInt?> = TboxRepository.cruiseSetSpeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val wheels: StateFlow<Wheels> = TboxRepository.wheels
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Wheels()
        )

    val steerAngle: StateFlow<Float?> = TboxRepository.steerAngle
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val steerSpeed: StateFlow<Int?> = TboxRepository.steerSpeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val climateSetTemperature1: StateFlow<Float?> = TboxRepository.climateSetTemperature1
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val engineTemperature: StateFlow<Float?> = TboxRepository.engineTemperature
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )


    val gearBoxMode: StateFlow<String> = TboxRepository.gearBoxMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val gearBoxCurrentGear: StateFlow<Int?> = TboxRepository.gearBoxCurrentGear
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val gearBoxChangeGear: StateFlow<Boolean?> = TboxRepository.gearBoxChangeGear
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val gearBoxPreparedGear: StateFlow<Int?> = TboxRepository.gearBoxPreparedGear
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val gearBoxOilTemperature: StateFlow<Int?> = TboxRepository.gearBoxOilTemperature
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val gearBoxDriveMode: StateFlow<String> = TboxRepository.gearBoxDriveMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val gearBoxWork: StateFlow<String> = TboxRepository.gearBoxWork
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val canFramesStructured: StateFlow<Map<String, List<CanFrame>>> = TboxRepository.canFramesStructured
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val canFrameTime: StateFlow<Date?> = TboxRepository.canFrameTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}

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
}

class WidgetViewModel : ViewModel() {

    val dashboardState: StateFlow<DashboardState> = WidgetsRepository.dashboardState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardState()
        )

    // Метод для обновления виджетов
    fun updateDashboardWidgets(widgets: List<DashboardWidget>) {
        viewModelScope.launch {
            WidgetsRepository.updateDashboardWidgets(widgets)
        }
    }
}

object WidgetsRepository {
    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    // Соответствие ключей заголовкам и единицам измерения
    private data class DataTitle(val title: String, val unit: String)

    private val dataKeyTitles = mapOf(
        "voltage" to DataTitle("Напряжение", "В"),
        "steerAngle" to DataTitle("Угол поворота руля", "°"),
        "steerSpeed" to DataTitle("Скорость вращения руля", ""),
        "engineRPM" to DataTitle("Обороты двигателя", "об/мин"),
        "carSpeed" to DataTitle("Скорость автомобиля", "км/ч"),
        "cruiseSetSpeed" to DataTitle("Скорость круиз-контроля", "км/ч"),
        "odometer" to DataTitle("Одометр", "км"),
        "fuelLevelPercentage" to DataTitle("Уровень топлива", "%"),
        "engineTemperature" to DataTitle("Температура двигателя", "°C"),
        "gearBoxOilTemperature" to DataTitle("Температура масла КПП", "°C"),
        "gearBoxCurrentGear" to DataTitle("Текущая передача КПП", ""),
        "gearBoxPreparedGear" to DataTitle("Приготовленная передача КПП", ""),
        "gearBoxChangeGear" to DataTitle("Выполнение переключения", ""),
        "gearBoxMode" to DataTitle("Режим КПП", ""),
        "gearBoxDriveMode" to DataTitle("Режим движения КПП", ""),
        "gearBoxWork" to DataTitle("Работа КПП", ""),
        "locateStatus" to DataTitle("Фиксация местоположения", ""),
        "gnssSpeed" to DataTitle("Скорость GNSS", "км/ч"),
        "visibleSatellites" to DataTitle("Видимые спутники", ""),
        "locationUpdateTime" to DataTitle("Время обновления GNSS", ""),
    )

    fun getTitleForDataKey(dataKey: String): String {
        return dataKeyTitles[dataKey]?.title ?: ""
    }

    fun getUnitForDataKey(dataKey: String): String {
        return dataKeyTitles[dataKey]?.unit ?: ""
    }

    fun getTitleUnitForDataKey(dataKey: String): String {
        val unit = getUnitForDataKey(dataKey)
        return if (unit != "") {
            "${getTitleForDataKey(dataKey)}, $unit"
        } else {
            getTitleForDataKey(dataKey)
        }
    }

    fun updateDashboardWidgets(widgets: List<DashboardWidget>) {
        _dashboardState.update { currentState ->
            currentState.copy(widgets = widgets)
        }
    }

    fun getAvailableDataKeys(): List<String> {
        return dataKeyTitles.keys.toList()
    }
}

// Модель для виджета панели
data class DashboardWidget(
    val id: Int,
    val title: String,
    val unit: String = "",
    val dataKey: String = "" // Ключ для идентификации данных
)

// Состояние панели виджетов
data class DashboardState(
    val widgets: List<DashboardWidget> = emptyList(),
    val availableDataKeys: List<String> = emptyList()
)