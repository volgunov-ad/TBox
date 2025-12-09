package com.dashing.tbox

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import kotlin.Boolean
import kotlin.collections.List

class TboxViewModel : ViewModel() {
    val logs: StateFlow<List<String>> = TboxRepository.logs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val atLogs: StateFlow<List<String>> = TboxRepository.atLogs
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

    val tboxAppStoped: StateFlow<Boolean> = TboxRepository.tboxAppStoped
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

    val apnStatus: StateFlow<Boolean> = TboxRepository.apnStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val locValues: StateFlow<LocValues> = TboxRepository.locValues
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LocValues()
        )

    val locationUpdateTime: StateFlow<Date?> = TboxRepository.locationUpdateTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isLocValuesTrue: StateFlow<Boolean> = TboxRepository.isLocValuesTrue
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
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

    val odometer: StateFlow<UInt?> = TboxRepository.odometer
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val distanceToNextMaintenance: StateFlow<UInt?> = TboxRepository.distanceToNextMaintenance
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val distanceToFuelEmpty: StateFlow<UInt?> = TboxRepository.distanceToFuelEmpty
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val breakingForce: StateFlow<UInt?> = TboxRepository.breakingForce
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

    val carSpeedAccurate: StateFlow<Float?> = TboxRepository.carSpeedAccurate
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

    val fuelLevelPercentageFiltered: StateFlow<UInt?> = TboxRepository.fuelLevelPercentageFiltered
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

    val wheelsSpeed: StateFlow<Wheels> = TboxRepository.wheelsSpeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Wheels()
        )

    val wheelsPressure: StateFlow<Wheels> = TboxRepository.wheelsPressure
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

    val frontLeftSeatMode: StateFlow<UInt?> = TboxRepository.frontLeftSeatMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val frontRightSeatMode: StateFlow<UInt?> = TboxRepository.frontRightSeatMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
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

    private val _widgetHistory = MutableStateFlow<Map<Int, List<Float>>>(emptyMap())
    val widgetHistory: StateFlow<Map<Int, List<Float>>> = _widgetHistory

    // Получение StateFlow для конкретного виджета (с кэшированием)
    private val historyFlows = mutableMapOf<Int, StateFlow<List<Float>>>()

    fun getWidgetHistoryFlow(widgetId: Int): StateFlow<List<Float>> {
        return historyFlows.getOrPut(widgetId) {
            widgetHistory
                .map { it[widgetId] ?: emptyList() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList()
                )
        }
    }

    fun updateWidgetHistory(widgetId: Int, value: Float) {
        _widgetHistory.update { currentMap ->
            val currentHistory = currentMap[widgetId] ?: emptyList()
            val newHistory = (currentHistory + value).takeLast(60)
            currentMap + (widgetId to newHistory)
        }
    }

    fun clearWidgetHistory(widgetId: Int) {
        val currentMap = _widgetHistory.value
        _widgetHistory.value = currentMap - widgetId
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
        "carSpeedAccurate" to DataTitle("Точная скорость автомобиля", "км/ч"),
        "wheel1Speed" to DataTitle("Скорость колеса 1", "км/ч"),
        "wheel2Speed" to DataTitle("Скорость колеса 2", "км/ч"),
        "wheel3Speed" to DataTitle("Скорость колеса 3", "км/ч"),
        "wheel4Speed" to DataTitle("Скорость колеса 4", "км/ч"),
        "wheel1Pressure" to DataTitle("Давление колеса 1", "бар"),
        "wheel2Pressure" to DataTitle("Давление колеса 2", "бар"),
        "wheel3Pressure" to DataTitle("Давление колеса 3", "бар"),
        "wheel4Pressure" to DataTitle("Давление колеса 4", "бар"),
        "cruiseSetSpeed" to DataTitle("Скорость круиз-контроля", "км/ч"),
        "odometer" to DataTitle("Одометр", "км"),
        "distanceToNextMaintenance" to DataTitle("Пробег до следующего ТО", "км"),
        "distanceToFuelEmpty" to DataTitle("Пробег на остатке топлива", "км"),
        "fuelLevelPercentage" to DataTitle("Уровень топлива", "%"),
        "fuelLevelPercentageFiltered" to DataTitle("Уровень топлива (сглажено)", "%"),
        "breakingForce" to DataTitle("Усилие торможения", ""),
        "engineTemperature" to DataTitle("Температура двигателя", "°C"),
        "gearBoxOilTemperature" to DataTitle("Температура масла КПП", "°C"),
        "gearBoxCurrentGear" to DataTitle("Текущая передача КПП", ""),
        "gearBoxPreparedGear" to DataTitle("Приготовленная передача КПП", ""),
        "gearBoxChangeGear" to DataTitle("Выполнение переключения", ""),
        "gearBoxMode" to DataTitle("Режим КПП", ""),
        "gearBoxDriveMode" to DataTitle("Режим движения КПП", ""),
        "gearBoxWork" to DataTitle("Работа КПП", ""),
        "frontLeftSeatMode" to DataTitle("Режим левого переднего сиденья", ""),
        "frontRightSeatMode" to DataTitle("Режим правого переднего сиденья", ""),
        "locateStatus" to DataTitle("Фиксация местоположения", ""),
        "isLocValuesTrue" to DataTitle("Правдивость местоположения", ""),
        "gnssSpeed" to DataTitle("Скорость GNSS", "км/ч"),
        "visibleSatellites" to DataTitle("Видимые спутники", ""),
        "longitude" to DataTitle("Долгота", "°"),
        "latitude" to DataTitle("Широта", "°"),
        "altitude" to DataTitle("Высота", "м"),
        "trueDirection" to DataTitle("Направление", ""),
        "locationUpdateTime" to DataTitle("Время изменения GNSS", ""),
        "locationRefreshTime" to DataTitle("Время получения GNSS", ""),
        "signalLevel" to DataTitle("Уровень сигнала сети", ""),
        "netStatus" to DataTitle("Тип сети", ""),
        "regStatus" to DataTitle("Регистрация в сети", ""),
        "simStatus" to DataTitle("Состояние SIM", ""),
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
    val dataKey: String = "", // Ключ для идентификации данных
    val maxValue: Float? = null,
    val minValue: Float? = null
)

// Состояние панели виджетов
data class DashboardState(
    val widgets: List<DashboardWidget> = emptyList(),
    val availableDataKeys: List<String> = emptyList()
)

fun valueToString(value: Any?, accuracy: Int = 1, booleanTrue: String = "да", booleanFalse: String = "нет"): String {
    if (value == null) {
        return ""
    }
    return when (value) {
        is Int -> value.toString()
        is UInt -> value.toString()
        is Float, is Double -> when (accuracy) {
            1 -> String.format(Locale.getDefault(), "%.1f", value)
            2 -> String.format(Locale.getDefault(), "%.2f", value)
            3 -> String.format(Locale.getDefault(), "%.3f", value)
            4 -> String.format(Locale.getDefault(), "%.4f", value)
            5 -> String.format(Locale.getDefault(), "%.5f", value)
            6 -> String.format(Locale.getDefault(), "%.6f", value)
            else -> String.format(Locale.getDefault(), "%.1f", value)
        }
        is Boolean -> if (value) booleanTrue else booleanFalse
        is String -> value
        else -> ""
    }
}

fun seatModeToString(seatMode: UInt?): String {
    if (seatMode == null) return ""
    return when (seatMode) {
        1u -> {
            "выключено"
        }
        2u -> {
            "обогрев 1"
        }
        3u -> {
            "обогрев 2"
        }
        4u -> {
            "обогрев 3"
        }
        5u -> {
            "вентиляция 1"
        }
        6u -> {
            "вентиляция 2"
        }
        7u -> {
            "вентиляция 3"
        }
        else -> {
            seatMode.toString()
        }
    }
}