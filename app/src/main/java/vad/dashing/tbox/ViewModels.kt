package vad.dashing.tbox

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    val canFrameTime: StateFlow<Date?> = TboxRepository.canFrameTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val floatingDashboardShownIds: StateFlow<Set<String>> = TboxRepository.floatingDashboardShownIds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val floatingDashboardShown: StateFlow<Boolean> = floatingDashboardShownIds
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun updateFloatingDashboardShown(panelId: String, isShown: Boolean) {
        viewModelScope.launch {
            TboxRepository.updateFloatingDashboardShown(panelId, isShown)
        }
    }
}

class MainDashboardViewModel : ViewModel() {
    val dashboardManager = DashboardManager("main")

    // Дополнительные методы если нужно
}

class FloatingDashboardViewModel(private val dashboardId: String) : ViewModel() {
    val dashboardManager = DashboardManager(dashboardId)

    // Дополнительные методы если нужно
}

class FloatingDashboardViewModelFactory(
    private val dashboardId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FloatingDashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FloatingDashboardViewModel(dashboardId) as T
        }
        throw IllegalArgumentException("Unknown FloatingDashboard ViewModel class")
    }
}

class DashboardManager(
    private val dashboardId: String
) {
    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    private val _widgetHistory = MutableStateFlow<Map<Int, List<Float>>>(emptyMap())

    private val historyFlows = mutableMapOf<Int, StateFlow<List<Float>>>()

    fun updateWidgets(widgets: List<DashboardWidget>) {
        _dashboardState.update { currentState ->
            currentState.copy(widgets = widgets)
        }
    }

    fun getWidgetHistoryFlow(widgetId: Int): StateFlow<List<Float>> {
        return historyFlows.getOrPut(widgetId) {
            _widgetHistory
                .map { it[widgetId] ?: emptyList() }
                .stateIn(
                    scope = CoroutineScope(Dispatchers.Default),
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
        _widgetHistory.update { currentMap ->
            currentMap - widgetId
        }
    }
}

object WidgetsRepository {
    // Только статические данные - заголовки и единицы измерения
    private data class DataTitle(val title: String, val unit: String)

    private val dataKeyTitles = mapOf(
        "voltage" to DataTitle("Напряжение", "В"),
        "steerAngle" to DataTitle("Угол поворота руля", "°"),
        "steerSpeed" to DataTitle("Скорость вращения руля", ""),
        "engineRPM" to DataTitle("Обороты двигателя", "об/мин"),
        "param1" to DataTitle("Параметр 1", ""),
        "carSpeed" to DataTitle("Скорость автомобиля", "км/ч"),
        "carSpeedAccurate" to DataTitle("Точная скорость автомобиля", "км/ч"),
        "wheel1Speed" to DataTitle("Скорость колеса 1", "км/ч"),
        "wheel2Speed" to DataTitle("Скорость колеса 2", "км/ч"),
        "wheel3Speed" to DataTitle("Скорость колеса 3", "км/ч"),
        "wheel4Speed" to DataTitle("Скорость колеса 4", "км/ч"),
        "wheel1Pressure" to DataTitle("Давление колеса ПЛ", "бар"),
        "wheel2Pressure" to DataTitle("Давление колеса ПП", "бар"),
        "wheel3Pressure" to DataTitle("Давление колеса ЗЛ", "бар"),
        "wheel4Pressure" to DataTitle("Давление колеса ЗП", "бар"),
        "wheel1Temperature" to DataTitle("Температура колеса ПЛ", "°C"),
        "wheel2Temperature" to DataTitle("Температура колеса ПП", "°C"),
        "wheel3Temperature" to DataTitle("Температура колеса ЗЛ", "°C"),
        "wheel4Temperature" to DataTitle("Температура колеса ЗП", "°C"),
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
        "outsideTemperature" to DataTitle("Температура на улице", "°C"),
        "insideTemperature" to DataTitle("Температура в машине", "°C"),
        "isWindowsBlocked" to DataTitle("Блокировка окон", ""),
        "motorHours" to DataTitle("Моточасы двигателя", "ч"),
        "netWidget" to DataTitle("Виджет сигнала сети", ""),
        "locWidget" to DataTitle("Виджет навигации", ""),
        "voltage+engineTemperatureWidget" to DataTitle("Виджет напряжения и температуры двигателя", ""),
        "gearBoxWidget" to DataTitle("Виджет режима КПП с текущей передачей и температурой", ""),
        "wheelsPressureWidget" to DataTitle("Виджет давления в шинах", ""),
        "wheelsPressureTemperatureWidget" to DataTitle("Виджет давления и температуры в шинах", ""),
        "tempInOutWidget" to DataTitle("Виджет температуры снаружи и внутри", ""),
        "restartTbox" to DataTitle("Кнопка перезагрузки TBox", ""),
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

fun valueToString(
    value: Any?,
    accuracy: Int = 1,
    booleanTrue: String = "да",
    booleanFalse: String = "нет",
    default: String = ""
): String {
    if (value == null) {
        return default
    }
    return when (value) {
        is Int -> value.toString()
        is UInt -> value.toString()
        is Float, is Double -> when (accuracy) {
            0 -> String.format(Locale.getDefault(), "%.0f", value)
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