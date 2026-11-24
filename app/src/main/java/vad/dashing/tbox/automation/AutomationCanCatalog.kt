package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

import vad.dashing.tbox.DRIVE_MODE_WIDGET_OPTIONS
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.isSpeedLimiterHiddenFromAutomationPicker
import vad.dashing.tbox.mbcan.CarSettingsAdasDomain
import vad.dashing.tbox.mbcan.CarSettingsAudioDomain
import vad.dashing.tbox.mbcan.CarSettingsHudDomain
import vad.dashing.tbox.mbcan.CarSettingsLocksLightsDomain
import vad.dashing.tbox.mbcan.AccTimeGap
import vad.dashing.tbox.mbcan.FcwSensitivity
import vad.dashing.tbox.mbcan.FirmwareVehicleJsonMapper
import vad.dashing.tbox.mbcan.FollowMeHomeMode
import vad.dashing.tbox.mbcan.HvacClimateDomain
import vad.dashing.tbox.mbcan.LdwSensitivity
import vad.dashing.tbox.mbcan.BodyComfortWrite
import vad.dashing.tbox.mbcan.MbCanAudioCommandRegistry
import vad.dashing.tbox.mbcan.MbCanCommandPolicy
import vad.dashing.tbox.mbcan.MbCanCommandRegistry
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.SlaSpeedLimitDomain

data class AutomationCanCatalogEntry(
    val bus: AutomationCanBus,
    val propertyId: Int,
    val label: String,
    val policy: MbCanCommandPolicy,
    val supportedModes: Set<HeadUnitCanMode>,
    /** Picker group header for the merged «Действия» list. */
    val group: AutomationActionGroup,
) {
    val allowedOperations: Set<AutomationCanOperation>
        get() = when {
            bus == AutomationCanBus.VEHICLE &&
                propertyId == MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL ->
                setOf(AutomationCanOperation.TRUNK_PULSE)

            policy is MbCanCommandPolicy.ToggleBinary ||
                policy is MbCanCommandPolicy.ToggleHvacFrontDefrost ->
                setOf(AutomationCanOperation.SET, AutomationCanOperation.TOGGLE)

            else -> setOf(AutomationCanOperation.SET)
        }

    val allowedValues: List<Int>
        get() = when (policy) {
            is MbCanCommandPolicy.ToggleBinary ->
                listOf(policy.offValue, policy.onValue).distinct()

            is MbCanCommandPolicy.ToggleHvacFrontDefrost -> listOf(
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE,
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT,
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT,
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST,
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT,
            )

            is MbCanCommandPolicy.SetExact -> policy.allowedValues.sorted()
            is MbCanCommandPolicy.SetRange -> policy.allowedValues.toList()
            is MbCanCommandPolicy.SetWindowPosition ->
                (BodyComfortWrite.WINDOW_A9_PERCENT_STEPS + BodyComfortWrite.WINDOW_A10_COMMANDS)
                    .distinct()
                    .sorted()
            is MbCanCommandPolicy.SetAnyInt -> emptyList()
        }

    val defaultValue: Int
        get() = when (policy) {
            is MbCanCommandPolicy.ToggleBinary -> policy.onValue
            else -> allowedValues.firstOrNull() ?: 0
        }

    fun allowedValuesFor(mode: HeadUnitCanMode): List<Int> =
        if (policy is MbCanCommandPolicy.SetWindowPosition) {
            BodyComfortWrite.windowValues(mode)
        } else {
            allowedValues
        }

    fun defaultValueFor(mode: HeadUnitCanMode): Int =
        if (policy is MbCanCommandPolicy.SetWindowPosition) {
            allowedValuesFor(mode).first()
        } else {
            defaultValue
        }

    fun isActionAllowed(action: AutomationAction.CanCommand): Boolean {
        if (action.bus != bus || action.propertyId != propertyId) return false
        if (action.operation !in allowedOperations) return false
        return when (action.operation) {
            AutomationCanOperation.TOGGLE -> true
            AutomationCanOperation.TRUNK_PULSE,
            AutomationCanOperation.SET,
            -> AutomationCanValueCodec.isResolvable(action, mode = null)
        }
    }

    fun supports(mode: HeadUnitCanMode): Boolean = mode in supportedModes

    fun valueLabel(value: Int, mode: HeadUnitCanMode? = null): String =
        AutomationCanCatalog.valueLabel(this, value, mode)
}

/**
 * User-facing allowlist for unattended CAN actions.
 *
 * Raw `SetAnyInt`, HU reboot, and cruise-control key pulses are intentionally absent. The liftgate
 * is available only as the staff pulse (`TrunkPulse` 1/2), matching the dashboard widget: no extra
 * software speed or PRND gate. Speed-limiter probes (253/254) stay unpublished in pickers
 * ([vad.dashing.tbox.SPEED_LIMITER_UI_HIDDEN]); they are also excluded here as `SetAnyInt`.
 */
object AutomationCanCatalog {
    private val allowedVehiclePropertyIds: Set<Int> = setOf(
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
        MbCanKnownVehiclePropertyId.AVH_SWITCH,
        MbCanKnownVehiclePropertyId.HDC_SWITCH,
        MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH,
        MbCanKnownVehiclePropertyId.LIGHTCONTROL,
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT,
        MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK,
        MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK,
        MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY,
        MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE,
        MbCanKnownVehiclePropertyId.DEFENCES_PROMPT,
        MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY,
        MbCanKnownVehiclePropertyId.REAR_WIPER,
        MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW,
        MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST,
        MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT,
        MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION,
        MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH,
        MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION,
        MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING,
        MbCanKnownVehiclePropertyId.FCW_SWITCH,
        MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH,
        MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING,
        MbCanKnownVehiclePropertyId.FCW_SENSITIVITY,
        MbCanKnownVehiclePropertyId.ACC_TIME_GAP_SET,
        MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL,
        MbCanKnownVehiclePropertyId.HMA_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_CUSTOM,
        MbCanKnownVehiclePropertyId.HVAC_AC_MAX,
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
        MbCanKnownVehiclePropertyId.HVAC_POWER,
        MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY,
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE,
        MbCanKnownVehiclePropertyId.HVAC_AQS,
        MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH,
        MbCanKnownVehiclePropertyId.FRAGRANCE_SMELL,
        MbCanKnownVehiclePropertyId.FRAGRANCE_CONCENTRATION,
        MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH,
        MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED,
        MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH,
        MbCanKnownVehiclePropertyId.HUD_SWITCH,
        MbCanKnownVehiclePropertyId.HUD_HEIGHT,
        MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS,
        MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE,
        MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS,
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE,
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL,
        MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET,
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION,
        MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH,
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_STEERING_MODE,
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE,
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET,
        MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH,
        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
        MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT,
        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED,
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF,
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH,
        MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
        MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH,
        MbCanKnownVehiclePropertyId.SUNSHADE_POS,
        MbCanKnownVehiclePropertyId.SUNROOF_CONTROL,
        MbCanKnownVehiclePropertyId.WINDOW_POS,
        MbCanKnownVehiclePropertyId.WINDOW_FL_POS,
        MbCanKnownVehiclePropertyId.WINDOW_FR_POS,
        MbCanKnownVehiclePropertyId.WINDOW_RL_POS,
        MbCanKnownVehiclePropertyId.WINDOW_RR_POS,
    )

    val entries: List<AutomationCanCatalogEntry> = buildList {
        MbCanCommandRegistry.all().forEach { spec ->
            if (spec.propertyId !in allowedVehiclePropertyIds) return@forEach
            if (spec.policy is MbCanCommandPolicy.SetAnyInt) return@forEach
            val supportedModes = buildSet {
                add(HeadUnitCanMode.Android9MbCan)
                if (FirmwareVehicleJsonMapper.hasExplicitWritePropertyId(spec.propertyId)) {
                    add(HeadUnitCanMode.Android10Vhal)
                }
            }
            add(
                AutomationCanCatalogEntry(
                    bus = AutomationCanBus.VEHICLE,
                    propertyId = spec.propertyId,
                    label = vehicleLabel(spec.propertyId),
                    policy = spec.policy,
                    supportedModes = supportedModes,
                    group = canActionGroup(AutomationCanBus.VEHICLE, spec.propertyId),
                ),
            )
        }
        MbCanAudioCommandRegistry.all().forEach { spec ->
            if (spec.policy is MbCanCommandPolicy.SetAnyInt) return@forEach
            add(
                AutomationCanCatalogEntry(
                    bus = AutomationCanBus.AUDIO,
                    propertyId = spec.propertyId,
                    label = audioLabel(spec.propertyId),
                    policy = spec.policy,
                    supportedModes = setOf(HeadUnitCanMode.Android9MbCan),
                    group = canActionGroup(AutomationCanBus.AUDIO, spec.propertyId),
                ),
            )
        }
    }.sortedWith(compareBy({ it.bus.ordinal }, { it.label.lowercase() }, { it.propertyId }))

    private val entriesByKey: Map<Pair<AutomationCanBus, Int>, AutomationCanCatalogEntry> =
        entries.associateBy { it.bus to it.propertyId }

    fun get(bus: AutomationCanBus, propertyId: Int): AutomationCanCatalogEntry? =
        entriesByKey[bus to propertyId]

    /** Picker list: omits speed-limiter CAN ids while [vad.dashing.tbox.SPEED_LIMITER_UI_HIDDEN]. */
    fun pickerEntries(): List<AutomationCanCatalogEntry> =
        entries.filterNot { isSpeedLimiterHiddenFromAutomationPicker(it.propertyId) }

    fun isAllowed(action: AutomationAction.CanCommand): Boolean =
        get(action.bus, action.propertyId)?.isActionAllowed(action) == true

    fun valueLabel(
        entry: AutomationCanCatalogEntry,
        value: Int,
        mode: HeadUnitCanMode? = null,
    ): String {
        val policy = entry.policy
        if (policy is MbCanCommandPolicy.ToggleBinary) {
            return when (value) {
                policy.offValue -> ruEn("Выключить", "Off")
                policy.onValue -> ruEn("Включить", "On")
                else -> value.toString()
            }
        }
        return when (entry.bus) {
            AutomationCanBus.AUDIO -> audioValueLabel(entry.propertyId, value)
            AutomationCanBus.VEHICLE -> vehicleValueLabel(entry.propertyId, value, mode)
        }
    }

    private fun vehicleValueLabel(
        propertyId: Int,
        value: Int,
        mode: HeadUnitCanMode?,
    ): String = when (propertyId) {
        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
        -> frontSeatValueLabel(value)

        MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH,
        -> rearSeatValueLabel(value)

        MbCanKnownVehiclePropertyId.LIGHTCONTROL ->
            HeadlightMode.fromRaw(value)?.widgetLabel ?: value.toString()

        MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY -> when (value) {
            FollowMeHomeMode.Sec30.mbCanWriteValue -> ruEn("30 с", "30 s")
            FollowMeHomeMode.Sec60.mbCanWriteValue -> ruEn("60 с", "60 s")
            FollowMeHomeMode.Off.mbCanWriteValue -> ruEn("Выкл", "Off")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE -> when (value) {
            1 -> ruEn("Водитель", "Driver")
            2 -> ruEn("Все", "All")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.DEFENCES_PROMPT -> when (value) {
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_LIGHT -> ruEn("Свет", "Light")
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_HORN -> ruEn("Сигнал", "Horn")
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_LIGHT_HORN -> ruEn("Свет и сигнал", "Light and horn")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY,
        MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST,
        -> ruEn("Уровень $value", "Level $value")

        MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT ->
            CarSettingsLocksLightsDomain.turnFlashCountBlinks(value)?.let { ruEn("$it миганий", "$it flashes") }
                ?: value.toString()

        MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION -> when (value) {
            MbCanKnownVehiclePropertyId.LAS_MODE_LDW -> "LDW"
            MbCanKnownVehiclePropertyId.LAS_MODE_LKA -> "LKA"
            MbCanKnownVehiclePropertyId.LAS_MODE_OFF -> ruEn("Выкл", "Off")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.FCW_SENSITIVITY -> when (CarSettingsAdasDomain.decodeFcwSensitivityMbCan(value)) {
            FcwSensitivity.Far -> ruEn("Дальняя", "Far")
            FcwSensitivity.Standard -> ruEn("Стандарт", "Standard")
            FcwSensitivity.Near -> ruEn("Ближняя", "Near")
            null -> value.toString()
        }

        MbCanKnownVehiclePropertyId.ACC_TIME_GAP_SET -> when (CarSettingsAdasDomain.decodeAccTimeGapRequest(value)) {
            AccTimeGap.Near -> ruEn("Ближняя", "Near")
            AccTimeGap.Medium -> ruEn("Средняя", "Medium")
            AccTimeGap.Far -> ruEn("Дальняя", "Far")
            null -> value.toString()
        }

        MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL -> when (CarSettingsAdasDomain.decodeLdwSensitivityMbCan(value)) {
            LdwSensitivity.High -> ruEn("Высокая", "High")
            LdwSensitivity.Low -> ruEn("Низкая", "Low")
            null -> value.toString()
        }

        MbCanKnownVehiclePropertyId.HVAC_CUSTOM -> when (value) {
            MbCanKnownVehiclePropertyId.HVAC_CUSTOM_ECO -> "ECO"
            MbCanKnownVehiclePropertyId.HVAC_CUSTOM_COMFORT -> ruEn("Комфорт", "Comfort")
            MbCanKnownVehiclePropertyId.HVAC_CUSTOM_STRONG -> ruEn("Сильный", "Strong")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.FRAGRANCE_SMELL -> when (value) {
            1 -> "Meteor"
            2 -> "Boss"
            3 -> "Tea"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.FRAGRANCE_CONCENTRATION -> when (value) {
            1 -> ruEn("Низкая", "Low")
            2 -> ruEn("Средняя", "Medium")
            3 -> ruEn("Высокая", "High")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE -> when (value) {
            CarSettingsHudDomain.HUD_MODE_STANDARD -> ruEn("Стандарт", "Standard")
            CarSettingsHudDomain.HUD_MODE_SNOW -> ruEn("Снег", "Snow")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.HUD_HEIGHT,
        MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS,
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL,
        -> ruEn("Уровень $value", "Level $value")

        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE -> when (value) {
            0 -> ruEn("Авто", "Auto")
            1 -> ruEn("Вручную", "Manual")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET ->
            CarSettingsHudDomain.decodeOverspeedKmh(value)?.let { ruEn("$it км/ч", "$it km/h") } ?: value.toString()

        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION -> when (value) {
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE -> ruEn("Лицо", "Face")
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT -> ruEn("Ноги", "Feet")
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT -> ruEn("Лицо и ноги", "Face and feet")
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST -> ruEn("Лобовое", "Windshield")
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT -> ruEn("Лобовое и ноги", "Windshield and feet")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE,
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_STEERING_MODE,
        -> when (value) {
            1 -> "NOR"
            2 -> "ECO"
            3 -> "SPT"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET,
        -> DRIVE_MODE_WIDGET_OPTIONS.firstOrNull {
            it.propertyId == propertyId && it.propertyValue == value
        }?.widgetLabel ?: value.toString()

        MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH -> when (value) {
            SlaSpeedLimitDomain.SLA_SWITCH_OFF -> ruEn("Выключить", "Off")
            SlaSpeedLimitDomain.SLA_SWITCH_ON -> ruEn("Включить", "On")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.VEHICLE_PM25_DISPLAY_TOGGLE -> when (value) {
            1 -> ruEn("Салон", "Cabin")
            2 -> ruEn("Снаружи", "Outside")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.VEHICLE_UV_LAMP_REQ -> when (value) {
            1 -> ruEn("Выкл", "Off")
            2 -> ruEn("Вкл", "On")
            3 -> ruEn("Авто", "Auto")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.VEHICLE_STERILIZE_STRENGTH_REQ -> when (value) {
            1 -> ruEn("Низкая", "Low")
            2 -> ruEn("Средняя", "Medium")
            3 -> ruEn("Высокая", "High")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT,
        -> HvacClimateDomain.mbCanTempRawToCelsius(value)?.let(::formatCelsius) ?: value.toString()

        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED -> ruEn("Скорость $value", "Speed $value")

        MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL -> when (value) {
            1 -> ruEn("Открыть", "Open")
            2 -> ruEn("Закрыть", "Close")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH -> when (value) {
            1 -> ruEn("Сложить", "Fold")
            2 -> ruEn("Разложить", "Unfold")
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.SUNSHADE_POS -> when (value) {
            BodyComfortWrite.SHADE_VALUES.first -> ruEn("Закрыто (0%)", "Closed (0%)")
            BodyComfortWrite.SHADE_VALUES.last -> ruEn("Открыто (100%)", "Open (100%)")
            in 2..10 -> "${(value - 1) * 10}%"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.SUNROOF_CONTROL -> when (value) {
            BodyComfortWrite.SHADE_VALUES.first -> ruEn("Закрыто (0%)", "Closed (0%)")
            BodyComfortWrite.SHADE_VALUES.last -> ruEn("Открыто (100%)", "Open (100%)")
            MbCanKnownVehiclePropertyId.SUNROOF_TILT -> ruEn("Откинуть", "Tilt")
            in 2..10 -> "${(value - 1) * 10}%"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.WINDOW_POS,
        MbCanKnownVehiclePropertyId.WINDOW_FL_POS,
        MbCanKnownVehiclePropertyId.WINDOW_FR_POS,
        MbCanKnownVehiclePropertyId.WINDOW_RL_POS,
        MbCanKnownVehiclePropertyId.WINDOW_RR_POS,
        -> if (mode == HeadUnitCanMode.Android10Vhal) {
            when (value) {
                MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE -> ruEn("Закрыть", "Close")
                MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN -> ruEn("Открыть", "Open")
                MbCanKnownVehiclePropertyId.WINDOW_A10_VENT -> ruEn("Щель", "Vent")
                else -> value.toString()
            }
        } else {
            "$value %"
        }

        else -> value.toString()
    }

    private fun audioValueLabel(propertyId: Int, value: Int): String = when (propertyId) {
        MbCanKnownAudioPropertyId.VOLUME_SPEED -> when (value) {
            1 -> ruEn("Выкл", "Off")
            2 -> ruEn("Низкий", "Low")
            3 -> ruEn("Средний", "Medium")
            4 -> ruEn("Высокий", "High")
            else -> value.toString()
        }

        MbCanKnownAudioPropertyId.VOLUME_KEY -> when (value) {
            0 -> ruEn("Выкл", "Off")
            1 -> ruEn("Низкий", "Low")
            2 -> ruEn("Средний", "Medium")
            3 -> ruEn("Высокий", "High")
            else -> value.toString()
        }

        MbCanKnownAudioPropertyId.VOLUME_RADAR -> when (value) {
            1 -> ruEn("Низкий", "Low")
            2 -> ruEn("Средний", "Medium")
            3 -> ruEn("Высокий", "High")
            else -> value.toString()
        }

        MbCanKnownAudioPropertyId.EQ_MODE -> when (value) {
            CarSettingsAudioDomain.EQ_MODE_POP -> "Pop"
            CarSettingsAudioDomain.EQ_MODE_ROCK -> "Rock"
            CarSettingsAudioDomain.EQ_MODE_JAZZ -> "Jazz"
            CarSettingsAudioDomain.EQ_MODE_CLASSIC -> "Classic"
            CarSettingsAudioDomain.EQ_MODE_VOICE -> "Voice"
            CarSettingsAudioDomain.EQ_MODE_CUSTOM -> "Custom"
            else -> value.toString()
        }

        else -> value.toString()
    }

    private fun frontSeatValueLabel(value: Int): String = when (value) {
        1 -> ruEn("Выкл.", "Off.")
        2 -> ruEn("Подогрев 1", "Heat 1")
        3 -> ruEn("Подогрев 2", "Heat 2")
        4 -> ruEn("Подогрев 3", "Heat 3")
        5 -> ruEn("Вентиляция 1", "Ventilation 1")
        6 -> ruEn("Вентиляция 2", "Ventilation 2")
        7 -> ruEn("Вентиляция 3", "Ventilation 3")
        else -> value.toString()
    }

    private fun rearSeatValueLabel(value: Int): String = when (value) {
        1 -> ruEn("Выкл.", "Off.")
        2 -> ruEn("Подогрев 1", "Heat 1")
        3 -> ruEn("Подогрев 2", "Heat 2")
        4 -> ruEn("Подогрев 3", "Heat 3")
        else -> value.toString()
    }

    private fun formatCelsius(celsius: Float): String {
        val rounded = if (celsius % 1f == 0f) celsius.toInt().toString() else celsius.toString()
        return "$rounded °C"
    }

    private fun vehicleLabel(propertyId: Int): String = AutomationParameterLabels.vehicleLabel(propertyId)

    private fun audioLabel(propertyId: Int): String = AutomationParameterLabels.audioLabel(propertyId)
}
