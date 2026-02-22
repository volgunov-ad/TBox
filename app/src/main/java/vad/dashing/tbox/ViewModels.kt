package vad.dashing.tbox

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
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

    val tboxAppSuspended: StateFlow<Boolean> = TboxRepository.tboxAppSuspended
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

    val tboxMdcSuspended: StateFlow<Boolean> = TboxRepository.tboxMdcSuspended
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val tboxMdcStoped: StateFlow<Boolean> = TboxRepository.tboxMdcStoped
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val tboxSwdSuspended: StateFlow<Boolean> = TboxRepository.tboxSwdSuspended
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val gateVersion: StateFlow<String> = TboxRepository.gateVersion
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
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

    fun updateFloatingDashboardShown(panelId: String, isShown: Boolean) {
        viewModelScope.launch {
            TboxRepository.updateFloatingDashboardShown(panelId, isShown)
        }
    }
}

class MainDashboardViewModel : ViewModel() {
    val dashboardManager = DashboardManager("main", viewModelScope)

    // Дополнительные методы если нужно
}

class FloatingDashboardViewModel(private val dashboardId: String) : ViewModel() {
    val dashboardManager = DashboardManager(dashboardId, viewModelScope)

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
    private val dashboardId: String,
    private val scope: CoroutineScope
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
                    scope = scope,
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
    private data class DataTitle(
        @StringRes val titleRes: Int,
        @StringRes val unitRes: Int? = null
    )

    private val dataKeyTitles = mapOf(
        "param1" to DataTitle(R.string.data_title_param_1),
        "param2" to DataTitle(R.string.data_title_param_2),
        "param3" to DataTitle(R.string.data_title_param_3),
        "param4" to DataTitle(R.string.data_title_param_4),
        "wheel1Speed" to DataTitle(R.string.data_title_wheel_speed_1, R.string.unit_kmh),
        "wheel2Speed" to DataTitle(R.string.data_title_wheel_speed_2, R.string.unit_kmh),
        "wheel3Speed" to DataTitle(R.string.data_title_wheel_speed_3, R.string.unit_kmh),
        "wheel4Speed" to DataTitle(R.string.data_title_wheel_speed_4, R.string.unit_kmh),
        "wheel1Pressure" to DataTitle(R.string.data_title_wheel_pressure_fl, R.string.unit_bar),
        "wheel2Pressure" to DataTitle(R.string.data_title_wheel_pressure_fr, R.string.unit_bar),
        "wheel3Pressure" to DataTitle(R.string.data_title_wheel_pressure_rl, R.string.unit_bar),
        "wheel4Pressure" to DataTitle(R.string.data_title_wheel_pressure_rr, R.string.unit_bar),
        "wheel1Temperature" to DataTitle(R.string.data_title_wheel_temperature_fl, R.string.unit_celsius),
        "wheel2Temperature" to DataTitle(R.string.data_title_wheel_temperature_fr, R.string.unit_celsius),
        "wheel3Temperature" to DataTitle(R.string.data_title_wheel_temperature_rl, R.string.unit_celsius),
        "wheel4Temperature" to DataTitle(R.string.data_title_wheel_temperature_rr, R.string.unit_celsius),
        "frontLeftSeatMode" to DataTitle(R.string.data_title_front_left_seat_mode),
        "frontRightSeatMode" to DataTitle(R.string.data_title_front_right_seat_mode),
        "locateStatus" to DataTitle(R.string.data_title_locate_status),
        "isLocValuesTrue" to DataTitle(R.string.data_title_loc_values_true),
        "locationUpdateTime" to DataTitle(R.string.data_title_location_update_time),
        "locationRefreshTime" to DataTitle(R.string.data_title_location_refresh_time),
        "signalLevel" to DataTitle(R.string.data_title_signal_level),
        "netStatus" to DataTitle(R.string.data_title_net_status),
        "regStatus" to DataTitle(R.string.data_title_reg_status),
        "simStatus" to DataTitle(R.string.data_title_sim_status),
        "isWindowsBlocked" to DataTitle(R.string.data_title_windows_blocked),
    )

    private val dataKeyTitlesWidgets = mapOf(
        "voltage" to DataTitle(R.string.data_title_voltage, R.string.unit_volt),
        "steerAngle" to DataTitle(R.string.data_title_steer_angle, R.string.unit_degree),
        "steerSpeed" to DataTitle(R.string.data_title_steer_speed),
        "engineRPM" to DataTitle(R.string.data_title_engine_rpm, R.string.unit_rpm),
        "carSpeed" to DataTitle(R.string.data_title_car_speed, R.string.unit_kmh),
        "carSpeedAccurate" to DataTitle(R.string.data_title_car_speed_accurate, R.string.unit_kmh),
        "cruiseSetSpeed" to DataTitle(R.string.data_title_cruise_set_speed, R.string.unit_kmh),
        "odometer" to DataTitle(R.string.data_title_odometer, R.string.unit_km),
        "distanceToNextMaintenance" to DataTitle(
            R.string.data_title_distance_to_next_maintenance,
            R.string.unit_km
        ),
        "distanceToFuelEmpty" to DataTitle(R.string.data_title_distance_to_fuel_empty, R.string.unit_km),
        "fuelLevelPercentage" to DataTitle(R.string.data_title_fuel_level_percentage, R.string.unit_percent),
        "fuelLevelPercentageFiltered" to DataTitle(
            R.string.data_title_fuel_level_percentage_filtered,
            R.string.unit_percent
        ),
        "breakingForce" to DataTitle(R.string.data_title_breaking_force),
        "engineTemperature" to DataTitle(R.string.data_title_engine_temperature, R.string.unit_celsius),
        "gearBoxOilTemperature" to DataTitle(R.string.data_title_gearbox_oil_temperature, R.string.unit_celsius),
        "gearBoxCurrentGear" to DataTitle(R.string.data_title_gearbox_current_gear),
        "gearBoxPreparedGear" to DataTitle(R.string.data_title_gearbox_prepared_gear),
        "gearBoxChangeGear" to DataTitle(R.string.data_title_gearbox_change_gear),
        "gearBoxMode" to DataTitle(R.string.data_title_gearbox_mode),
        "gearBoxDriveMode" to DataTitle(R.string.data_title_gearbox_drive_mode),
        "gearBoxWork" to DataTitle(R.string.data_title_gearbox_work),
        "gnssSpeed" to DataTitle(R.string.data_title_gnss_speed, R.string.unit_kmh),
        "visibleSatellites" to DataTitle(R.string.data_title_visible_satellites),
        "longitude" to DataTitle(R.string.data_title_longitude, R.string.unit_degree),
        "latitude" to DataTitle(R.string.data_title_latitude, R.string.unit_degree),
        "altitude" to DataTitle(R.string.data_title_altitude, R.string.unit_meter),
        "trueDirection" to DataTitle(R.string.data_title_true_direction),
        "outsideTemperature" to DataTitle(R.string.data_title_outside_temperature, R.string.unit_celsius),
        "insideTemperature" to DataTitle(R.string.data_title_inside_temperature, R.string.unit_celsius),
        "outsideAirQuality" to DataTitle(R.string.data_title_outside_air_quality),
        "insideAirQuality" to DataTitle(R.string.data_title_inside_air_quality),
        "motorHours" to DataTitle(R.string.data_title_motor_hours, R.string.unit_hours),
        "motorHoursTrip" to DataTitle(R.string.data_title_motor_hours_trip, R.string.unit_hours),
        "motorHoursWidget" to DataTitle(R.string.data_title_motor_hours_widget),
        "netWidget" to DataTitle(R.string.data_title_net_widget),
        "locWidget" to DataTitle(R.string.data_title_loc_widget),
        "voltage+engineTemperatureWidget" to DataTitle(R.string.data_title_voltage_engine_temperature_widget),
        "gearBoxWidget" to DataTitle(R.string.data_title_gearbox_widget),
        "wheelsPressureWidget" to DataTitle(R.string.data_title_wheels_pressure_widget, R.string.unit_bar),
        "wheelsPressureTemperatureWidget" to DataTitle(
            R.string.data_title_wheels_pressure_temperature_widget,
            R.string.unit_bar_celsius
        ),
        "tempInOutWidget" to DataTitle(R.string.data_title_temp_in_out_widget),
        "restartTbox" to DataTitle(R.string.data_title_restart_tbox),
    )

    private fun getDataTitle(dataKey: String): DataTitle? {
        return (dataKeyTitles + dataKeyTitlesWidgets)[dataKey]
    }

    fun getTitleForDataKey(context: Context, dataKey: String): String {
        val dataTitle = getDataTitle(dataKey) ?: return ""
        return context.getString(dataTitle.titleRes)
    }

    fun getUnitForDataKey(context: Context, dataKey: String): String {
        val dataTitle = getDataTitle(dataKey) ?: return ""
        val unitRes = dataTitle.unitRes ?: return ""
        return context.getString(unitRes)
    }

    fun getTitleUnitForDataKey(context: Context, dataKey: String): String {
        val dataTitle = getDataTitle(dataKey) ?: return ""
        val title = context.getString(dataTitle.titleRes)
        val unitRes = dataTitle.unitRes ?: return title
        val unit = context.getString(unitRes)
        return context.getString(R.string.title_with_unit, title, unit)
    }

    fun getAvailableDataKeys(): List<String> {
        return (dataKeyTitles + dataKeyTitlesWidgets).keys.toList()
    }

    fun getAvailableDataKeysWidgets(): List<String> {
        return dataKeyTitlesWidgets.keys.toList()
    }
}

const val DEFAULT_WIDGET_TEXT_COLOR_LIGHT = LIGHT_THEME_ON_SURFACE_COLOR_INT
const val DEFAULT_WIDGET_TEXT_COLOR_DARK = DARK_THEME_ON_SURFACE_COLOR_INT

// Модель для виджета панели
data class DashboardWidget(
    val id: Int,
    val title: String,
    val unit: String = "",
    val dataKey: String = "", // Ключ для идентификации данных
    val maxValue: Float? = null,
    val minValue: Float? = null,
    val textColorLight: Int = DEFAULT_WIDGET_TEXT_COLOR_LIGHT,
    val textColorDark: Int = DEFAULT_WIDGET_TEXT_COLOR_DARK
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