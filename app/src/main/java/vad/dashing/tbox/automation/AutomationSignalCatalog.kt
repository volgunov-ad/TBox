package vad.dashing.tbox.automation

import java.text.Collator
import java.util.Locale
import vad.dashing.tbox.mbcan.AccStatusDomain
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.WiperStsDomain

data class AutomationSignalNamedValue(
    val value: String,
    val label: String,
)

data class AutomationSignalDescriptor(
    val id: AutomationSignalId,
    val label: String,
    val unit: String = "",
    val sources: Set<AutomationSignalSource>,
    val stateOptions: List<String> = emptyList(),
    val namedValues: List<AutomationSignalNamedValue> = emptyList(),
    val typicalRange: String = "",
) {
    fun valueHint(): String {
        val named = formatNamedValues(namedValues)
        return when {
            named.isNotEmpty() && typicalRange.isNotEmpty() ->
                "Возможные значения: $named. $typicalRange"
            named.isNotEmpty() -> "Возможные значения: $named"
            else -> typicalRange
        }
    }
}

object AutomationSignalCatalog {
    private val SOURCE_UI_ORDER = listOf(
        AutomationSignalSource.HEAD_UNIT,
        AutomationSignalSource.TBOX,
        AutomationSignalSource.APP,
    )
    private val bothSources = setOf(
        AutomationSignalSource.HEAD_UNIT,
        AutomationSignalSource.TBOX,
    )
    private val headUnitOnly = setOf(AutomationSignalSource.HEAD_UNIT)
    private val tboxOnly = setOf(AutomationSignalSource.TBOX)
    private val appOnly = setOf(AutomationSignalSource.APP)
    private val binaryStates = listOf("off", "on")
    private val frontSeatStates = listOf(
        "off",
        "heat_1",
        "heat_2",
        "heat_3",
        "vent_1",
        "vent_2",
        "vent_3",
    )
    private val rearSeatStates = listOf("off", "heat_1", "heat_2", "heat_3")
    private const val windowPositionTypicalRange =
        "Только ГУ. Закрыто / открыто / щель. Положение 0…100 %: 0 закрыто, 1…30 щель " +
            "(штатная щель 20), 31…100 открыто. A9 BCM getVehicleWindow; A10 *_WIN_Position."

    val entries: List<AutomationSignalDescriptor> = listOf(
        number(
            AutomationSignalId.ENGINE_RPM,
            "Обороты двигателя",
            "об/мин",
            bothSources,
            typicalRange = "Значения в об/мин, обычно 0…8000",
        ),
        number(
            AutomationSignalId.CAR_SPEED,
            "Скорость автомобиля",
            "км/ч",
            bothSources,
            typicalRange = "Значения в км/ч, обычно 0…240",
        ),
        number(
            AutomationSignalId.ENGINE_TEMPERATURE,
            "Температура двигателя",
            "°C",
            bothSources,
            typicalRange = "Значения в °C",
        ),
        number(
            AutomationSignalId.OUTSIDE_TEMPERATURE,
            "Температура снаружи",
            "°C",
            bothSources,
            typicalRange = "Значения в °C",
        ),
        number(
            AutomationSignalId.INSIDE_TEMPERATURE,
            "Температура в салоне",
            "°C",
            tboxOnly,
            typicalRange = "Значения в °C",
        ),
        number(
            AutomationSignalId.FUEL_LEVEL_PERCENT,
            "Уровень топлива",
            "%",
            bothSources,
            typicalRange = "Значения в %, обычно 0…100",
        ),
        number(
            AutomationSignalId.ODOMETER_KM,
            "Одометр",
            "км",
            bothSources,
            typicalRange = "Значения в км",
        ),
        number(
            AutomationSignalId.CURRENT_FUEL_CONSUMPTION,
            "Текущий расход топлива",
            "л/100 км",
            bothSources,
            typicalRange = "Значения в л/100 км",
        ),
        number(
            AutomationSignalId.DISTANCE_TO_EMPTY_KM,
            "Запас хода",
            "км",
            bothSources,
            typicalRange = "Значения в км",
        ),
        number(
            AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM,
            "До обслуживания",
            "км",
            bothSources,
            typicalRange = "Значения в км",
        ),
        number(
            AutomationSignalId.VOLTAGE,
            "Напряжение",
            "В",
            tboxOnly,
            typicalRange = "Значения в В, обычно 11…15",
        ),
        number(
            AutomationSignalId.STEERING_ANGLE,
            "Угол руля",
            "°",
            bothSources,
            typicalRange = "Значения в градусах",
        ),
        number(
            AutomationSignalId.STEERING_SPEED,
            "Скорость вращения руля",
            "°/с",
            bothSources,
            typicalRange = "Значения в °/с; на ГУ Android 10 часто недоступна",
        ),
        number(
            AutomationSignalId.CRUISE_SET_SPEED,
            "Уставка круиза",
            "км/ч",
            bothSources,
            typicalRange = "Значения в км/ч",
        ),
        state(AutomationSignalId.GEAR_MODE, "Режим КПП", bothSources, listOf("P", "R", "N", "D")),
        state(
            AutomationSignalId.ACC_STATUS,
            "Статус ACC (ключ)",
            headUnitOnly,
            AccStatusDomain.STATE_OPTIONS,
            typicalRange = "Android 9: AccStatus 4=ACC ON, 5=ON, 0…3=выкл. " +
                "Android 10: MCU_REPLY_ACC_STATUS 1 и 2=ACC ON, 0 и 3=выкл (шкала не 4/5).",
        ),
        number(
            AutomationSignalId.GAS_PEDAL,
            "Педаль газа",
            "%",
            headUnitOnly,
            typicalRange = "Значения в %, обычно 0…100. Невалидный EMS-флаг или вне диапазона — нет значения.",
        ),
        state(
            AutomationSignalId.BRAKE_PEDAL,
            "Педаль тормоза",
            headUnitOnly,
            binaryStates,
            typicalRange = "Только ГУ. BrakePedalSts: 2=нажата (on), 1=отпущена (off). " +
                "0 и прочие — нет значения. Не CEM 1-bit.",
        ),
        number(
            AutomationSignalId.CURRENT_GEAR,
            "Текущая передача",
            "",
            tboxOnly,
            typicalRange = "Номер передачи в D, обычно 1…8; вне D часто 0",
        ),
        number(
            AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE,
            "Давление переднего левого колеса",
            "бар",
            bothSources,
            typicalRange = "Значения в бар",
        ),
        number(
            AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE,
            "Давление переднего правого колеса",
            "бар",
            bothSources,
            typicalRange = "Значения в бар",
        ),
        number(
            AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE,
            "Давление заднего левого колеса",
            "бар",
            bothSources,
            typicalRange = "Значения в бар",
        ),
        number(
            AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE,
            "Давление заднего правого колеса",
            "бар",
            bothSources,
            typicalRange = "Значения в бар",
        ),
        number(
            AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE,
            "Температура переднего левого колеса",
            "°C",
            bothSources,
            typicalRange = "Значения в °C",
        ),
        number(
            AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE,
            "Температура переднего правого колеса",
            "°C",
            bothSources,
            typicalRange = "Значения в °C",
        ),
        number(
            AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE,
            "Температура заднего левого колеса",
            "°C",
            bothSources,
            typicalRange = "Значения в °C",
        ),
        number(
            AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE,
            "Температура заднего правого колеса",
            "°C",
            bothSources,
            typicalRange = "Значения в °C",
        ),
        number(
            AutomationSignalId.INSIDE_AIR_QUALITY,
            "Качество воздуха в салоне",
            "",
            bothSources,
            typicalRange = "PM2.5, мкг/м³; валидно 1…65534",
        ),
        number(
            AutomationSignalId.OUTSIDE_AIR_QUALITY,
            "Качество наружного воздуха",
            "",
            bothSources,
            typicalRange = "PM2.5, мкг/м³; валидно 1…65534",
        ),
        state(AutomationSignalId.STEERING_WHEEL_HEAT, "Обогрев руля", headUnitOnly, binaryStates),
        state(
            AutomationSignalId.WIPER_MAINTENANCE,
            "Сервисное положение дворников",
            headUnitOnly,
            binaryStates,
        ),
        state(
            AutomationSignalId.WIPER_STS,
            "Режим дворников",
            headUnitOnly,
            WiperStsDomain.STATE_OPTIONS,
            typicalRange = "Только ГУ. TTG: 0=выкл, 1=INT (на части комплектаций иконка AUTO), " +
                "2=Low, 3=High. Не сервисное положение дворников.",
        ),
        state(
            AutomationSignalId.RAIN_DETECTED,
            "Детектор дождя",
            headUnitOnly,
            binaryStates,
            typicalRange = "Только ГУ. CEM 1-bit: 1=дождь (S_RAIN TRUE), 0=сухо. " +
                "A9 BCM getRainDetectedSts, A10 R_0400_CEM_2_RainDetected. " +
                "Не отказ датчика RainSensorFailSts.",
        ),
        state(
            AutomationSignalId.SUNSHADE,
            "Шторка",
            headUnitOnly,
            BodyComfortDomain.SHADE_STATE_OPTIONS,
            typicalRange = "Только ГУ. Закрыто / открыто. A9: canGet/cfg 46 (в BCM шторки нет). " +
                "A10: Abat_VentCMDSts. 0/1 закрыто, 2…11 и 10…100 открыто.",
        ),
        state(
            AutomationSignalId.SUNROOF,
            "Люк",
            headUnitOnly,
            BodyComfortDomain.ROOF_STATE_OPTIONS,
            typicalRange = "Только ГУ. Закрыто / открыто / откинут. A9: canGet/cfg 45 " +
                "(не BCM getSunRoof: там −1). Статус: 0 закрыто, 10…100 открыто, 102 откинут; " +
                "команда 12 тоже tilt. A10: PSRFCMDSts.",
        ),
        state(
            AutomationSignalId.WINDOW_FRONT_LEFT,
            "Стекло переднее левое",
            headUnitOnly,
            BodyComfortDomain.WINDOW_STATE_OPTIONS,
            typicalRange = windowPositionTypicalRange,
        ),
        state(
            AutomationSignalId.WINDOW_FRONT_RIGHT,
            "Стекло переднее правое",
            headUnitOnly,
            BodyComfortDomain.WINDOW_STATE_OPTIONS,
            typicalRange = windowPositionTypicalRange,
        ),
        state(
            AutomationSignalId.WINDOW_REAR_LEFT,
            "Стекло заднее левое",
            headUnitOnly,
            BodyComfortDomain.WINDOW_STATE_OPTIONS,
            typicalRange = windowPositionTypicalRange,
        ),
        state(
            AutomationSignalId.WINDOW_REAR_RIGHT,
            "Стекло заднее правое",
            headUnitOnly,
            BodyComfortDomain.WINDOW_STATE_OPTIONS,
            typicalRange = windowPositionTypicalRange,
        ),
        state(AutomationSignalId.PARKING_RADAR, "Парковочный радар", headUnitOnly, binaryStates),
        state(
            AutomationSignalId.REAR_FOG,
            "Задний противотуманный фонарь",
            headUnitOnly,
            binaryStates,
        ),
        state(AutomationSignalId.AVH, "Auto Hold (AVH)", headUnitOnly, binaryStates),
        state(AutomationSignalId.HDC, "HDC", headUnitOnly, binaryStates),
        state(AutomationSignalId.ESP_OFF, "Отключение ESP", headUnitOnly, binaryStates),
        state(AutomationSignalId.TJA_ICA, "TJA/ICA", headUnitOnly, binaryStates),
        state(
            AutomationSignalId.HMA,
            "Автоматический дальний свет HMA",
            headUnitOnly,
            binaryStates,
        ),
        state(AutomationSignalId.HVAC_AC_MAX, "AC MAX", headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_POWER, "Питание климата", headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_AUTO, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_AUTO), headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_RECIRCULATION, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_RECIRCULATION), headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_SYNC, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_SYNC), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.DRIVE_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.DRIVE_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.driveModeOptions,
            typicalRange = "Стандартный режим движения ГУ (ECO, NOR, SPT и др.)",
        ),
        state(
            AutomationSignalId.HEADLIGHT_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HEADLIGHT_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.headlightOptions,
        ),
        state(AutomationSignalId.REVERSE_GEAR, "Задняя передача", headUnitOnly, binaryStates),
        state(
            AutomationSignalId.FRONT_LEFT_SEAT_MODE,
            "Левое переднее сиденье",
            headUnitOnly,
            frontSeatStates,
        ),
        state(
            AutomationSignalId.FRONT_RIGHT_SEAT_MODE,
            "Правое переднее сиденье",
            headUnitOnly,
            frontSeatStates,
        ),
        state(
            AutomationSignalId.REAR_LEFT_SEAT_MODE,
            "Левое заднее сиденье",
            headUnitOnly,
            rearSeatStates,
        ),
        state(
            AutomationSignalId.REAR_RIGHT_SEAT_MODE,
            "Правое заднее сиденье",
            headUnitOnly,
            rearSeatStates,
        ),
        state(AutomationSignalId.DOOR_AUTO_LOCK, AutomationParameterLabels.signalLabel(AutomationSignalId.DOOR_AUTO_LOCK), headUnitOnly, binaryStates),
        state(AutomationSignalId.DOOR_IGNOFF_UNLOCK, AutomationParameterLabels.signalLabel(AutomationSignalId.DOOR_IGNOFF_UNLOCK), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.HEADLIGHTS_FOLLOW_ME_HOME,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HEADLIGHTS_FOLLOW_ME_HOME),
            headUnitOnly,
            AutomationSignalStateEncoding.followMeHomeOptions,
        ),
        state(
            AutomationSignalId.DRIVER_UNLOCK_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.DRIVER_UNLOCK_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.driverUnlockOptions,
        ),
        state(
            AutomationSignalId.REMOTE_LOCK_FEEDBACK,
            AutomationParameterLabels.signalLabel(AutomationSignalId.REMOTE_LOCK_FEEDBACK),
            headUnitOnly,
            AutomationSignalStateEncoding.remoteLockFeedbackOptions,
        ),
        number(
            AutomationSignalId.WIPER_SENSITIVITY,
            AutomationParameterLabels.signalLabel(AutomationSignalId.WIPER_SENSITIVITY),
            "",
            headUnitOnly,
            typicalRange = "Уровень 1…4",
        ),
        state(AutomationSignalId.REAR_WIPER, AutomationParameterLabels.signalLabel(AutomationSignalId.REAR_WIPER), headUnitOnly, binaryStates),
        state(AutomationSignalId.MIRROR_AUTO_FOLD, AutomationParameterLabels.signalLabel(AutomationSignalId.MIRROR_AUTO_FOLD), headUnitOnly, binaryStates),
        number(
            AutomationSignalId.LOW_BEAM_HEIGHT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.LOW_BEAM_HEIGHT),
            "",
            headUnitOnly,
            typicalRange = "Уровень 1…4",
        ),
        number(
            AutomationSignalId.TURN_FLASH_COUNT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.TURN_FLASH_COUNT),
            "",
            headUnitOnly,
            typicalRange = "CAN 1/2/3 → 3/5/7 миганий",
        ),
        state(
            AutomationSignalId.LAS_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.LAS_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.lasModeOptions,
        ),
        state(AutomationSignalId.BLIND_SPOT_DETECTION, AutomationParameterLabels.signalLabel(AutomationSignalId.BLIND_SPOT_DETECTION), headUnitOnly, binaryStates),
        state(AutomationSignalId.DOOR_OPEN_WARNING, AutomationParameterLabels.signalLabel(AutomationSignalId.DOOR_OPEN_WARNING), headUnitOnly, binaryStates),
        state(AutomationSignalId.FCW, AutomationParameterLabels.signalLabel(AutomationSignalId.FCW), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.FCW_SENSITIVITY,
            AutomationParameterLabels.signalLabel(AutomationSignalId.FCW_SENSITIVITY),
            headUnitOnly,
            AutomationSignalStateEncoding.fcwSensitivityOptions,
        ),
        state(
            AutomationSignalId.LDW_SENSITIVITY,
            AutomationParameterLabels.signalLabel(AutomationSignalId.LDW_SENSITIVITY),
            headUnitOnly,
            AutomationSignalStateEncoding.ldwSensitivityOptions,
        ),
        state(
            AutomationSignalId.HVAC_CUSTOM_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_CUSTOM_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.hvacCustomOptions,
        ),
        state(AutomationSignalId.FRONT_WINDSCREEN_HEAT, AutomationParameterLabels.signalLabel(AutomationSignalId.FRONT_WINDSCREEN_HEAT), headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_REAR_DEFROSTER, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_REAR_DEFROSTER), headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_AC_CLEAN_WHEN_LOCKED, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_AC_CLEAN_WHEN_LOCKED), headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_ANION_PURIFY, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_ANION_PURIFY), headUnitOnly, binaryStates),
        state(AutomationSignalId.FRAGRANCE, AutomationParameterLabels.signalLabel(AutomationSignalId.FRAGRANCE), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.FRAGRANCE_SMELL,
            AutomationParameterLabels.signalLabel(AutomationSignalId.FRAGRANCE_SMELL),
            headUnitOnly,
            AutomationSignalStateEncoding.fragranceSmellOptions,
            typicalRange = "Только Android 9 mbCAN",
        ),
        state(
            AutomationSignalId.FRAGRANCE_CONCENTRATION,
            AutomationParameterLabels.signalLabel(AutomationSignalId.FRAGRANCE_CONCENTRATION),
            headUnitOnly,
            AutomationSignalStateEncoding.fragranceConcentrationOptions,
            typicalRange = "Только Android 9 mbCAN",
        ),
        state(AutomationSignalId.HVAC_FIRST_BLOWING, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_FIRST_BLOWING), headUnitOnly, binaryStates),
        state(AutomationSignalId.BT_REDUCE_FAN, AutomationParameterLabels.signalLabel(AutomationSignalId.BT_REDUCE_FAN), headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_AUTO_VENTILATION, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_AUTO_VENTILATION), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.HVAC_FAN_DIRECTION,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_FAN_DIRECTION),
            headUnitOnly,
            AutomationSignalStateEncoding.hvacFanDirectionOptions,
        ),
        number(
            AutomationSignalId.HVAC_TEMPERATURE_LEFT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_TEMPERATURE_LEFT),
            "°C",
            headUnitOnly,
            typicalRange = "Температура в °C",
        ),
        number(
            AutomationSignalId.HVAC_TEMPERATURE_RIGHT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_TEMPERATURE_RIGHT),
            "°C",
            headUnitOnly,
            typicalRange = "Температура в °C",
        ),
        number(
            AutomationSignalId.HVAC_FAN_SPEED,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_FAN_SPEED),
            "",
            headUnitOnly,
            typicalRange = "Скорость вентилятора 1…7",
        ),
        state(AutomationSignalId.HVAC_FRONT_OFF, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_FRONT_OFF), headUnitOnly, binaryStates),
        state(AutomationSignalId.HUD, AutomationParameterLabels.signalLabel(AutomationSignalId.HUD), headUnitOnly, binaryStates),
        number(
            AutomationSignalId.HUD_HEIGHT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HUD_HEIGHT),
            "",
            headUnitOnly,
            typicalRange = "Уровень 1…10",
        ),
        number(
            AutomationSignalId.HUD_BRIGHTNESS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HUD_BRIGHTNESS),
            "",
            headUnitOnly,
            typicalRange = "Уровень 1…10",
        ),
        state(
            AutomationSignalId.HUD_DISPLAY_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HUD_DISPLAY_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.hudDisplayModeOptions,
        ),
        state(AutomationSignalId.HUD_AUTO_BRIGHTNESS, AutomationParameterLabels.signalLabel(AutomationSignalId.HUD_AUTO_BRIGHTNESS), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.ICM_BRIGHTNESS_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ICM_BRIGHTNESS_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.icmBrightnessModeOptions,
        ),
        number(
            AutomationSignalId.ICM_BRIGHTNESS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ICM_BRIGHTNESS),
            "",
            headUnitOnly,
            typicalRange = "Уровень 1…10",
        ),
        number(
            AutomationSignalId.OVERSPEED_ALARM,
            AutomationParameterLabels.signalLabel(AutomationSignalId.OVERSPEED_ALARM),
            "км/ч",
            headUnitOnly,
            typicalRange = "30…230 км/ч с шагом 5",
        ),
        state(
            AutomationSignalId.STEERING_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.STEERING_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.steeringFeelOptions,
        ),
        state(
            AutomationSignalId.EPS_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.EPS_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.steeringFeelOptions,
        ),
        state(
            AutomationSignalId.DRIVE_MODE_6DCT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.DRIVE_MODE_6DCT),
            headUnitOnly,
            AutomationSignalStateEncoding.driveMode6dctOptions,
        ),
        state(AutomationSignalId.TSR_SWITCH, AutomationParameterLabels.signalLabel(AutomationSignalId.TSR_SWITCH), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.TRUNK_DOOR,
            AutomationParameterLabels.signalLabel(AutomationSignalId.TRUNK_DOOR),
            headUnitOnly,
            AutomationSignalStateEncoding.trunkDoorOptions,
        ),
        state(
            AutomationSignalId.AUDIO_VOLUME_SPEED_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_VOLUME_SPEED_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.audioVolumeSpeedOptions,
            typicalRange = "Только Android 9 mbCAN",
        ),
        number(
            AutomationSignalId.AUDIO_KEY_TONE_VOLUME,
            AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_KEY_TONE_VOLUME),
            "",
            headUnitOnly,
            typicalRange = "0 выкл, 1…3 уровень. Только Android 9 mbCAN",
        ),
        state(
            AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME,
            AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME),
            headUnitOnly,
            AutomationSignalStateEncoding.audioRadarVolumeOptions,
            typicalRange = "Только Android 9 mbCAN",
        ),
        state(
            AutomationSignalId.AUDIO_EQ_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_EQ_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.audioEqModeOptions,
            typicalRange = "Только Android 9 mbCAN",
        ),
        number(AutomationSignalId.AUDIO_EQ_BASS, AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_EQ_BASS), "", headUnitOnly, typicalRange = "Уровень 0…14, Android 9"),
        number(AutomationSignalId.AUDIO_EQ_MIDDLE, AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_EQ_MIDDLE), "", headUnitOnly, typicalRange = "Уровень 0…14, Android 9"),
        number(AutomationSignalId.AUDIO_EQ_TREBLE, AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_EQ_TREBLE), "", headUnitOnly, typicalRange = "Уровень 0…14, Android 9"),
        number(AutomationSignalId.AUDIO_BALANCE, AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_BALANCE), "", headUnitOnly, typicalRange = "Уровень 0…14, Android 9"),
        number(AutomationSignalId.AUDIO_FADER, AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_FADER), "", headUnitOnly, typicalRange = "Уровень 0…14, Android 9"),
        AutomationSignalDescriptor(
            id = AutomationSignalId.GEO_POSITION,
            label = "Геопозиция",
            sources = appOnly,
            typicalRange = "Текущая точка GeoDisplay (GNSS или подмена)",
        ),
        state(AutomationSignalId.ESP_GPIO_IN_0, "ESP-вход 0", appOnly, binaryStates),
        state(AutomationSignalId.ESP_GPIO_IN_1, "ESP-вход 1", appOnly, binaryStates),
        state(AutomationSignalId.ESP_GPIO_IN_2, "ESP-вход 2", appOnly, binaryStates),
        state(AutomationSignalId.ESP_GPIO_IN_3, "ESP-вход 3", appOnly, binaryStates),
        state(AutomationSignalId.ESP_RELAY_0, "ESP-реле 0", appOnly, binaryStates),
        state(AutomationSignalId.ESP_RELAY_1, "ESP-реле 1", appOnly, binaryStates),
        state(
            AutomationSignalId.FOREGROUND_APP,
            "Приложение на экране",
            appOnly,
            typicalRange = "Пакет приложения на переднем плане. Нужен доступ к статистике " +
                "использования. Опрос 1 с, окно событий 10 с; пустой опрос держит последний пакет. " +
                "Пакет самого TBox учитывается, только если открыт главный экран. Без разрешения " +
                "сигнала нет. Камера 360 com.mengbo.avm учитывается по штатному оверлею " +
                "(Settings.Global avm_state), даже если UsageStats держит предыдущее приложение.",
        ),
    )

    private val byId = entries.associateBy { it.id }

    fun get(id: AutomationSignalId): AutomationSignalDescriptor = requireNotNull(byId[id])

    fun preferredSource(id: AutomationSignalId): AutomationSignalSource =
        preferredSource(get(id).sources)

    fun preferredSource(sources: Set<AutomationSignalSource>): AutomationSignalSource {
        require(sources.isNotEmpty()) { "signal sources must not be empty" }
        return SOURCE_UI_ORDER.firstOrNull { it in sources } ?: sources.first()
    }

    fun sourcesForUi(id: AutomationSignalId): List<AutomationSignalSource> =
        sourcesForUi(get(id).sources)

    fun sourcesForUi(sources: Set<AutomationSignalSource>): List<AutomationSignalSource> {
        val ordered = SOURCE_UI_ORDER.filter { it in sources }
        val extra = sources.filter { it !in SOURCE_UI_ORDER }
        return ordered + extra
    }

    fun supports(id: AutomationSignalId, source: AutomationSignalSource): Boolean =
        source in get(id).sources

    fun signalsOfType(valueType: AutomationSignalValueType): List<AutomationSignalId> =
        entries.filter { it.id.valueType == valueType }
            .sortedByAutomationLabel { it.label }
            .map { it.id }

    fun stateOptionLabel(raw: String): String = AutomationSignalStateEncoding.stateOptionLabel(raw)

    private fun number(
        id: AutomationSignalId,
        label: String,
        unit: String,
        sources: Set<AutomationSignalSource>,
        typicalRange: String = "",
        namedValues: List<AutomationSignalNamedValue> = emptyList(),
    ) = AutomationSignalDescriptor(
        id = id,
        label = label,
        unit = unit,
        sources = sources,
        namedValues = namedValues,
        typicalRange = typicalRange,
    )

    private fun state(
        id: AutomationSignalId,
        label: String,
        sources: Set<AutomationSignalSource>,
        options: List<String> = emptyList(),
        typicalRange: String = "",
    ) = AutomationSignalDescriptor(
        id = id,
        label = label,
        sources = sources,
        stateOptions = options,
        namedValues = options.map { AutomationSignalNamedValue(it, stateOptionLabel(it)) },
        typicalRange = typicalRange,
    )
}

private fun formatNamedValues(values: List<AutomationSignalNamedValue>): String =
    values.joinToString { named ->
        val label = named.label.trim()
        val value = named.value.trim()
        when {
            label.isEmpty() -> value
            label.equals(value, ignoreCase = true) -> value
            else -> "$label ($value)"
        }
    }

private val automationLabelCollator: Collator =
    Collator.getInstance(Locale.forLanguageTag("ru-RU")).apply {
        strength = Collator.PRIMARY
    }

internal fun <T> List<T>.sortedByAutomationLabel(labelOf: (T) -> String): List<T> =
    sortedWith(compareBy(automationLabelCollator, labelOf))
