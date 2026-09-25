package vad.dashing.tbox.automation

import java.util.UUID
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.AUTOMATION_TRIGGER_WIDGET_TOGGLE_INT
import vad.dashing.tbox.DEFAULT_HTTP_REQUEST_WIDGET_YAML
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide

const val AUTOMATION_FORMAT_VERSION = 1
const val AUTOMATION_DEFAULT_HOLD_MS = 0L
const val AUTOMATION_MAX_HOLD_MS = 24L * 60L * 60L * 1_000L
const val AUTOMATION_DEFAULT_CONDITION_WAIT_MS = 0L
const val AUTOMATION_MAX_CONDITION_WAIT_MS = AUTOMATION_MAX_HOLD_MS
const val AUTOMATION_MAX_DELAY_MS = 24L * 60L * 60L * 1_000L
const val AUTOMATION_MIN_INTERVAL_MS = AUTOMATION_MIN_LAUNCH_INTERVAL_MS
const val AUTOMATION_DEFAULT_INTERVAL_MS = 60_000L
const val AUTOMATION_MAX_INTERVAL_MS = AUTOMATION_MAX_HOLD_MS
const val AUTOMATION_MAX_CONDITION_DEPTH = 6
const val AUTOMATION_MAX_ACTION_DEPTH = 6
const val AUTOMATION_MAX_ACTION_COUNT = 200
const val AUTOMATION_MAX_USER_MESSAGE_CHARS = 1_000
const val AUTOMATION_GEOFENCE_RADIUS_GAP_M = 10.0
const val AUTOMATION_GEOFENCE_DEFAULT_ZONE_RADIUS_M = 50.0
const val AUTOMATION_GEOFENCE_MAX_RADIUS_M = 1_000_000.0
const val AUTOMATION_SOLAR_MAX_OFFSET_MINUTES = 180
const val AUTOMATION_HARD_KEY_MIN_CODE = 0
const val AUTOMATION_HARD_KEY_MAX_CODE = 1023
const val AUTOMATION_HARD_KEY_DEBOUNCE_MS = 120L

enum class AutomationSignalSource(val storageKey: String) {
    TBOX("tbox"),
    HEAD_UNIT("head_unit"),
    APP("app");

    companion object {
        fun fromStorageKey(raw: String?): AutomationSignalSource? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

enum class AutomationSignalValueType {
    NUMBER,
    STATE,
    POSITION,
}

/**
 * Stable, non-localized identifiers available to triggers and conditions.
 *
 * Source support and user-facing labels live in [AutomationSignalCatalog]; keeping the IDs here
 * makes persisted rules independent from UI wording.
 */
enum class AutomationSignalId(
    val storageKey: String,
    val valueType: AutomationSignalValueType = AutomationSignalValueType.NUMBER,
) {
    ENGINE_RPM("engine_rpm"),
    CAR_SPEED("car_speed"),
    ENGINE_TEMPERATURE("engine_temperature"),
    OUTSIDE_TEMPERATURE("outside_temperature"),
    INSIDE_TEMPERATURE("inside_temperature"),
    FUEL_LEVEL_PERCENT("fuel_level_percent"),
    ODOMETER_KM("odometer_km"),
    CURRENT_FUEL_CONSUMPTION("current_fuel_consumption"),
    DISTANCE_TO_EMPTY_KM("distance_to_empty_km"),
    DISTANCE_TO_MAINTENANCE_KM("distance_to_maintenance_km"),
    VOLTAGE("voltage"),
    STEERING_ANGLE("steering_angle"),
    STEERING_SPEED("steering_speed"),
    CRUISE_SET_SPEED("cruise_set_speed"),
    GEAR_MODE("gear_mode", AutomationSignalValueType.STATE),
    ACC_STATUS("acc_status", AutomationSignalValueType.STATE),
    ACC_CRUISE_STATE("acc_cruise_state", AutomationSignalValueType.STATE),
    CCS_CRUISE_STATE("ccs_cruise_state", AutomationSignalValueType.STATE),
    GAS_PEDAL("gas_pedal"),
    BRAKE_PEDAL("brake_pedal", AutomationSignalValueType.STATE),
    CURRENT_GEAR("current_gear"),
    TARGET_GEAR("target_gear"),
    FRONT_LEFT_WHEEL_PRESSURE("front_left_wheel_pressure"),
    FRONT_RIGHT_WHEEL_PRESSURE("front_right_wheel_pressure"),
    REAR_LEFT_WHEEL_PRESSURE("rear_left_wheel_pressure"),
    REAR_RIGHT_WHEEL_PRESSURE("rear_right_wheel_pressure"),
    FRONT_LEFT_WHEEL_TEMPERATURE("front_left_wheel_temperature"),
    FRONT_RIGHT_WHEEL_TEMPERATURE("front_right_wheel_temperature"),
    REAR_LEFT_WHEEL_TEMPERATURE("rear_left_wheel_temperature"),
    REAR_RIGHT_WHEEL_TEMPERATURE("rear_right_wheel_temperature"),
    INSIDE_AIR_QUALITY("inside_air_quality"),
    OUTSIDE_AIR_QUALITY("outside_air_quality"),
    STEERING_WHEEL_HEAT("steering_wheel_heat", AutomationSignalValueType.STATE),
    WIPER_MAINTENANCE("wiper_maintenance", AutomationSignalValueType.STATE),
    WIPER_STS("wiper_sts", AutomationSignalValueType.STATE),
    RAIN_DETECTED("rain_detected", AutomationSignalValueType.STATE),
    HIGH_BEAM("high_beam", AutomationSignalValueType.STATE),
    EPB_PARK_LAMP("epb_park_lamp", AutomationSignalValueType.STATE),
    ENGINE_OIL_PRESSURE("engine_oil_pressure", AutomationSignalValueType.STATE),
    BRAKE_FLUID("brake_fluid", AutomationSignalValueType.STATE),
    FRM_DX_TAR_OBJ("frm_dx_tar_obj"),
    SUNSHADE("sunshade", AutomationSignalValueType.STATE),
    SUNROOF("sunroof", AutomationSignalValueType.STATE),
    WINDOW_FRONT_LEFT("window_front_left", AutomationSignalValueType.STATE),
    WINDOW_FRONT_RIGHT("window_front_right", AutomationSignalValueType.STATE),
    WINDOW_REAR_LEFT("window_rear_left", AutomationSignalValueType.STATE),
    WINDOW_REAR_RIGHT("window_rear_right", AutomationSignalValueType.STATE),
    PARKING_RADAR("parking_radar", AutomationSignalValueType.STATE),
    REAR_FOG("rear_fog", AutomationSignalValueType.STATE),
    AVH("avh", AutomationSignalValueType.STATE),
    HDC("hdc", AutomationSignalValueType.STATE),
    ESP_OFF("esp_off", AutomationSignalValueType.STATE),
    TJA_ICA("tja_ica", AutomationSignalValueType.STATE),
    HMA("hma", AutomationSignalValueType.STATE),
    HVAC_AC_MAX("hvac_ac_max", AutomationSignalValueType.STATE),
    HVAC_POWER("hvac_power", AutomationSignalValueType.STATE),
    HVAC_AUTO("hvac_auto", AutomationSignalValueType.STATE),
    HVAC_RECIRCULATION("hvac_recirculation", AutomationSignalValueType.STATE),
    HVAC_SYNC("hvac_sync", AutomationSignalValueType.STATE),
    DRIVE_MODE("drive_mode", AutomationSignalValueType.STATE),
    HEADLIGHT_MODE("headlight_mode", AutomationSignalValueType.STATE),
    DOOR_AUTO_LOCK("door_auto_lock", AutomationSignalValueType.STATE),
    DOOR_IGNOFF_UNLOCK("door_ignoff_unlock", AutomationSignalValueType.STATE),
    HEADLIGHTS_FOLLOW_ME_HOME("headlights_follow_me_home", AutomationSignalValueType.STATE),
    DRIVER_UNLOCK_MODE("driver_unlock_mode", AutomationSignalValueType.STATE),
    REMOTE_LOCK_FEEDBACK("remote_lock_feedback", AutomationSignalValueType.STATE),
    WIPER_SENSITIVITY("wiper_sensitivity"),
    REAR_WIPER("rear_wiper", AutomationSignalValueType.STATE),
    MIRROR_AUTO_FOLD("mirror_auto_fold", AutomationSignalValueType.STATE),
    LOW_BEAM_HEIGHT("low_beam_height"),
    TURN_FLASH_COUNT("turn_flash_count"),
    LAS_MODE("las_mode", AutomationSignalValueType.STATE),
    BLIND_SPOT_DETECTION("blind_spot_detection", AutomationSignalValueType.STATE),
    DOOR_OPEN_WARNING("door_open_warning", AutomationSignalValueType.STATE),
    FCW("fcw", AutomationSignalValueType.STATE),
    FCW_SENSITIVITY("fcw_sensitivity", AutomationSignalValueType.STATE),
    LDW_SENSITIVITY("ldw_sensitivity", AutomationSignalValueType.STATE),
    HVAC_CUSTOM_MODE("hvac_custom_mode", AutomationSignalValueType.STATE),
    FRONT_WINDSCREEN_HEAT("front_windscreen_heat", AutomationSignalValueType.STATE),
    HVAC_REAR_DEFROSTER("hvac_rear_defroster", AutomationSignalValueType.STATE),
    HVAC_AC_CLEAN_WHEN_LOCKED("hvac_ac_clean_when_locked", AutomationSignalValueType.STATE),
    HVAC_ANION_PURIFY("hvac_anion_purify", AutomationSignalValueType.STATE),
    FRAGRANCE("fragrance", AutomationSignalValueType.STATE),
    FRAGRANCE_SMELL("fragrance_smell", AutomationSignalValueType.STATE),
    FRAGRANCE_CONCENTRATION("fragrance_concentration", AutomationSignalValueType.STATE),
    HVAC_FIRST_BLOWING("hvac_first_blowing", AutomationSignalValueType.STATE),
    BT_REDUCE_FAN("bt_reduce_fan", AutomationSignalValueType.STATE),
    HVAC_AUTO_VENTILATION("hvac_auto_ventilation", AutomationSignalValueType.STATE),
    HVAC_FAN_DIRECTION("hvac_fan_direction", AutomationSignalValueType.STATE),
    HVAC_TEMPERATURE_LEFT("hvac_temperature_left"),
    HVAC_TEMPERATURE_RIGHT("hvac_temperature_right"),
    HVAC_FAN_SPEED("hvac_fan_speed"),
    HVAC_FRONT_OFF("hvac_front_off", AutomationSignalValueType.STATE),
    HUD("hud", AutomationSignalValueType.STATE),
    HUD_HEIGHT("hud_height"),
    HUD_BRIGHTNESS("hud_brightness"),
    HUD_DISPLAY_MODE("hud_display_mode", AutomationSignalValueType.STATE),
    HUD_AUTO_BRIGHTNESS("hud_auto_brightness", AutomationSignalValueType.STATE),
    ICM_BRIGHTNESS_MODE("icm_brightness_mode", AutomationSignalValueType.STATE),
    ICM_BRIGHTNESS("icm_brightness"),
    OVERSPEED_ALARM("overspeed_alarm"),
    STEERING_MODE("steering_mode", AutomationSignalValueType.STATE),
    EPS_MODE("eps_mode", AutomationSignalValueType.STATE),
    DRIVE_MODE_6DCT("drive_mode_6dct", AutomationSignalValueType.STATE),
    TSR_SWITCH("tsr_switch", AutomationSignalValueType.STATE),
    TRUNK_DOOR("trunk_door", AutomationSignalValueType.STATE),
    AUDIO_VOLUME_SPEED_MODE("audio_volume_speed_mode", AutomationSignalValueType.STATE),
    AUDIO_KEY_TONE_VOLUME("audio_key_tone_volume"),
    AUDIO_RADAR_ALARM_VOLUME("audio_radar_alarm_volume", AutomationSignalValueType.STATE),
    AUDIO_EQ_MODE("audio_eq_mode", AutomationSignalValueType.STATE),
    AUDIO_EQ_BASS("audio_eq_bass"),
    AUDIO_EQ_MIDDLE("audio_eq_middle"),
    AUDIO_EQ_TREBLE("audio_eq_treble"),
    AUDIO_BALANCE("audio_balance"),
    AUDIO_FADER("audio_fader"),
    REVERSE_GEAR("reverse_gear", AutomationSignalValueType.STATE),
    FRONT_LEFT_SEAT_MODE("front_left_seat_mode", AutomationSignalValueType.STATE),
    FRONT_RIGHT_SEAT_MODE("front_right_seat_mode", AutomationSignalValueType.STATE),
    REAR_LEFT_SEAT_MODE("rear_left_seat_mode", AutomationSignalValueType.STATE),
    REAR_RIGHT_SEAT_MODE("rear_right_seat_mode", AutomationSignalValueType.STATE),
    GEO_POSITION("geo_position", AutomationSignalValueType.POSITION),
    ESP_GPIO_IN_0("esp_gpio_in_0", AutomationSignalValueType.STATE),
    ESP_GPIO_IN_1("esp_gpio_in_1", AutomationSignalValueType.STATE),
    ESP_GPIO_IN_2("esp_gpio_in_2", AutomationSignalValueType.STATE),
    ESP_GPIO_IN_3("esp_gpio_in_3", AutomationSignalValueType.STATE),
    ESP_RELAY_0("esp_relay_0", AutomationSignalValueType.STATE),
    ESP_RELAY_1("esp_relay_1", AutomationSignalValueType.STATE),
    ESP_BLE_BOUND("esp_ble_bound", AutomationSignalValueType.STATE),
    ESP_BLE_BATTERY("esp_ble_battery"),
    WIFI_ENABLED("wifi_enabled", AutomationSignalValueType.STATE),
    WIFI_ASSOCIATED("wifi_associated", AutomationSignalValueType.STATE),
    WIFI_SSID("wifi_ssid", AutomationSignalValueType.STATE),
    HU_INTERNET_STATUS("hu_internet_status", AutomationSignalValueType.STATE),
    /** HTTP link to Wi‑Fi modem admin when source is WIFI_HTTP; [idle] for TBox / poller off. */
    WIFI_MODEM_LINK_STATUS("wifi_modem_link_status", AutomationSignalValueType.STATE),
    /** Mobile data / PPP from [TboxRepository.apnStatus] (TBox or Wi‑Fi modem sink). */
    MODEM_MOBILE_DATA("modem_mobile_data", AutomationSignalValueType.STATE),
    /** Cellular RAT from [TboxRepository.netState] (`2g`/`3g`/`4g`/`none`). */
    MODEM_NET_TYPE("modem_net_type", AutomationSignalValueType.STATE),
    /** SIM readiness from [TboxRepository.netState.simStatus]. */
    MODEM_SIM_STATUS("modem_sim_status", AutomationSignalValueType.STATE),
    FOREGROUND_APP("foreground_app", AutomationSignalValueType.STATE),
    APP_THEME_MODE("app_theme_mode", AutomationSignalValueType.STATE),
    APP_THEME("app_theme", AutomationSignalValueType.STATE),
    /** Head-unit screen backlight UI level 1…10 (not HUD / ICM). */
    HU_SCREEN_BRIGHTNESS("hu_screen_brightness"),
    /** Head-unit screen auto-brightness on/off. */
    HU_SCREEN_AUTO_BRIGHTNESS("hu_screen_auto_brightness", AutomationSignalValueType.STATE),
    /** Platform mixer volumes (Car Settings → Аудио), not mbCAN EQ. */
    HU_MEDIA_VOLUME("hu_media_volume"),
    HU_PHONE_VOLUME("hu_phone_volume"),
    HU_NAVI_VOLUME("hu_navi_volume"),
    HU_VOICE_VOLUME("hu_voice_volume"),
    /** Headrest speaker: `only` / `assist` / `off`. */
    HU_HEADREST_SPEAKER("hu_headrest_speaker", AutomationSignalValueType.STATE);

    companion object {
        fun fromStorageKey(raw: String?): AutomationSignalId? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

enum class AutomationSystemEvent(val storageKey: String) {
    BACKGROUND_SERVICE_STARTED("background_service_started"),
    MAIN_SCREEN_OPENED("main_screen_opened"),
    MENU_OPENED("menu_opened");

    companion object {
        fun fromStorageKey(raw: String?): AutomationSystemEvent? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

/** A9 mbCAN hardkey keyStatus: 0 = нажата, 1 = отпущена. */
enum class AutomationHardKeyStatus(val storageKey: String, val rawValue: Int) {
    PRESSED("pressed", 0),
    RELEASED("released", 1);

    companion object {
        fun fromStorageKey(raw: String?): AutomationHardKeyStatus? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }

        fun fromRawValue(raw: Int): AutomationHardKeyStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** Shelly Blu / BTHome button action from companion `bleBtn.act`. */
enum class AutomationEspBleBtnAction(val storageKey: String) {
    PRESS("press"),
    DOUBLE("double"),
    TRIPLE("triple"),
    LONG("long"),
    HOLD("hold");

    companion object {
        fun fromStorageKey(raw: String?): AutomationEspBleBtnAction? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

const val AUTOMATION_ESP_BLE_BTN_MIN = 1
const val AUTOMATION_ESP_BLE_BTN_MAX = 4

enum class AutomationGeofenceDirection(val storageKey: String) {
    ENTER("enter"),
    EXIT("exit");

    companion object {
        fun fromStorageKey(raw: String?): AutomationGeofenceDirection? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

enum class AutomationGeofencePresence(val storageKey: String) {
    INSIDE("inside"),
    OUTSIDE("outside");

    companion object {
        fun fromStorageKey(raw: String?): AutomationGeofencePresence? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

enum class AutomationUiState(val storageKey: String) {
    SERVICE_RUNNING("service_running"),
    MAIN_SCREEN_OPEN("main_screen_open"),
    MENU_OPEN("menu_open");

    companion object {
        fun fromStorageKey(raw: String?): AutomationUiState? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

enum class AutomationThresholdDirection(val storageKey: String) {
    ABOVE("above"),
    BELOW("below");

    companion object {
        fun fromStorageKey(raw: String?): AutomationThresholdDirection? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

enum class AutomationComparison(val storageKey: String) {
    ABOVE("above"),
    BELOW("below"),
    AT_LEAST("at_least"),
    AT_MOST("at_most"),
    EQUAL("equal"),
    NOT_EQUAL("not_equal");

    companion object {
        fun fromStorageKey(raw: String?): AutomationComparison? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

enum class AutomationSolarEvent(val storageKey: String) {
    SUNRISE("sunrise"),
    SUNSET("sunset");

    companion object {
        fun fromStorageKey(raw: String?): AutomationSolarEvent? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

enum class AutomationSolarOffsetDirection(val storageKey: String) {
    BEFORE("before"),
    AFTER("after");

    companion object {
        fun fromStorageKey(raw: String?): AutomationSolarOffsetDirection? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

data class AutomationSolarInstant(
    val event: AutomationSolarEvent = AutomationSolarEvent.SUNSET,
    val offsetMinutes: Int = 0,
    val offsetDirection: AutomationSolarOffsetDirection = AutomationSolarOffsetDirection.AFTER,
) {
    fun signedOffsetMinutes(): Int = when (offsetDirection) {
        AutomationSolarOffsetDirection.BEFORE -> -offsetMinutes
        AutomationSolarOffsetDirection.AFTER -> offsetMinutes
    }

    fun isValid(): Boolean = offsetMinutes in 0..AUTOMATION_SOLAR_MAX_OFFSET_MINUTES
}

enum class AutomationStartupBehavior(val storageKey: String) {
    INITIALIZE_ONLY("initialize_only"),
    FIRE_IF_MATCHING("fire_if_matching");

    companion object {
        fun fromStorageKey(raw: String?): AutomationStartupBehavior =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() } ?: INITIALIZE_ONLY
    }
}

enum class AutomationRunMode(val storageKey: String) {
    SINGLE("single"),
    RESTART("restart"),
    QUEUED("queued"),
    PARALLEL("parallel");

    companion object {
        fun fromStorageKey(raw: String?): AutomationRunMode =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() } ?: SINGLE
    }
}

sealed interface AutomationTrigger {
    val id: String

    data class SystemEvent(
        override val id: String = "1",
        val event: AutomationSystemEvent,
    ) : AutomationTrigger

    /**
     * Fired when the user taps an automation trigger widget tile whose trigger id equals
     * [triggerId]. Fires regardless of the tile active/inactive state.
     */
    data class WidgetPressed(
        override val id: String = "1",
        val triggerId: String,
    ) : AutomationTrigger

    /**
     * Fired by OEM A9 mbCAN hardkey events (steering wheel keys, door buttons).
     * Verified [keyCode] values are listed in docs/MBCAN_VHAL_PARAMETERS_RU.md and
     * [vad.dashing.tbox.mbcan.KeyPressDiagnosticFormat.mbCanKeyName]; the backend is
     * available only when the head unit runs the Android 9 mbCAN CAN stack.
     */
    data class HardKey(
        override val id: String = "1",
        val keyCode: Int,
        val keyStatus: AutomationHardKeyStatus = AutomationHardKeyStatus.PRESSED,
    ) : AutomationTrigger

    /**
     * Fired by ESP companion `bleBtn` (Shelly Blu Button 1 / RC Button 4 / BTHome).
     * [btn] is 1…4 (Button 1 uses only 1); [act] is press / double / triple / long / hold.
     */
    data class EspBleBtn(
        override val id: String = "1",
        val btn: Int = 1,
        val act: AutomationEspBleBtnAction = AutomationEspBleBtnAction.PRESS,
    ) : AutomationTrigger

    data class Interval(
        override val id: String = "1",
        val intervalMillis: Long = AUTOMATION_DEFAULT_INTERVAL_MS,
    ) : AutomationTrigger

    data class NumericThreshold(
        override val id: String = "1",
        val signal: AutomationSignalId,
        val source: AutomationSignalSource,
        val direction: AutomationThresholdDirection,
        val threshold: Double,
        /**
         * Value that re-arms an already fired trigger. Null means [threshold].
         * ABOVE triggers re-arm at `value <= resetThreshold`; BELOW at `value >= resetThreshold`.
         * Ignored when [rearmEnabled] is false.
         */
        val resetThreshold: Double? = null,
        /** When false, fire on each new numeric value while still matching (no hysteresis). */
        val rearmEnabled: Boolean = true,
        val holdMillis: Long = AUTOMATION_DEFAULT_HOLD_MS,
        val startupBehavior: AutomationStartupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
    ) : AutomationTrigger

    data class StateEquals(
        override val id: String = "1",
        val signal: AutomationSignalId,
        val source: AutomationSignalSource,
        val expectedState: String,
        val holdMillis: Long = AUTOMATION_DEFAULT_HOLD_MS,
        val startupBehavior: AutomationStartupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
    ) : AutomationTrigger

    data class Geofence(
        override val id: String = "1",
        val queryText: String = "",
        val latitude: Double = Double.NaN,
        val longitude: Double = Double.NaN,
        val direction: AutomationGeofenceDirection = AutomationGeofenceDirection.ENTER,
        val zoneRadiusMeters: Double = AUTOMATION_GEOFENCE_DEFAULT_ZONE_RADIUS_M,
        val rearmRadiusMeters: Double = AUTOMATION_GEOFENCE_DEFAULT_ZONE_RADIUS_M +
            AUTOMATION_GEOFENCE_RADIUS_GAP_M,
        val holdMillis: Long = AUTOMATION_DEFAULT_HOLD_MS,
        val startupBehavior: AutomationStartupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
    ) : AutomationTrigger

    data class Time(
        override val id: String = "1",
        val at: AutomationTimeOfDay = AutomationTimeOfDay.DEFAULT,
        val weekdays: Set<AutomationWeekday> = emptySet(),
        val startupBehavior: AutomationStartupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
    ) : AutomationTrigger

    data class Solar(
        override val id: String = "1",
        val event: AutomationSolarEvent = AutomationSolarEvent.SUNSET,
        val offsetMinutes: Int = 0,
        val offsetDirection: AutomationSolarOffsetDirection = AutomationSolarOffsetDirection.AFTER,
        val weekdays: Set<AutomationWeekday> = emptySet(),
        val startupBehavior: AutomationStartupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
    ) : AutomationTrigger
}

sealed interface AutomationCondition {
    data object Always : AutomationCondition

    data class Numeric(
        val signal: AutomationSignalId,
        val source: AutomationSignalSource,
        val comparison: AutomationComparison,
        val expectedValue: Double,
    ) : AutomationCondition

    data class State(
        val signal: AutomationSignalId,
        val source: AutomationSignalSource,
        val expectedState: String,
    ) : AutomationCondition

    data class TriggeredBy(
        val triggerIds: Set<String>,
    ) : AutomationCondition

    data class All(
        val conditions: List<AutomationCondition>,
    ) : AutomationCondition

    data class Any(
        val conditions: List<AutomationCondition>,
    ) : AutomationCondition

    data class Not(
        val condition: AutomationCondition,
    ) : AutomationCondition

    data class Time(
        val after: AutomationTimeOfDay? = null,
        val before: AutomationTimeOfDay? = null,
        val weekdays: Set<AutomationWeekday> = emptySet(),
    ) : AutomationCondition

    data class Solar(
        val after: AutomationSolarInstant? = null,
        val before: AutomationSolarInstant? = null,
        val weekdays: Set<AutomationWeekday> = emptySet(),
    ) : AutomationCondition

    data class Geofence(
        val queryText: String = "",
        val latitude: Double = Double.NaN,
        val longitude: Double = Double.NaN,
        val presence: AutomationGeofencePresence = AutomationGeofencePresence.INSIDE,
        val zoneRadiusMeters: Double = AUTOMATION_GEOFENCE_DEFAULT_ZONE_RADIUS_M,
    ) : AutomationCondition

    data class UiState(
        val state: AutomationUiState,
    ) : AutomationCondition

    data class TriggerWidget(
        val triggerId: String,
        val active: Boolean = true,
    ) : AutomationCondition
}

enum class AutomationCanBus(val storageKey: String) {
    VEHICLE("vehicle"),
    AUDIO("audio");

    companion object {
        fun fromStorageKey(raw: String?): AutomationCanBus =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() } ?: VEHICLE
    }
}

enum class AutomationCanOperation(val storageKey: String) {
    SET("set"),
    TOGGLE("toggle"),
    TRUNK_PULSE("trunk_pulse");

    companion object {
        fun fromStorageKey(raw: String?): AutomationCanOperation =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() } ?: SET
    }
}

enum class AutomationMainScreenTarget(val storageKey: String) {
    FULLSCREEN("fullscreen"),
    CURRENT_WINDOW("current_window");

    companion object {
        fun fromStorageKey(raw: String?): AutomationMainScreenTarget =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() } ?: FULLSCREEN
    }
}

enum class AutomationBuiltinActionType(val storageKey: String) {
    OPEN_MENU("open_menu"),
    FINISH_AND_START_TRIP("finish_and_start_trip"),
    RESET_MOTOR_HOURS("reset_motor_hours"),
    RESTART_TBOX("restart_tbox"),
    TOGGLE_APP_DAY_NIGHT_THEME("toggle_app_day_night_theme"),
    ENABLE_HEAD_UNIT_AUTO_THEME("enable_head_unit_auto_theme"),
    /** Explicit day/night like Car Settings: stringValue `light` / `dark` / `auto`. */
    SET_HU_DAY_NIGHT_THEME("set_hu_day_night_theme"),
    TOGGLE_MIRROR_ADJUST_MODE("toggle_mirror_adjust_mode"),
    TOGGLE_HIDE_FLOATING_PANELS("toggle_hide_floating_panels"),
    TOGGLE_FLOATING_PANELS_ENABLED("toggle_floating_panels_enabled"),
    ESP_RELAY_SET("esp_relay_set"),
    ESP_RELAY_TOGGLE("esp_relay_toggle"),
    ESP_RELAY_PULSE("esp_relay_pulse"),
    MEDIA_PREVIOUS("media_previous"),
    MEDIA_PLAY_PAUSE("media_play_pause"),
    MEDIA_PLAY("media_play"),
    MEDIA_NEXT("media_next"),
    MEDIA_TOGGLE_LIKE("media_toggle_like"),
    SET_MEDIA_VOLUME("set_media_volume"),
    SET_PHONE_VOLUME("set_phone_volume"),
    SET_NAVI_VOLUME("set_navi_volume"),
    SET_VOICE_VOLUME("set_voice_volume"),
    /** stringValue: `only` / `assist` / `off` (shared UI 1/2/3). */
    SET_HEADREST_SPEAKER("set_headrest_speaker"),
    CYCLE_MOCK_LOCATION_MODE("cycle_mock_location_mode"),
    GNSS_MODULE_REBOOT("gnss_module_reboot"),
    SET_SIMULATED_LOCATION_SOURCE_LOSS("set_simulated_location_source_loss"),
    SET_GEO_DEBUG_LOG("set_geo_debug_log"),
    WIFI_SET_ENABLED("wifi_set_enabled"),
    WIFI_CONNECT("wifi_connect"),
    WIFI_DISCONNECT("wifi_disconnect"),
    WIFI_MODEM_SET_DATA("wifi_modem_set_data"),
    WIFI_MODEM_REBOOT("wifi_modem_reboot"),
    SET_HU_SCREEN_BRIGHTNESS("set_hu_screen_brightness"),
    SET_HU_SCREEN_AUTO_BRIGHTNESS("set_hu_screen_auto_brightness"),
    SHOW_TOAST("show_toast"),
    SHOW_ALERT("show_alert"),
    SET_AUTOMATION_TRIGGER_WIDGET("set_automation_trigger_widget");

    companion object {
        fun fromStorageKey(raw: String?): AutomationBuiltinActionType? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

sealed interface AutomationAction {
    data class Delay(
        val durationMillis: Long,
    ) : AutomationAction

    data class IfThenElse(
        val condition: AutomationCondition,
        val thenActions: List<AutomationAction>,
        val elseActions: List<AutomationAction> = emptyList(),
    ) : AutomationAction

    /**
     * The persisted property id is accepted only when [AutomationCanCatalog] exposes it.
     * Users never enter arbitrary ids; codec validation blocks hand-edited unsafe values.
     *
     * [value] is the legacy / A9-canonical raw int. Optional [valueKey] (`on`/`off`,
     * `close`/`open`/`vent`, …) makes binary and window actions portable across A9/A10;
     * see [AutomationCanValueCodec].
     */
    data class CanCommand(
        val bus: AutomationCanBus = AutomationCanBus.VEHICLE,
        val propertyId: Int,
        val operation: AutomationCanOperation,
        val value: Int = 0,
        val valueKey: String? = null,
    ) : AutomationAction

    data class LaunchApplication(
        val packageName: String,
        val launchMode: AppLauncherLaunchMode = AppLauncherLaunchMode.DEFAULT,
        val freeformSide: FreeformLaunchSide = FreeformLaunchSide.DEFAULT,
        val freeformPercent: Int = FreeformLaunchBounds.DEFAULT_PERCENT,
        val freeformOverlayPage: Int? = null,
        val freeformOverlayCrop: Boolean = false,
        /** Target display for [AppLauncherLaunchMode.VIRTUAL_DISPLAY]; ignored otherwise. */
        val virtualDisplayId: Int? = null,
    ) : AutomationAction

    data class OpenMainScreen(
        val page: Int,
        val target: AutomationMainScreenTarget = AutomationMainScreenTarget.FULLSCREEN,
    ) : AutomationAction

    data class HttpRequest(
        val yaml: String = DEFAULT_HTTP_REQUEST_WIDGET_YAML,
        val openBrowser: Boolean = false,
    ) : AutomationAction

    /**
     * Parameters are interpreted by [type]. They keep the persisted schema stable while allowing
     * the complete existing user-action catalog to share one executor.
     */
    data class Builtin(
        val type: AutomationBuiltinActionType,
        val intValue: Int = 0,
        val stringValue: String = "",
        val boolValue: Boolean = false,
    ) : AutomationAction
}

/**
 * Execution mode of the [AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET] action.
 * Persisted through the generic builtin slots: `intValue == AUTOMATION_TRIGGER_WIDGET_TOGGLE_INT`
 * means [TOGGLE]; any other `intValue` falls back to [AutomationAction.Builtin.boolValue].
 */
enum class AutomationTriggerWidgetCommand {
    ACTIVATE,
    DEACTIVATE,
    TOGGLE,
}

fun builtinActionTriggerWidgetCommand(action: AutomationAction.Builtin): AutomationTriggerWidgetCommand =
    when {
        action.intValue == AUTOMATION_TRIGGER_WIDGET_TOGGLE_INT -> AutomationTriggerWidgetCommand.TOGGLE
        action.boolValue -> AutomationTriggerWidgetCommand.ACTIVATE
        else -> AutomationTriggerWidgetCommand.DEACTIVATE
    }

data class AutomationDefinition(
    val id: String = newAutomationNodeId(),
    val name: String,
    val description: String = "",
    val enabled: Boolean = false,
    val triggers: List<AutomationTrigger>,
    /** Top-level conditions are combined with AND. */
    val conditions: List<AutomationCondition> = emptyList(),
    val actions: List<AutomationAction>,
    val runMode: AutomationRunMode = AutomationRunMode.SINGLE,
    val maxRuns: Int = 1,
    /**
     * One timeout for the whole top-level AND group of [conditions].
     * `0` — if conditions are false at trigger time, skip immediately.
     * `> 0` — wait until trigger still matches **and** all conditions are true, or until timeout.
     */
    val conditionWaitMillis: Long = AUTOMATION_DEFAULT_CONDITION_WAIT_MS,
) {
    companion object {
        fun newDraft(): AutomationDefinition =
            AutomationDefinition(
                name = "",
                triggers = listOf(
                    AutomationTrigger.SystemEvent(
                        id = "1",
                        event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                    ),
                ),
                actions = emptyList(),
            )
    }

    fun duplicated(): AutomationDefinition = copy(
        id = newAutomationNodeId(),
        name = duplicatedAutomationName(name),
        enabled = false,
    )
}

internal fun duplicatedAutomationName(name: String): String {
    val base = name.trim().ifEmpty { "Автоматизация" }
    return "$base (копия)"
}

data class AutomationDocument(
    val formatVersion: Int = AUTOMATION_FORMAT_VERSION,
    val automations: List<AutomationDefinition> = emptyList(),
)

data class AutomationSignalKey(
    val signal: AutomationSignalId,
    val source: AutomationSignalSource,
)

sealed interface AutomationSignalValue {
    data class Number(val value: Double) : AutomationSignalValue
    data class State(val value: String) : AutomationSignalValue
    data class Position(val latitude: Double, val longitude: Double) : AutomationSignalValue
    data object Unavailable : AutomationSignalValue
}

internal val AUTOMATION_GEO_DISPLAY_KEY = AutomationSignalKey(
    signal = AutomationSignalId.GEO_POSITION,
    source = AutomationSignalSource.APP,
)

fun automationGeofenceRearmRadius(
    direction: AutomationGeofenceDirection,
    zoneRadiusMeters: Double,
    currentRearmRadiusMeters: Double,
): Double {
    if (!zoneRadiusMeters.isFinite() || zoneRadiusMeters < 0.0) return currentRearmRadiusMeters
    val rearm = currentRearmRadiusMeters
    return when (direction) {
        AutomationGeofenceDirection.ENTER ->
            if (rearm.isFinite() && rearm > zoneRadiusMeters) {
                rearm
            } else {
                zoneRadiusMeters + AUTOMATION_GEOFENCE_RADIUS_GAP_M
            }

        AutomationGeofenceDirection.EXIT ->
            if (rearm.isFinite() && rearm >= 0.0 && rearm < zoneRadiusMeters) {
                rearm
            } else {
                (zoneRadiusMeters - AUTOMATION_GEOFENCE_RADIUS_GAP_M).coerceAtLeast(0.0)
            }
    }
}

data class AutomationSignalSample(
    val key: AutomationSignalKey,
    val value: AutomationSignalValue,
    val observedAtElapsedMillis: Long,
)

data class AutomationTriggerContext(
    val automationId: String,
    val triggerId: String,
    val firedAtEpochMillis: Long,
    val oldValue: AutomationSignalValue? = null,
    val newValue: AutomationSignalValue? = null,
)

internal fun newAutomationNodeId(): String = UUID.randomUUID().toString()

internal fun nextAutomationTriggerId(existingIds: Collection<String>): String {
    val used = existingIds.mapNotNull { raw ->
        raw.trim().toIntOrNull()?.takeIf { it > 0 }
    }.toSet()
    var candidate = 1
    while (candidate in used) {
        candidate++
    }
    return candidate.toString()
}
