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
import vad.dashing.tbox.ui.theme.DARK_THEME_ON_SURFACE_COLOR_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_ON_SURFACE_COLOR_INT
import vad.dashing.tbox.utils.GEARBOX_MODE_CURRENT_GEAR_DATA_KEY
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

    val floatingDashboardShownIds: StateFlow<Set<String>> = TboxRepository.floatingDashboardShownIds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
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

    val tboxLocSuspended: StateFlow<Boolean> = TboxRepository.tboxLocSuspended
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

    private data class WidgetDescription(
        @StringRes val descriptionRes: Int,
        @StringRes val actionsRes: Int? = null,
    )

    const val EXTERNAL_WIDGET_DATA_KEY = "externalAppWidget"

    private val dataKeyTitles = mapOf(
        "param1" to DataTitle(R.string.data_title_param_1),
        "param2" to DataTitle(R.string.data_title_param_2),
        "param3" to DataTitle(R.string.data_title_param_3),
        "param4" to DataTitle(R.string.data_title_param_4),
        "param5" to DataTitle(R.string.data_title_param_5),
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
        "fuelLevelLiters" to DataTitle(R.string.data_title_fuel_level_liters, R.string.unit_liter),
        "fuelLevelLitersActual" to DataTitle(R.string.data_title_fuel_level_liters_actual, R.string.unit_liter),
        "currentFuelConsumption" to DataTitle(R.string.currentFuelConsumption, R.string.unit_l_100km),
        AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY to DataTitle(
            R.string.averageFuelConsumption,
            R.string.unit_l_100km,
        ),
        "breakingForce" to DataTitle(R.string.data_title_breaking_force),
        GAS_BRAKE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_gas_brake_widget, R.string.unit_percent),
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
        "timeWidget" to DataTitle(R.string.data_title_time_widget),
        "dateWidget" to DataTitle(R.string.data_title_date_widget),
        CPU_USAGE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_cpu_usage, R.string.unit_percent),
        FREE_RAM_PERCENT_WIDGET_DATA_KEY to DataTitle(
            R.string.data_title_free_ram_percent,
            R.string.unit_percent,
        ),
        "activeTripWidget" to DataTitle(R.string.data_title_active_trip_widget),
        "activeTripWidgetSimple" to DataTitle(R.string.data_title_active_trip_widget_simple),
        "activeTripWidgetMini" to DataTitle(R.string.data_title_active_trip_widget_mini),
        ACTIVE_TRIP_WIDGET_CUSTOM_DATA_KEY to DataTitle(R.string.data_title_active_trip_widget_custom),
        "netWidget" to DataTitle(R.string.data_title_net_widget),
        "netWidgetNew" to DataTitle(R.string.data_title_net_widget_new),
        "netWidgetColored" to DataTitle(R.string.data_title_net_widget_colored),
        "locWidget" to DataTitle(R.string.data_title_loc_widget),
        GEOPOSITION_DATA_WIDGET_DATA_KEY to DataTitle(R.string.data_title_geoposition_data_widget),
        ROAD_MATCH_MAP_WIDGET_DATA_KEY to DataTitle(R.string.data_title_road_match_map_widget),
        MOCK_LOCATION_MODE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_mock_location_mode_widget),
        GNSS_DEBUG_WIDGET_DATA_KEY to DataTitle(R.string.data_title_gnss_debug_widget),
        "voltage+engineTemperatureWidget" to DataTitle(R.string.data_title_voltage_engine_temperature_widget),
        "gearBoxWidget" to DataTitle(R.string.data_title_gearbox_widget),
        GEARBOX_MODE_CURRENT_GEAR_DATA_KEY to DataTitle(R.string.data_title_gearbox_mode_current_gear),
        DRIVE_MODE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_drive_mode_widget),
        DRIVE_MODE_CYCLE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_drive_mode_cycle_widget),
        "wheel1Pressure" to DataTitle(R.string.data_title_wheel_pressure_fl, R.string.unit_bar),
        "wheel2Pressure" to DataTitle(R.string.data_title_wheel_pressure_fr, R.string.unit_bar),
        "wheel3Pressure" to DataTitle(R.string.data_title_wheel_pressure_rl, R.string.unit_bar),
        "wheel4Pressure" to DataTitle(R.string.data_title_wheel_pressure_rr, R.string.unit_bar),
        "wheel1Temperature" to DataTitle(R.string.data_title_wheel_temperature_fl, R.string.unit_celsius),
        "wheel2Temperature" to DataTitle(R.string.data_title_wheel_temperature_fr, R.string.unit_celsius),
        "wheel3Temperature" to DataTitle(R.string.data_title_wheel_temperature_rl, R.string.unit_celsius),
        "wheel4Temperature" to DataTitle(R.string.data_title_wheel_temperature_rr, R.string.unit_celsius),
        "wheelsPressureWidget" to DataTitle(R.string.data_title_wheels_pressure_widget, R.string.unit_bar),
        "wheelsPressureTemperatureWidget" to DataTitle(
            R.string.data_title_wheels_pressure_temperature_widget,
            R.string.unit_bar_celsius
        ),
        "tempInOutWidget" to DataTitle(R.string.data_title_temp_in_out_widget),
        "fuelLevelWidget" to DataTitle(R.string.data_title_fuel_level_widget),
        "airQualityWidget" to DataTitle(R.string.data_title_air_quality_widget),
        "steeringWheelHeatWidget" to DataTitle(R.string.data_title_steering_wheel_heat_widget),
        WIPER_MAINTENANCE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_wiper_maintenance_widget),
        PARKING_RADAR_WIDGET_DATA_KEY to DataTitle(R.string.data_title_parking_radar_widget),
        REAR_FOG_WIDGET_DATA_KEY to DataTitle(R.string.data_title_rear_fog_widget),
        HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_headlight_mode_cycle_widget),
        AVH_WIDGET_DATA_KEY to DataTitle(R.string.data_title_avh_widget),
        HDC_WIDGET_DATA_KEY to DataTitle(R.string.data_title_hdc_widget),
        ESP_OFF_WIDGET_DATA_KEY to DataTitle(R.string.data_title_esp_off_widget),
        LDW_WIDGET_DATA_KEY to DataTitle(R.string.data_title_ldw_widget),
        LKA_WIDGET_DATA_KEY to DataTitle(R.string.data_title_lka_widget),
        TJA_ICA_WIDGET_DATA_KEY to DataTitle(R.string.data_title_tja_ica_widget),
        HMA_WIDGET_DATA_KEY to DataTitle(R.string.data_title_hma_widget),
        HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_hvac_custom_mode_cycle_widget),
        HVAC_AC_MAX_WIDGET_DATA_KEY to DataTitle(R.string.data_title_hvac_ac_max_widget),
        ACC_CRUISE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_acc_cruise_widget),
        CRUISE_STATUS_WIDGET_DATA_KEY to DataTitle(R.string.data_title_cruise_status_widget),
        SLA_SPEED_LIMIT_WIDGET_DATA_KEY to DataTitle(R.string.data_title_sla_speed_limit_widget),
        OSM_SPEED_LIMIT_WIDGET_DATA_KEY to DataTitle(R.string.data_title_osm_speed_limit_widget),
        SPEED_LIMITER_WIDGET_DATA_KEY to DataTitle(R.string.data_title_speed_limiter_widget),
        "frontWindscreenHeatWidget" to DataTitle(R.string.data_title_front_windscreen_heat_widget),
        "rearWindowMirrorsDefrostWidget" to DataTitle(R.string.data_title_rear_window_mirrors_defrost_widget),
        "hvacAirRecirculationWidget" to DataTitle(R.string.data_title_hvac_air_recirculation_widget),
        "hvacAcWidget" to DataTitle(R.string.data_title_hvac_ac_widget),
        "hvacAcCleanWhenLockedWidget" to DataTitle(R.string.data_title_hvac_ac_clean_when_locked_widget),
        "hvacAutoWidget" to DataTitle(R.string.data_title_hvac_auto_widget),
        "hvacDefrosterFrontWidget" to DataTitle(R.string.data_title_hvac_defroster_front_widget),
        HVAC_SYNC_WIDGET_DATA_KEY to DataTitle(R.string.data_title_hvac_sync_widget),
        HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY to DataTitle(R.string.data_title_hvac_fan_widget_horizontal),
        HVAC_FAN_WIDGET_VERTICAL_DATA_KEY to DataTitle(R.string.data_title_hvac_fan_widget_vertical),
        HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY to DataTitle(R.string.data_title_hvac_temp_left_widget_horizontal),
        HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY to DataTitle(R.string.data_title_hvac_temp_left_widget_vertical),
        HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY to DataTitle(R.string.data_title_hvac_temp_right_widget_horizontal),
        HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY to DataTitle(R.string.data_title_hvac_temp_right_widget_vertical),
        HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_hvac_blow_mode_cycle_widget),
        HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY to DataTitle(R.string.data_title_hvac_blow_mode_panel_widget_horizontal),
        HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY to DataTitle(R.string.data_title_hvac_blow_mode_panel_widget_vertical),
        TRUNK_DOOR_WIDGET_DATA_KEY to DataTitle(R.string.data_title_trunk_door_widget),
        MIRROR_ADJUST_MODE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_mirror_adjust_mode_widget),
        MIRROR_FOLD_WIDGET_DATA_KEY to DataTitle(R.string.data_title_mirror_fold_widget),
        DAY_NIGHT_THEME_WIDGET_DATA_KEY to DataTitle(R.string.data_title_day_night_theme_widget),
        "frontLeftSeatHeatVentWidget" to DataTitle(R.string.data_title_front_left_seat_heat_vent_widget),
        "frontRightSeatHeatVentWidget" to DataTitle(R.string.data_title_front_right_seat_heat_vent_widget),
        FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY to DataTitle(
            R.string.data_title_front_left_seat_heat_vent_single_widget
        ),
        FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY to DataTitle(
            R.string.data_title_front_right_seat_heat_vent_single_widget
        ),
        REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY to DataTitle(R.string.data_title_rear_left_seat_heat_widget),
        REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY to DataTitle(R.string.data_title_rear_right_seat_heat_widget),
        MUSIC_WIDGET_DATA_KEY to DataTitle(R.string.data_title_music_widget),
        MUSIC_COVER_WIDGET_DATA_KEY to DataTitle(R.string.data_title_music_cover_widget),
        MUSIC_SQUARE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_music_square_widget),
        MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY to DataTitle(
            R.string.data_title_music_buttons_widget_horizontal
        ),
        MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY to DataTitle(
            R.string.data_title_music_buttons_widget_vertical
        ),
        MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY to DataTitle(
            R.string.data_title_media_volume_widget_horizontal
        ),
        MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY to DataTitle(
            R.string.data_title_media_volume_widget_vertical
        ),
        APP_LAUNCHER_WIDGET_DATA_KEY to DataTitle(R.string.data_title_app_launcher_widget),
        HTTP_REQUEST_WIDGET_DATA_KEY to DataTitle(R.string.data_title_http_request_widget),
        EMPTY_TILE_WIDGET_DATA_KEY to DataTitle(R.string.data_title_empty_tile_widget),
        "restartTbox" to DataTitle(R.string.data_title_restart_tbox),
        "espConnected" to DataTitle(R.string.data_title_esp_connected),
        "espGpioIn0" to DataTitle(R.string.data_title_esp_gpio_in_0),
        "espGpioIn1" to DataTitle(R.string.data_title_esp_gpio_in_1),
        "espGpioIn2" to DataTitle(R.string.data_title_esp_gpio_in_2),
        "espGpioIn3" to DataTitle(R.string.data_title_esp_gpio_in_3),
        "espRelay0" to DataTitle(R.string.data_title_esp_relay_0),
        "espRelay1" to DataTitle(R.string.data_title_esp_relay_1),
        EXTERNAL_WIDGET_DATA_KEY to DataTitle(R.string.data_title_external_app_widget),
        HIDE_FLOATING_PANELS_WIDGET_DATA_KEY to DataTitle(R.string.data_title_hide_floating_panels_widget),
        TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY to DataTitle(
            R.string.data_title_toggle_floating_panels_enabled_widget
        ),
    )

    private val widgetDescriptions = mapOf(
        "voltage" to WidgetDescription(R.string.widget_desc_voltage),
        "steerAngle" to WidgetDescription(R.string.widget_desc_steer_angle),
        "steerSpeed" to WidgetDescription(R.string.widget_desc_steer_speed),
        "engineRPM" to WidgetDescription(R.string.widget_desc_engine_rpm),
        "carSpeed" to WidgetDescription(R.string.widget_desc_car_speed),
        "carSpeedAccurate" to WidgetDescription(R.string.widget_desc_car_speed_accurate),
        "cruiseSetSpeed" to WidgetDescription(R.string.widget_desc_cruise_set_speed),
        "odometer" to WidgetDescription(R.string.widget_desc_odometer),
        "distanceToNextMaintenance" to WidgetDescription(R.string.widget_desc_distance_to_next_maintenance),
        "distanceToFuelEmpty" to WidgetDescription(R.string.widget_desc_distance_to_fuel_empty),
        "fuelLevelPercentage" to WidgetDescription(R.string.widget_desc_fuel_level_percentage),
        "fuelLevelPercentageFiltered" to WidgetDescription(R.string.widget_desc_fuel_level_percentage_filtered),
        "fuelLevelLiters" to WidgetDescription(R.string.widget_desc_fuel_level_liters),
        "fuelLevelLitersActual" to WidgetDescription(R.string.widget_desc_fuel_level_liters_actual),
        "currentFuelConsumption" to WidgetDescription(R.string.widget_desc_current_fuel_consumption),
        AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_average_fuel_consumption,
        ),
        "breakingForce" to WidgetDescription(R.string.widget_desc_breaking_force),
        GAS_BRAKE_WIDGET_DATA_KEY to WidgetDescription(R.string.widget_desc_gas_brake_widget),
        "engineTemperature" to WidgetDescription(R.string.widget_desc_engine_temperature),
        "gearBoxOilTemperature" to WidgetDescription(R.string.widget_desc_gearbox_oil_temperature),
        "gearBoxCurrentGear" to WidgetDescription(R.string.widget_desc_gearbox_current_gear),
        "gearBoxPreparedGear" to WidgetDescription(R.string.widget_desc_gearbox_prepared_gear),
        "gearBoxChangeGear" to WidgetDescription(R.string.widget_desc_gearbox_change_gear),
        "gearBoxMode" to WidgetDescription(R.string.widget_desc_gearbox_mode),
        "gearBoxDriveMode" to WidgetDescription(R.string.widget_desc_gearbox_drive_mode),
        "gearBoxWork" to WidgetDescription(R.string.widget_desc_gearbox_work),
        "gnssSpeed" to WidgetDescription(R.string.widget_desc_gnss_speed),
        "visibleSatellites" to WidgetDescription(R.string.widget_desc_visible_satellites),
        "longitude" to WidgetDescription(R.string.widget_desc_longitude),
        "latitude" to WidgetDescription(R.string.widget_desc_latitude),
        "altitude" to WidgetDescription(R.string.widget_desc_altitude),
        "trueDirection" to WidgetDescription(R.string.widget_desc_true_direction),
        "outsideTemperature" to WidgetDescription(R.string.widget_desc_outside_temperature),
        "insideTemperature" to WidgetDescription(R.string.widget_desc_inside_temperature),
        "outsideAirQuality" to WidgetDescription(R.string.widget_desc_outside_air_quality),
        "insideAirQuality" to WidgetDescription(R.string.widget_desc_inside_air_quality),
        "motorHours" to WidgetDescription(
            R.string.widget_desc_motor_hours,
            R.string.widget_actions_motor_hours,
        ),
        "motorHoursTrip" to WidgetDescription(R.string.widget_desc_motor_hours_trip),
        "motorHoursWidget" to WidgetDescription(
            R.string.widget_desc_motor_hours_widget,
            R.string.widget_actions_motor_hours,
        ),
        "timeWidget" to WidgetDescription(R.string.widget_desc_time),
        "dateWidget" to WidgetDescription(R.string.widget_desc_date),
        CPU_USAGE_WIDGET_DATA_KEY to WidgetDescription(R.string.widget_desc_cpu_usage),
        FREE_RAM_PERCENT_WIDGET_DATA_KEY to WidgetDescription(R.string.widget_desc_free_ram_percent),
        "activeTripWidget" to WidgetDescription(
            R.string.widget_desc_active_trip,
            R.string.widget_actions_active_trip,
        ),
        "activeTripWidgetSimple" to WidgetDescription(
            R.string.widget_desc_active_trip_simple,
            R.string.widget_actions_active_trip,
        ),
        "activeTripWidgetMini" to WidgetDescription(
            R.string.widget_desc_active_trip_mini,
            R.string.widget_actions_active_trip,
        ),
        ACTIVE_TRIP_WIDGET_CUSTOM_DATA_KEY to WidgetDescription(
            R.string.widget_desc_active_trip_custom,
            R.string.widget_actions_active_trip,
        ),
        "netWidget" to WidgetDescription(R.string.widget_desc_net_signal),
        "netWidgetNew" to WidgetDescription(R.string.widget_desc_net_new),
        "netWidgetColored" to WidgetDescription(R.string.widget_desc_net_colored),
        "locWidget" to WidgetDescription(R.string.widget_desc_navigation),
        GEOPOSITION_DATA_WIDGET_DATA_KEY to WidgetDescription(R.string.widget_desc_geoposition_data),
        ROAD_MATCH_MAP_WIDGET_DATA_KEY to WidgetDescription(
            if (BuildConfig.MAPKIT_ENABLED) {
                R.string.widget_desc_road_match_map
            } else {
                R.string.widget_desc_road_match_map_canvas_only
            },
        ),
        MOCK_LOCATION_MODE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_mock_location_mode,
            R.string.widget_actions_mock_location_mode,
        ),
        GNSS_DEBUG_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_gnss_debug,
            R.string.widget_actions_gnss_debug,
        ),
        "voltage+engineTemperatureWidget" to WidgetDescription(R.string.widget_desc_voltage_engine_temperature),
        "gearBoxWidget" to WidgetDescription(R.string.widget_desc_gearbox),
        GEARBOX_MODE_CURRENT_GEAR_DATA_KEY to WidgetDescription(R.string.widget_desc_gearbox_mode_current_gear),
        DRIVE_MODE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_drive_mode,
            R.string.widget_actions_drive_mode,
        ),
        DRIVE_MODE_CYCLE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_drive_mode_cycle,
            R.string.widget_actions_drive_mode_cycle,
        ),
        "wheel1Pressure" to WidgetDescription(R.string.widget_desc_wheel_pressure_fl),
        "wheel2Pressure" to WidgetDescription(R.string.widget_desc_wheel_pressure_fr),
        "wheel3Pressure" to WidgetDescription(R.string.widget_desc_wheel_pressure_rl),
        "wheel4Pressure" to WidgetDescription(R.string.widget_desc_wheel_pressure_rr),
        "wheel1Temperature" to WidgetDescription(R.string.widget_desc_wheel_temperature_fl),
        "wheel2Temperature" to WidgetDescription(R.string.widget_desc_wheel_temperature_fr),
        "wheel3Temperature" to WidgetDescription(R.string.widget_desc_wheel_temperature_rl),
        "wheel4Temperature" to WidgetDescription(R.string.widget_desc_wheel_temperature_rr),
        "wheelsPressureWidget" to WidgetDescription(R.string.widget_desc_wheels_pressure),
        "wheelsPressureTemperatureWidget" to WidgetDescription(R.string.widget_desc_wheels_pressure_temperature),
        "tempInOutWidget" to WidgetDescription(R.string.widget_desc_temp_in_out),
        "fuelLevelWidget" to WidgetDescription(R.string.widget_desc_fuel_level),
        "airQualityWidget" to WidgetDescription(R.string.widget_desc_air_quality),
        "steeringWheelHeatWidget" to WidgetDescription(
            R.string.widget_desc_steering_wheel_heat,
            R.string.widget_actions_steering_wheel_heat,
        ),
        WIPER_MAINTENANCE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_wiper_maintenance,
            R.string.widget_actions_wiper_maintenance,
        ),
        PARKING_RADAR_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_parking_radar,
            R.string.widget_actions_parking_radar,
        ),
        REAR_FOG_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_rear_fog,
            R.string.widget_actions_rear_fog,
        ),
        HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_headlight_mode_cycle,
            R.string.widget_actions_headlight_mode_cycle,
        ),
        AVH_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_avh,
            R.string.widget_actions_avh,
        ),
        HDC_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hdc,
            R.string.widget_actions_hdc,
        ),
        ESP_OFF_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_esp_off,
            R.string.widget_actions_esp_off,
        ),
        LDW_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_ldw,
            R.string.widget_actions_ldw,
        ),
        LKA_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_lka,
            R.string.widget_actions_lka,
        ),
        TJA_ICA_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_tja_ica,
            R.string.widget_actions_tja_ica,
        ),
        HMA_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hma,
            R.string.widget_actions_hma,
        ),
        HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_custom_mode_cycle,
            R.string.widget_actions_hvac_custom_mode_cycle,
        ),
        HVAC_AC_MAX_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_ac_max,
            R.string.widget_actions_hvac_ac_max,
        ),
        ACC_CRUISE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_acc_cruise,
            R.string.widget_actions_acc_cruise,
        ),
        CRUISE_STATUS_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_cruise_status,
            R.string.widget_actions_cruise_status,
        ),
        SLA_SPEED_LIMIT_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_sla_speed_limit,
        ),
        OSM_SPEED_LIMIT_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_osm_speed_limit,
        ),
        SPEED_LIMITER_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_speed_limiter,
            R.string.widget_actions_speed_limiter,
        ),
        "frontWindscreenHeatWidget" to WidgetDescription(
            R.string.widget_desc_front_windscreen_heat,
            R.string.widget_actions_front_windscreen_heat,
        ),
        "rearWindowMirrorsDefrostWidget" to WidgetDescription(
            R.string.widget_desc_rear_window_mirrors_defrost,
            R.string.widget_actions_rear_window_mirrors_defrost,
        ),
        "hvacAirRecirculationWidget" to WidgetDescription(
            R.string.widget_desc_hvac_air_recirculation,
            R.string.widget_actions_hvac_air_recirculation,
        ),
        "hvacAcWidget" to WidgetDescription(
            R.string.widget_desc_hvac_ac,
            R.string.widget_actions_hvac_ac,
        ),
        "hvacAcCleanWhenLockedWidget" to WidgetDescription(
            R.string.widget_desc_hvac_ac_clean_when_locked,
            R.string.widget_actions_hvac_ac_clean_when_locked,
        ),
        "hvacAutoWidget" to WidgetDescription(
            R.string.widget_desc_hvac_auto,
            R.string.widget_actions_hvac_auto,
        ),
        "hvacDefrosterFrontWidget" to WidgetDescription(
            R.string.widget_desc_hvac_defroster_front,
            R.string.widget_actions_hvac_defroster_front,
        ),
        HVAC_SYNC_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_sync,
            R.string.widget_actions_hvac_sync,
        ),
        HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_fan_horizontal,
            R.string.widget_actions_hvac_fan,
        ),
        HVAC_FAN_WIDGET_VERTICAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_fan_vertical,
            R.string.widget_actions_hvac_fan,
        ),
        HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_temp_left_horizontal,
            R.string.widget_actions_hvac_temperature,
        ),
        HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_temp_left_vertical,
            R.string.widget_actions_hvac_temperature,
        ),
        HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_temp_right_horizontal,
            R.string.widget_actions_hvac_temperature,
        ),
        HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_temp_right_vertical,
            R.string.widget_actions_hvac_temperature,
        ),
        HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_blow_mode_cycle,
            R.string.widget_actions_hvac_blow_mode_cycle,
        ),
        HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_blow_mode_panel_horizontal,
            R.string.widget_actions_hvac_blow_mode_panel,
        ),
        HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hvac_blow_mode_panel_vertical,
            R.string.widget_actions_hvac_blow_mode_panel,
        ),
        TRUNK_DOOR_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_trunk_door,
            R.string.widget_actions_trunk_door,
        ),
        MIRROR_ADJUST_MODE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_mirror_adjust_mode,
            R.string.widget_actions_mirror_adjust_mode,
        ),
        MIRROR_FOLD_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_mirror_fold,
            R.string.widget_actions_mirror_fold,
        ),
        DAY_NIGHT_THEME_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_day_night_theme,
            R.string.widget_actions_day_night_theme,
        ),
        "frontLeftSeatHeatVentWidget" to WidgetDescription(
            R.string.widget_desc_front_left_seat_heat_vent,
            R.string.widget_actions_seat_heat_vent_dual,
        ),
        "frontRightSeatHeatVentWidget" to WidgetDescription(
            R.string.widget_desc_front_right_seat_heat_vent,
            R.string.widget_actions_seat_heat_vent_dual,
        ),
        FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_front_left_seat_heat_vent_single,
            R.string.widget_actions_seat_heat_vent_single,
        ),
        FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_front_right_seat_heat_vent_single,
            R.string.widget_actions_seat_heat_vent_single,
        ),
        REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_rear_left_seat_heat,
            R.string.widget_actions_rear_seat_heat,
        ),
        REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_rear_right_seat_heat,
            R.string.widget_actions_rear_seat_heat,
        ),
        MUSIC_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_music,
            R.string.widget_actions_music,
        ),
        MUSIC_COVER_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_music_cover,
            R.string.widget_actions_music,
        ),
        MUSIC_SQUARE_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_music_square,
            R.string.widget_actions_music_square,
        ),
        MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_music_buttons_horizontal,
            R.string.widget_actions_music_buttons,
        ),
        MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_music_buttons_vertical,
            R.string.widget_actions_music_buttons,
        ),
        MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_media_volume_horizontal,
            R.string.widget_actions_media_volume,
        ),
        MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY to WidgetDescription(
            R.string.widget_desc_media_volume_vertical,
            R.string.widget_actions_media_volume,
        ),
        APP_LAUNCHER_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_app_launcher,
            R.string.widget_actions_app_launcher,
        ),
        HTTP_REQUEST_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_http_request,
            R.string.widget_actions_http_request,
        ),
        EMPTY_TILE_WIDGET_DATA_KEY to WidgetDescription(R.string.widget_desc_empty_tile),
        "restartTbox" to WidgetDescription(
            R.string.widget_desc_restart_tbox,
            R.string.widget_actions_restart_tbox,
        ),
        "espConnected" to WidgetDescription(R.string.widget_desc_esp_connected),
        "espGpioIn0" to WidgetDescription(R.string.widget_desc_esp_gpio),
        "espGpioIn1" to WidgetDescription(R.string.widget_desc_esp_gpio),
        "espGpioIn2" to WidgetDescription(R.string.widget_desc_esp_gpio),
        "espGpioIn3" to WidgetDescription(R.string.widget_desc_esp_gpio),
        "espRelay0" to WidgetDescription(R.string.widget_desc_esp_relay),
        "espRelay1" to WidgetDescription(R.string.widget_desc_esp_relay),
        EXTERNAL_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_external_app,
            R.string.widget_actions_external_app,
        ),
        HIDE_FLOATING_PANELS_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_hide_floating_panels,
            R.string.widget_actions_hide_floating_panels,
        ),
        TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY to WidgetDescription(
            R.string.widget_desc_toggle_floating_panels_enabled,
            R.string.widget_actions_toggle_floating_panels_enabled,
        ),
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

    fun getAvailableDataKeysWidgets(noTboxConnect: Boolean = false): List<String> {
        val keys = dataKeyTitlesWidgets.keys.toList()
        if (!noTboxConnect) return keys
        return keys.filter { isWidgetOfferedWhenNoTbox(it) }
    }

    @StringRes
    fun getDescriptionResForDataKey(dataKey: String): Int? {
        return widgetDescriptions[dataKey]?.descriptionRes
    }

    @StringRes
    fun getActionsDescriptionResForDataKey(dataKey: String): Int? {
        return widgetDescriptions[dataKey]?.actionsRes
    }

    /**
     * Tile types where [FloatingDashboardWidgetConfig.showUnit] affects the UI
     * ([DashboardWidgetItem] or composite widgets that pass `units`).
     */
    fun supportsShowUnit(dataKey: String): Boolean {
        if (dataKey.isBlank()) return false
        return when (dataKey) {
            "netWidget",
            "netWidgetNew",
            "netWidgetColored",
            "locWidget",
            ROAD_MATCH_MAP_WIDGET_DATA_KEY,
            MOCK_LOCATION_MODE_WIDGET_DATA_KEY,
            GNSS_DEBUG_WIDGET_DATA_KEY,
            "airQualityWidget",
            "steeringWheelHeatWidget",
            PARKING_RADAR_WIDGET_DATA_KEY,
            REAR_FOG_WIDGET_DATA_KEY,
            HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY,
            AVH_WIDGET_DATA_KEY,
            HDC_WIDGET_DATA_KEY,
            ESP_OFF_WIDGET_DATA_KEY,
            LDW_WIDGET_DATA_KEY,
            LKA_WIDGET_DATA_KEY,
            TJA_ICA_WIDGET_DATA_KEY,
            HMA_WIDGET_DATA_KEY,
            HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY,
            HVAC_AC_MAX_WIDGET_DATA_KEY,
            ACC_CRUISE_WIDGET_DATA_KEY,
            CRUISE_STATUS_WIDGET_DATA_KEY,
            SLA_SPEED_LIMIT_WIDGET_DATA_KEY,
            OSM_SPEED_LIMIT_WIDGET_DATA_KEY,
            SPEED_LIMITER_WIDGET_DATA_KEY,
            "frontWindscreenHeatWidget",
            "rearWindowMirrorsDefrostWidget",
            "hvacAirRecirculationWidget",
            "hvacAcWidget",
            "hvacAcCleanWhenLockedWidget",
            "hvacAutoWidget",
            "hvacDefrosterFrontWidget",
            HVAC_SYNC_WIDGET_DATA_KEY,
            HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_FAN_WIDGET_VERTICAL_DATA_KEY,
            HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY,
            HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY,
            HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY,
            HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY,
            TRUNK_DOOR_WIDGET_DATA_KEY,
            MIRROR_ADJUST_MODE_WIDGET_DATA_KEY,
            MIRROR_FOLD_WIDGET_DATA_KEY,
            DAY_NIGHT_THEME_WIDGET_DATA_KEY,
            "frontLeftSeatHeatVentWidget",
            "frontRightSeatHeatVentWidget",
            FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
            FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
            REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY,
            REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY,
            EXTERNAL_WIDGET_DATA_KEY,
            APP_LAUNCHER_WIDGET_DATA_KEY,
            EMPTY_TILE_WIDGET_DATA_KEY,
            MUSIC_WIDGET_DATA_KEY,
            MUSIC_COVER_WIDGET_DATA_KEY,
            MUSIC_SQUARE_WIDGET_DATA_KEY,
            MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
            MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY,
            MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY,
            MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY,
            HIDE_FLOATING_PANELS_WIDGET_DATA_KEY,
            TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY,
            HTTP_REQUEST_WIDGET_DATA_KEY,
            "timeWidget",
            "dateWidget",
            DRIVE_MODE_WIDGET_DATA_KEY,
            DRIVE_MODE_CYCLE_WIDGET_DATA_KEY,
            GEARBOX_MODE_CURRENT_GEAR_DATA_KEY,
            -> false
            else -> !isActiveTripWidgetDataKey(dataKey)
        }
    }

    /** Widget types that support optional single-line layout for two metrics. */
    fun supportsSingleLineDualMetrics(dataKey: String): Boolean {
        if (isSeatHeatVentSingleWidgetDataKey(dataKey)) return false
        return dataKey in setOf(
            "gearBoxWidget",
            "motorHoursWidget",
            "tempInOutWidget",
            "voltage+engineTemperatureWidget",
            "fuelLevelWidget",
            "airQualityWidget",
            "frontLeftSeatHeatVentWidget",
            "frontRightSeatHeatVentWidget"
        )
    }

    /**
     * Tile types whose primary values come from [vad.dashing.tbox.ui.TboxDataProvider.getValueFlow]
     * and can use per-tile [FloatingDashboardWidgetConfig.valueAccuracy].
     */
    fun supportsValueAccuracy(dataKey: String): Boolean {
        if (dataKey.isBlank()) return false
        return when (dataKey) {
            EXTERNAL_WIDGET_DATA_KEY,
            MUSIC_WIDGET_DATA_KEY,
            MUSIC_COVER_WIDGET_DATA_KEY,
            MUSIC_SQUARE_WIDGET_DATA_KEY,
            MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
            MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY,
            APP_LAUNCHER_WIDGET_DATA_KEY,
            HTTP_REQUEST_WIDGET_DATA_KEY,
            EMPTY_TILE_WIDGET_DATA_KEY,
            MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY,
            MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY,
            HIDE_FLOATING_PANELS_WIDGET_DATA_KEY,
            TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY,
            "timeWidget",
            "dateWidget",
            "restartTbox",
            "netWidget",
            "netWidgetNew",
            "netWidgetColored",
            ROAD_MATCH_MAP_WIDGET_DATA_KEY,
            "frontLeftSeatHeatVentWidget",
            "frontRightSeatHeatVentWidget",
            FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
            FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
            REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY,
            REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY,
            "steeringWheelHeatWidget",
            PARKING_RADAR_WIDGET_DATA_KEY,
            REAR_FOG_WIDGET_DATA_KEY,
            HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY,
            AVH_WIDGET_DATA_KEY,
            HDC_WIDGET_DATA_KEY,
            ESP_OFF_WIDGET_DATA_KEY,
            LDW_WIDGET_DATA_KEY,
            LKA_WIDGET_DATA_KEY,
            TJA_ICA_WIDGET_DATA_KEY,
            HMA_WIDGET_DATA_KEY,
            HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY,
            HVAC_AC_MAX_WIDGET_DATA_KEY,
            ACC_CRUISE_WIDGET_DATA_KEY,
            CRUISE_STATUS_WIDGET_DATA_KEY,
            SLA_SPEED_LIMIT_WIDGET_DATA_KEY,
            OSM_SPEED_LIMIT_WIDGET_DATA_KEY,
            SPEED_LIMITER_WIDGET_DATA_KEY,
            "frontWindscreenHeatWidget",
            "rearWindowMirrorsDefrostWidget",
            "hvacAirRecirculationWidget",
            "hvacAcWidget",
            "hvacAcCleanWhenLockedWidget",
            "hvacAutoWidget",
            "hvacDefrosterFrontWidget",
            HVAC_SYNC_WIDGET_DATA_KEY,
            HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_FAN_WIDGET_VERTICAL_DATA_KEY,
            HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY,
            HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY,
            HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY,
            HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY,
            TRUNK_DOOR_WIDGET_DATA_KEY,
            MIRROR_ADJUST_MODE_WIDGET_DATA_KEY,
            MIRROR_FOLD_WIDGET_DATA_KEY,
            DAY_NIGHT_THEME_WIDGET_DATA_KEY,
            DRIVE_MODE_WIDGET_DATA_KEY,
            DRIVE_MODE_CYCLE_WIDGET_DATA_KEY,
            GEARBOX_MODE_CURRENT_GEAR_DATA_KEY,
            -> false
            else -> !isActiveTripWidgetDataKey(dataKey)
        }
    }

    fun supportsDateTimeFormat(dataKey: String): Boolean = isDateTimeWidgetDataKey(dataKey)

    /**
     * Widget types where [FloatingDashboardWidgetConfig.useMbCanVhal] is available in widget settings.
     */
    fun supportsUseMbCanVhal(dataKey: String): Boolean {
        if (dataKey.isBlank()) return false
        return dataKey in setOf(
            ENGINE_RPM_WIDGET_DATA_KEY,
            ENGINE_TEMPERATURE_WIDGET_DATA_KEY,
            CAR_SPEED_WIDGET_DATA_KEY,
            GEAR_BOX_MODE_WIDGET_DATA_KEY,
            ODOMETER_WIDGET_DATA_KEY,
            FUEL_LEVEL_PERCENTAGE_WIDGET_DATA_KEY,
            OUTSIDE_TEMPERATURE_WIDGET_DATA_KEY,
            WHEELS_PRESSURE_WIDGET_DATA_KEY,
            WHEELS_PRESSURE_TEMPERATURE_WIDGET_DATA_KEY,
            WHEEL1_PRESSURE_WIDGET_DATA_KEY,
            WHEEL2_PRESSURE_WIDGET_DATA_KEY,
            WHEEL3_PRESSURE_WIDGET_DATA_KEY,
            WHEEL4_PRESSURE_WIDGET_DATA_KEY,
            WHEEL1_TEMPERATURE_WIDGET_DATA_KEY,
            WHEEL2_TEMPERATURE_WIDGET_DATA_KEY,
            WHEEL3_TEMPERATURE_WIDGET_DATA_KEY,
            WHEEL4_TEMPERATURE_WIDGET_DATA_KEY,
            CURRENT_FUEL_CONSUMPTION_WIDGET_DATA_KEY,
            DISTANCE_TO_NEXT_MAINTENANCE_WIDGET_DATA_KEY,
            DISTANCE_TO_FUEL_EMPTY_WIDGET_DATA_KEY,
            INSIDE_AIR_QUALITY_WIDGET_DATA_KEY,
            OUTSIDE_AIR_QUALITY_WIDGET_DATA_KEY,
            AIR_QUALITY_WIDGET_DATA_KEY,
            STEER_ANGLE_WIDGET_DATA_KEY,
            STEER_SPEED_WIDGET_DATA_KEY,
        )
    }

    /**
     * Widget types that only work via TBox UDP / modem / CDR (no HU path, no [supportsUseMbCanVhal]).
     * Hidden from the picker when «Не подключаться к TBox» is on; existing tiles are left as-is.
     */
    fun requiresTboxConnection(dataKey: String): Boolean {
        if (dataKey.isBlank()) return false
        return dataKey in setOf(
            "voltage",
            "carSpeedAccurate",
            "cruiseSetSpeed",
            "breakingForce",
            "gearBoxOilTemperature",
            "gearBoxCurrentGear",
            "gearBoxPreparedGear",
            "gearBoxChangeGear",
            "gearBoxDriveMode",
            "gearBoxWork",
            "gearBoxWidget",
            GEARBOX_MODE_CURRENT_GEAR_DATA_KEY,
            "insideTemperature",
            "voltage+engineTemperatureWidget",
            "tempInOutWidget",
            "netWidget",
            "netWidgetNew",
            "netWidgetColored",
            "restartTbox",
        )
    }

    fun isWidgetOfferedWhenNoTbox(dataKey: String): Boolean =
        !requiresTboxConnection(dataKey)

    /**
     * When no-TBox mode is on, eligible tiles get [FloatingDashboardWidgetConfig.useMbCanVhal]=true
     * (theme import / paste / new tile defaults). Does not clear the flag when mode is off.
     */
    fun preferUseMbCanVhalOnConfigs(
        widgets: List<FloatingDashboardWidgetConfig>,
        noTboxConnect: Boolean,
    ): List<FloatingDashboardWidgetConfig> {
        if (!noTboxConnect) return widgets
        return widgets.map { cfg ->
            if (supportsUseMbCanVhal(cfg.dataKey) && !cfg.useMbCanVhal) {
                cfg.copy(useMbCanVhal = true)
            } else {
                cfg
            }
        }
    }

    fun supportsStepperAdjustIconStyle(dataKey: String): Boolean = isStepperWidgetDataKey(dataKey)

    fun supportsHvacTempStep(dataKey: String): Boolean = isHvacTempWidgetDataKey(dataKey)

    fun supportsEspRelayMode(dataKey: String): Boolean = isEspRelayWidgetDataKey(dataKey)
}

const val DEFAULT_WIDGET_TEXT_COLOR_LIGHT = LIGHT_THEME_ON_SURFACE_COLOR_INT
const val DEFAULT_WIDGET_TEXT_COLOR_DARK = DARK_THEME_ON_SURFACE_COLOR_INT
const val DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_MAIN = 0xFFFFFFFF.toInt()
const val DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_MAIN = 0xFF131C2D.toInt()
const val DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING = 0x00000000
const val DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING = 0x00000000

// Модель для виджета панели
data class DashboardWidget(
    val id: Int,
    val title: String,
    val unit: String = "",
    val dataKey: String = "", // Ключ для идентификации данных
    val maxValue: Float? = null,
    val minValue: Float? = null,
    val textColorLight: Int = DEFAULT_WIDGET_TEXT_COLOR_LIGHT,
    val textColorDark: Int = DEFAULT_WIDGET_TEXT_COLOR_DARK,
    val backgroundColorLight: Int = DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_MAIN,
    val backgroundColorDark: Int = DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_MAIN,
    /** Overrides numeric formatting for this tile when non-null; see [FloatingDashboardWidgetConfig.valueAccuracy]. */
    val valueAccuracy: Int? = null
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