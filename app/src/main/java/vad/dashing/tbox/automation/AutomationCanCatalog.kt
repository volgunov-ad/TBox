package vad.dashing.tbox.automation

import vad.dashing.tbox.DRIVE_MODE_WIDGET_OPTIONS
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.mbcan.CarSettingsAdasDomain
import vad.dashing.tbox.mbcan.CarSettingsAudioDomain
import vad.dashing.tbox.mbcan.CarSettingsHudDomain
import vad.dashing.tbox.mbcan.CarSettingsLocksLightsDomain
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
            AutomationCanOperation.TRUNK_PULSE -> action.value in setOf(1, 2)
            AutomationCanOperation.SET -> action.value in allowedValues
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
 * software speed or PRND gate.
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
                ),
            )
        }
    }.sortedWith(compareBy({ it.bus.ordinal }, { it.label.lowercase() }, { it.propertyId }))

    private val entriesByKey: Map<Pair<AutomationCanBus, Int>, AutomationCanCatalogEntry> =
        entries.associateBy { it.bus to it.propertyId }

    fun get(bus: AutomationCanBus, propertyId: Int): AutomationCanCatalogEntry? =
        entriesByKey[bus to propertyId]

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
                policy.offValue -> "Выключить"
                policy.onValue -> "Включить"
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
            FollowMeHomeMode.Sec30.mbCanWriteValue -> "30 с"
            FollowMeHomeMode.Sec60.mbCanWriteValue -> "60 с"
            FollowMeHomeMode.Off.mbCanWriteValue -> "Выкл"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE -> when (value) {
            1 -> "Водитель"
            2 -> "Все"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.DEFENCES_PROMPT -> when (value) {
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_LIGHT -> "Свет"
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_HORN -> "Сигнал"
            CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_LIGHT_HORN -> "Свет и сигнал"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY,
        MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST,
        -> "Уровень $value"

        MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT ->
            CarSettingsLocksLightsDomain.turnFlashCountBlinks(value)?.let { "$it миганий" }
                ?: value.toString()

        MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION -> when (value) {
            MbCanKnownVehiclePropertyId.LAS_MODE_LDW -> "LDW"
            MbCanKnownVehiclePropertyId.LAS_MODE_LKA -> "LKA"
            MbCanKnownVehiclePropertyId.LAS_MODE_OFF -> "Выкл"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.FCW_SENSITIVITY -> when (CarSettingsAdasDomain.decodeFcwSensitivityMbCan(value)) {
            FcwSensitivity.Far -> "Дальняя"
            FcwSensitivity.Standard -> "Стандарт"
            FcwSensitivity.Near -> "Ближняя"
            null -> value.toString()
        }

        MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL -> when (CarSettingsAdasDomain.decodeLdwSensitivityMbCan(value)) {
            LdwSensitivity.High -> "Высокая"
            LdwSensitivity.Low -> "Низкая"
            null -> value.toString()
        }

        MbCanKnownVehiclePropertyId.HVAC_CUSTOM -> when (value) {
            MbCanKnownVehiclePropertyId.HVAC_CUSTOM_ECO -> "ECO"
            MbCanKnownVehiclePropertyId.HVAC_CUSTOM_COMFORT -> "Комфорт"
            MbCanKnownVehiclePropertyId.HVAC_CUSTOM_STRONG -> "Сильный"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.FRAGRANCE_SMELL -> when (value) {
            1 -> "Meteor"
            2 -> "Boss"
            3 -> "Tea"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.FRAGRANCE_CONCENTRATION -> when (value) {
            1 -> "Низкая"
            2 -> "Средняя"
            3 -> "Высокая"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE -> when (value) {
            CarSettingsHudDomain.HUD_MODE_STANDARD -> "Стандарт"
            CarSettingsHudDomain.HUD_MODE_SNOW -> "Снег"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.HUD_HEIGHT,
        MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS,
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL,
        -> "Уровень $value"

        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE -> when (value) {
            0 -> "Авто"
            1 -> "Вручную"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET ->
            CarSettingsHudDomain.decodeOverspeedKmh(value)?.let { "$it км/ч" } ?: value.toString()

        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION -> when (value) {
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE -> "Лицо"
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT -> "Ноги"
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT -> "Лицо и ноги"
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST -> "Лобовое"
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT -> "Лобовое и ноги"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE,
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_STEERING_MODE,
        -> when (value) {
            1 -> "ECO"
            2 -> "Комфорт"
            3 -> "Спорт"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET,
        -> DRIVE_MODE_WIDGET_OPTIONS.firstOrNull {
            it.propertyId == propertyId && it.propertyValue == value
        }?.widgetLabel ?: value.toString()

        MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH -> when (value) {
            SlaSpeedLimitDomain.SLA_SWITCH_OFF -> "Выключить"
            SlaSpeedLimitDomain.SLA_SWITCH_ON -> "Включить"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.VEHICLE_PM25_DISPLAY_TOGGLE -> when (value) {
            1 -> "Салон"
            2 -> "Снаружи"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.VEHICLE_UV_LAMP_REQ -> when (value) {
            1 -> "Выкл"
            2 -> "Вкл"
            3 -> "Авто"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.VEHICLE_STERILIZE_STRENGTH_REQ -> when (value) {
            1 -> "Низкая"
            2 -> "Средняя"
            3 -> "Высокая"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT,
        -> HvacClimateDomain.mbCanTempRawToCelsius(value)?.let(::formatCelsius) ?: value.toString()

        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED -> "Скорость $value"

        MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL -> when (value) {
            1 -> "Открыть"
            2 -> "Закрыть"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH -> when (value) {
            1 -> "Сложить"
            2 -> "Разложить"
            else -> value.toString()
        }

        MbCanKnownVehiclePropertyId.SUNSHADE_POS -> when (value) {
            BodyComfortWrite.SHADE_VALUES.first -> "Закрыто"
            BodyComfortWrite.SHADE_VALUES.last -> "Открыто"
            else -> "Положение $value"
        }

        MbCanKnownVehiclePropertyId.SUNROOF_CONTROL -> when (value) {
            BodyComfortWrite.SHADE_VALUES.first -> "Закрыто"
            BodyComfortWrite.SHADE_VALUES.last -> "Открыто"
            MbCanKnownVehiclePropertyId.SUNROOF_TILT -> "Откинуть"
            else -> "Положение $value"
        }

        MbCanKnownVehiclePropertyId.WINDOW_POS,
        MbCanKnownVehiclePropertyId.WINDOW_FL_POS,
        MbCanKnownVehiclePropertyId.WINDOW_FR_POS,
        MbCanKnownVehiclePropertyId.WINDOW_RL_POS,
        MbCanKnownVehiclePropertyId.WINDOW_RR_POS,
        -> if (mode == HeadUnitCanMode.Android10Vhal) {
            when (value) {
                MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE -> "Закрыть"
                MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN -> "Открыть"
                MbCanKnownVehiclePropertyId.WINDOW_A10_VENT -> "Щель"
                else -> value.toString()
            }
        } else {
            "$value %"
        }

        else -> value.toString()
    }

    private fun audioValueLabel(propertyId: Int, value: Int): String = when (propertyId) {
        MbCanKnownAudioPropertyId.VOLUME_SPEED -> when (value) {
            1 -> "Выкл"
            2 -> "Низкий"
            3 -> "Средний"
            4 -> "Высокий"
            else -> value.toString()
        }

        MbCanKnownAudioPropertyId.VOLUME_KEY -> when (value) {
            0 -> "Выкл"
            1 -> "Низкий"
            2 -> "Средний"
            3 -> "Высокий"
            else -> value.toString()
        }

        MbCanKnownAudioPropertyId.VOLUME_RADAR -> when (value) {
            1 -> "Низкий"
            2 -> "Средний"
            3 -> "Высокий"
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
        1 -> "Выкл."
        2 -> "Подогрев 1"
        3 -> "Подогрев 2"
        4 -> "Подогрев 3"
        5 -> "Вентиляция 1"
        6 -> "Вентиляция 2"
        7 -> "Вентиляция 3"
        else -> value.toString()
    }

    private fun rearSeatValueLabel(value: Int): String = when (value) {
        1 -> "Выкл."
        2 -> "Подогрев 1"
        3 -> "Подогрев 2"
        4 -> "Подогрев 3"
        else -> value.toString()
    }

    private fun formatCelsius(celsius: Float): String {
        val rounded = if (celsius % 1f == 0f) celsius.toInt().toString() else celsius.toString()
        return "$rounded °C"
    }

    private fun vehicleLabel(propertyId: Int): String = when (propertyId) {
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH -> "Обогрев руля"
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH -> "Сервисное положение дворников"
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH -> "Парковочный радар"
        MbCanKnownVehiclePropertyId.AVH_SWITCH -> "Auto Hold (AVH)"
        MbCanKnownVehiclePropertyId.HDC_SWITCH -> "HDC"
        MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH -> "Отключение ESP"
        MbCanKnownVehiclePropertyId.LIGHTCONTROL -> "Режим фар"
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT -> "Задний противотуманный фонарь"
        MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK -> "Автозапирание дверей"
        MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK -> "Отпирание при выключении зажигания"
        MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY -> "Задержка Follow Me Home"
        MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE -> "Режим отпирания двери водителя"
        MbCanKnownVehiclePropertyId.DEFENCES_PROMPT -> "Подтверждение запирания"
        MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY -> "Чувствительность дворников"
        MbCanKnownVehiclePropertyId.REAR_WIPER -> "Задний дворник"
        MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW -> "Автоскладывание зеркал"
        MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST -> "Высота ближнего света"
        MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT -> "Количество комфортных миганий"
        MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION -> "Режим удержания полосы"
        MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH -> "TJA/ICA"
        MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION -> "Контроль слепых зон"
        MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING -> "Предупреждение открытия двери"
        MbCanKnownVehiclePropertyId.FCW_SWITCH -> "FCW"
        MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH -> "Автоторможение AEB"
        MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING -> "Предупреждение дистанции"
        MbCanKnownVehiclePropertyId.FCW_SENSITIVITY -> "Чувствительность FCW"
        MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL -> "Чувствительность LDW"
        MbCanKnownVehiclePropertyId.HMA_SWITCH -> "Автоматический дальний свет HMA"
        MbCanKnownVehiclePropertyId.HVAC_CUSTOM -> "Режим климата"
        MbCanKnownVehiclePropertyId.HVAC_AC_MAX -> "AC MAX"
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH -> "Обогрев лобового стекла"
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH -> "Обогрев заднего стекла и зеркал"
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION -> "Рециркуляция воздуха"
        MbCanKnownVehiclePropertyId.HVAC_POWER -> "Питание климата"
        MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY -> "Очистка кондиционера при запирании"
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE -> "Автоматический режим климата"
        MbCanKnownVehiclePropertyId.HVAC_AQS -> "Очистка воздуха / анионы"
        MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH -> "Ароматизация"
        MbCanKnownVehiclePropertyId.FRAGRANCE_SMELL -> "Аромат"
        MbCanKnownVehiclePropertyId.FRAGRANCE_CONCENTRATION -> "Интенсивность аромата"
        MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH -> "Первичная продувка"
        MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED -> "Снижение вентилятора при Bluetooth"
        MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH -> "Автоматическая вентиляция"
        MbCanKnownVehiclePropertyId.HUD_SWITCH -> "HUD"
        MbCanKnownVehiclePropertyId.HUD_HEIGHT -> "Высота HUD"
        MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS -> "Яркость HUD"
        MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE -> "Режим HUD"
        MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS -> "Автояркость HUD"
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE -> "Режим яркости приборной панели"
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL -> "Яркость приборной панели"
        MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET -> "Предупреждение превышения скорости"
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION -> "Направление обдува"
        MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH -> "Беспроводная зарядка"
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_STEERING_MODE -> "Режим рулевого управления"
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE -> "Режим усилителя руля"
        MbCanKnownVehiclePropertyId.SYSTEM_MODE -> "Системный режим автомобиля"
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE -> "Режим движения"
        MbCanKnownVehiclePropertyId.VEHICLE_POWERMODE -> "Режим силовой установки"
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET -> "Режим движения 6DCT"
        MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH -> "Распознавание дорожных знаков"
        MbCanKnownVehiclePropertyId.VEHICLE_PM25_DISPLAY_TOGGLE -> "Отображение PM2.5"
        MbCanKnownVehiclePropertyId.VEHICLE_UV_LAMP_REQ -> "УФ-лампа"
        MbCanKnownVehiclePropertyId.VEHICLE_STERILIZE_STRENGTH_REQ -> "Интенсивность стерилизации"
        MbCanKnownVehiclePropertyId.VEHICEL_BRAKE_PEDA_FEEL_MODE -> "Отклик педали тормоза"
        MbCanKnownVehiclePropertyId.SOURCE_STATION_MODE -> "Режим станции источника"
        MbCanKnownVehiclePropertyId.VEHICLE_VEHWASH_MODESET -> "Режим мойки"
        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH -> "Левое переднее сиденье"
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH -> "Правое переднее сиденье"
        MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH -> "Левое заднее сиденье"
        MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH -> "Правое заднее сиденье"
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT -> "Температура климата слева"
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT -> "Температура климата справа"
        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED -> "Скорость вентилятора"
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF -> "Передний климат"
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH -> "Синхронизация климата"
        MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL -> "Электропривод багажника"
        MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH -> "Складывание зеркал"
        MbCanKnownVehiclePropertyId.SUNSHADE_POS -> "Шторка"
        MbCanKnownVehiclePropertyId.SUNROOF_CONTROL -> "Люк"
        MbCanKnownVehiclePropertyId.WINDOW_POS -> "Все стёкла"
        MbCanKnownVehiclePropertyId.WINDOW_FL_POS -> "Стекло переднее левое"
        MbCanKnownVehiclePropertyId.WINDOW_FR_POS -> "Стекло переднее правое"
        MbCanKnownVehiclePropertyId.WINDOW_RL_POS -> "Стекло заднее левое"
        MbCanKnownVehiclePropertyId.WINDOW_RR_POS -> "Стекло заднее правое"
        else -> "CAN-параметр $propertyId"
    }

    private fun audioLabel(propertyId: Int): String = when (propertyId) {
        MbCanKnownAudioPropertyId.VOLUME_SPEED -> "Громкость в зависимости от скорости"
        MbCanKnownAudioPropertyId.VOLUME_KEY -> "Громкость звука клавиш"
        MbCanKnownAudioPropertyId.VOLUME_RADAR -> "Громкость парковочного радара"
        MbCanKnownAudioPropertyId.EQ_MODE -> "Режим эквалайзера"
        MbCanKnownAudioPropertyId.EQ_BAND_BASS -> "Низкие частоты"
        MbCanKnownAudioPropertyId.EQ_BAND_MIDDLE -> "Средние частоты"
        MbCanKnownAudioPropertyId.EQ_BAND_TREBLE -> "Высокие частоты"
        MbCanKnownAudioPropertyId.BALANCE -> "Баланс аудио"
        MbCanKnownAudioPropertyId.FADER -> "Фейдер аудио"
        else -> "Аудиопараметр $propertyId"
    }
}
