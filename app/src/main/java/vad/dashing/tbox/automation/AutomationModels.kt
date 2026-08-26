package vad.dashing.tbox.automation

import java.util.UUID
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.DEFAULT_HTTP_REQUEST_WIDGET_YAML
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide

const val AUTOMATION_FORMAT_VERSION = 1
const val AUTOMATION_DEFAULT_HOLD_MS = 0L
const val AUTOMATION_MAX_HOLD_MS = 24L * 60L * 60L * 1_000L
const val AUTOMATION_MAX_DELAY_MS = 24L * 60L * 60L * 1_000L
const val AUTOMATION_MAX_CONDITION_DEPTH = 6
const val AUTOMATION_MAX_ACTION_DEPTH = 6
const val AUTOMATION_MAX_ACTION_COUNT = 200

enum class AutomationSignalSource(val storageKey: String) {
    TBOX("tbox"),
    HEAD_UNIT("head_unit");

    companion object {
        fun fromStorageKey(raw: String?): AutomationSignalSource? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }
    }
}

enum class AutomationSignalValueType {
    NUMBER,
    STATE,
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
    CURRENT_GEAR("current_gear"),
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
    DRIVE_MODE("drive_mode"),
    HEADLIGHT_MODE("headlight_mode"),
    REVERSE_GEAR("reverse_gear", AutomationSignalValueType.STATE),
    FRONT_LEFT_SEAT_MODE("front_left_seat_mode", AutomationSignalValueType.STATE),
    FRONT_RIGHT_SEAT_MODE("front_right_seat_mode", AutomationSignalValueType.STATE),
    REAR_LEFT_SEAT_MODE("rear_left_seat_mode", AutomationSignalValueType.STATE),
    REAR_RIGHT_SEAT_MODE("rear_right_seat_mode", AutomationSignalValueType.STATE);

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
        override val id: String = newAutomationNodeId(),
        val event: AutomationSystemEvent,
    ) : AutomationTrigger

    data class NumericThreshold(
        override val id: String = newAutomationNodeId(),
        val signal: AutomationSignalId,
        val source: AutomationSignalSource,
        val direction: AutomationThresholdDirection,
        val threshold: Double,
        /**
         * Value that re-arms an already fired trigger. Null means [threshold].
         * ABOVE triggers re-arm at `value <= resetThreshold`; BELOW at `value >= resetThreshold`.
         */
        val resetThreshold: Double? = null,
        val holdMillis: Long = AUTOMATION_DEFAULT_HOLD_MS,
        val startupBehavior: AutomationStartupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
    ) : AutomationTrigger

    data class StateEquals(
        override val id: String = newAutomationNodeId(),
        val signal: AutomationSignalId,
        val source: AutomationSignalSource,
        val expectedState: String,
        val holdMillis: Long = AUTOMATION_DEFAULT_HOLD_MS,
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
    CYCLE_MOCK_LOCATION_MODE("cycle_mock_location_mode"),
    GNSS_MODULE_REBOOT("gnss_module_reboot"),
    SET_SIMULATED_LOCATION_SOURCE_LOSS("set_simulated_location_source_loss"),
    SET_GEO_DEBUG_LOG("set_geo_debug_log");

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
     */
    data class CanCommand(
        val bus: AutomationCanBus = AutomationCanBus.VEHICLE,
        val propertyId: Int,
        val operation: AutomationCanOperation,
        val value: Int = 0,
    ) : AutomationAction

    data class LaunchApplication(
        val packageName: String,
        val launchMode: AppLauncherLaunchMode = AppLauncherLaunchMode.DEFAULT,
        val freeformSide: FreeformLaunchSide = FreeformLaunchSide.DEFAULT,
        val freeformPercent: Int = FreeformLaunchBounds.DEFAULT_PERCENT,
        val freeformOverlayPage: Int? = null,
        val freeformOverlayCrop: Boolean = false,
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
) {
    companion object {
        fun newDraft(): AutomationDefinition =
            AutomationDefinition(
                name = "",
                triggers = listOf(
                    AutomationTrigger.SystemEvent(
                        event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                    ),
                ),
                actions = emptyList(),
            )
    }
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
    data object Unavailable : AutomationSignalValue
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
