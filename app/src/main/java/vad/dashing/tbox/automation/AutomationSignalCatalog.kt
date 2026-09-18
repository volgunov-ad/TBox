package vad.dashing.tbox.automation

import java.text.Collator
import java.util.Locale
import vad.dashing.tbox.mbcan.AccCruiseDomain
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
        "Только ГУ. Положение 0…100 %: 0 закрыто, штатная щель 20, комфортное открытие 80, " +
            "100 полностью открыто. A9 BCM getVehicleWindow; A10 *_WIN_Position."

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
        state(
            AutomationSignalId.ACC_CRUISE_STATE,
            "Состояние ACC (круиз)",
            headUnitOnly,
            AccCruiseDomain.ACC_AUTOMATION_STATE_OPTIONS,
            typicalRange = "Только ГУ. Off/Standby/Active/Override/Fault из ACCMode: " +
                "0=off; 1,2,6=standby; 3,4,5=active; 7=override (газ перебивает ACC); 9=fault. " +
                "Не путать со «Статус ACC (ключ)» (acc_status).",
        ),
        state(
            AutomationSignalId.CCS_CRUISE_STATE,
            "Состояние CCS (круиз)",
            headUnitOnly,
            AccCruiseDomain.CCS_AUTOMATION_STATE_OPTIONS,
            typicalRange = "Только ГУ. Off/Standby/Active из CruiseControlStatus: " +
                "0=off; 1=active; 2=standby. Fault нет (только у ACC).",
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
            typicalRange = "Только ГУ. A9 BrakePedalSts: 2=нажата (on), 1=отпущена (off); " +
                "A10 VHAL: 1=нажата, 0=отпущена. Прочие — нет значения.",
        ),
        number(
            AutomationSignalId.CURRENT_GEAR,
            "Текущая передача",
            "",
            bothSources,
            typicalRange = "Номер передачи в D, обычно 1…8; вне D часто 0. " +
                "A9 BCM getGSM_GearShiftPos / A10 VHAL GSM_GearShiftPos; TBox gearBoxCurrentGear.",
        ),
        number(
            AutomationSignalId.TARGET_GEAR,
            "Целевая передача",
            "",
            bothSources,
            typicalRange = "Подготовленная / целевая передача. " +
                "A10 VHAL EMS_TargetGearPosition; на A9 mbCAN обычно нет (null). TBox gearBoxPreparedGear.",
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
            AutomationSignalId.HIGH_BEAM,
            "Дальний свет",
            headUnitOnly,
            binaryStates,
            typicalRange = "Только ГУ. Бинарный статус: включён/выключен. " +
                "A9 BCM stLightSts.nHighBeamSts, A10 R_0404_CEM_2_HighBeamSts (CEM 1-bit). " +
                "Не режим LIGHTCONTROL 1…4.",
        ),
        state(
            AutomationSignalId.EPB_PARK_LAMP,
            "Лампа EPB (паркинг)",
            headUnitOnly,
            binaryStates,
            typicalRange = "Только ГУ. Бинарный статус лампы EPB. " +
                "A9 BCM getEPBParkLampSts (предположена шкала CEM-switch 2=on); " +
                "A10 VHAL R_0900_ICM_7_EPBWarningLampSts (прокси, CEM 1-bit) — уточнить на машине.",
        ),
        state(
            AutomationSignalId.ENGINE_OIL_PRESSURE,
            "Давление масла (предупреждение)",
            headUnitOnly,
            binaryStates,
            typicalRange = "Только ГУ. on = предупреждение (проблема), off = норма. " +
                "A9 ICM drive info getICM_EngineOil (type 44, только poll); " +
                "A10 VHAL R_0900_ICM_4_Engine_Oil_Pressure (CEM 1-bit, TBD на машине).",
        ),
        state(
            AutomationSignalId.BRAKE_FLUID,
            "Тормозная жидкость (предупреждение)",
            headUnitOnly,
            binaryStates,
            typicalRange = "Только ГУ. on = предупреждение (проблема), off = норма. " +
                "A9 ICM drive info getICM_Brakefluid (type 44, только poll); " +
                "A10 VHAL R_0900_ICM_4_Brake_Fuel_Level (CEM 1-bit, TBD на машине).",
        ),
        number(
            AutomationSignalId.FRM_DX_TAR_OBJ,
            "FRM DxTarObj",
            "",
            headUnitOnly,
            typicalRange = "Только ГУ. Сырое FRM_3_DxTarObj при ObjValid=1; иначе нет значения.",
        ),
        state(
            AutomationSignalId.SUNSHADE,
            "Шторка",
            headUnitOnly,
            BodyComfortDomain.SHADE_STATE_OPTIONS,
            typicalRange = "Только ГУ. Проценты 0…100 с шагом 10 (0% закрыто, 100% открыто). " +
                "A9: canGet/cfg 46 (в BCM шторки нет). A10: Abat_VentCMDSts.",
        ),
        state(
            AutomationSignalId.SUNROOF,
            "Люк",
            headUnitOnly,
            BodyComfortDomain.ROOF_STATE_OPTIONS,
            typicalRange = "Только ГУ. Проценты 0…100 с шагом 10 + tilt (откинут: чтение 102 " +
                "или 10%). A9: canGet/cfg 45 (не BCM getSunRoof: там −1). A10: PSRFCMDSts.",
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
        state(AutomationSignalId.AVH, AutomationParameterLabels.signalLabel(AutomationSignalId.AVH), headUnitOnly, binaryStates),
        state(AutomationSignalId.HDC, AutomationParameterLabels.signalLabel(AutomationSignalId.HDC), headUnitOnly, binaryStates),
        state(AutomationSignalId.ESP_OFF, AutomationParameterLabels.signalLabel(AutomationSignalId.ESP_OFF), headUnitOnly, binaryStates),
        state(AutomationSignalId.TJA_ICA, AutomationParameterLabels.signalLabel(AutomationSignalId.TJA_ICA), headUnitOnly, binaryStates),
        state(AutomationSignalId.HMA, AutomationParameterLabels.signalLabel(AutomationSignalId.HMA), headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_AC_MAX, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_AC_MAX), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.HVAC_POWER,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_POWER),
            headUnitOnly,
            binaryStates,
        ),
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
            AutomationSignalId.WIFI_ENABLED,
            "Wi-Fi",
            appOnly,
            binaryStates,
            typicalRange = "Только клиентский Wi-Fi ГУ (wlan0). on — радио включено, off — выключено. " +
                "Точка доступа ГУ (SoftAP) не учитывается.",
        ),
        state(
            AutomationSignalId.WIFI_ASSOCIATED,
            "Wi-Fi: подключение к сети",
            appOnly,
            binaryStates,
            typicalRange = "on — есть ассоциация с любой сохранённой сетью; off — нет ассоциации " +
                "или радио выключено (явный off, не «нет значения»).",
        ),
        state(
            AutomationSignalId.WIFI_SSID,
            "Wi-Fi: точка доступа",
            appOnly,
            typicalRange = "SSID текущей сети или none, если радио выключено или ассоциации нет. " +
                "Выбор из сохранённых сетей ГУ.",
        ),
        state(
            AutomationSignalId.HU_INTERNET_STATUS,
            "Интернет ГУ",
            appOnly,
            listOf("unknown", "checking", "online", "offline"),
            typicalRange = "HTTP(S) проверка URL с вкладки «Модем». online — сразу после удачной " +
                "проверки; offline — после двух подряд неудачных; unknown — монитор не запущен. " +
                "checking почти не публикуется (чтобы не дёргать автоматизации).",
        ),
        state(
            AutomationSignalId.WIFI_MODEM_LINK_STATUS,
            "Wi‑Fi модем: связь",
            appOnly,
            vad.dashing.tbox.wifimodem.ModemAutomationStates.LINK_OPTIONS,
            typicalRange = "HTTP-связь с админкой Wi‑Fi модема при источнике «Wi‑Fi HTTP»: " +
                "ok / auth_failed / unreachable / error; idle — источник TBox или поллер не запущен.",
        ),
        state(
            AutomationSignalId.MODEM_MOBILE_DATA,
            "Модем: передача данных",
            appOnly,
            binaryStates,
            typicalRange = "on/off по apnStatus (TBox MDC или Wi‑Fi модем). " +
                "То же readback, что после действия wifi_modem_set_data.",
        ),
        state(
            AutomationSignalId.MODEM_NET_TYPE,
            "Модем: тип сети",
            appOnly,
            vad.dashing.tbox.wifimodem.ModemAutomationStates.NET_TYPE_OPTIONS,
            typicalRange = "2g / 3g / 4g / none из netStatus вкладки «Модем» (TBox или Wi‑Fi HTTP).",
        ),
        state(
            AutomationSignalId.MODEM_SIM_STATUS,
            "Модем: SIM",
            appOnly,
            vad.dashing.tbox.wifimodem.ModemAutomationStates.SIM_OPTIONS,
            typicalRange = "none / ready / pin / error / unknown из simStatus вкладки «Модем».",
        ),
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
        state(
            AutomationSignalId.APP_THEME_MODE,
            "Тема приложения: режим",
            appOnly,
            listOf("manual_day", "manual_night", "auto_day", "auto_night"),
            typicalRange = "manual_day / manual_night — ручная тема (ГУ или локальная тема " +
                "приложения, если оно отвязано от системы), auto_day / auto_night — штатный " +
                "авто день/ночь ГУ с текущим разрешением. Один триггер «любой день/ночь» — " +
                "сигнал app_theme. Тот же режим, что у переключателя темы и действий " +
                "toggle_app_day_night_theme / enable_head_unit_auto_theme.",
        ),
        state(
            AutomationSignalId.APP_THEME,
            "Тема приложения: сейчас",
            appOnly,
            listOf("day", "night"),
            typicalRange = "Эффективная тема в моменте: day — светлая, night — тёмная, " +
                "ручная или разрешённая авто-режимом. Триггер «День» срабатывает и на ручной, " +
                "и на авто-день; «Ночь» — и на ручную, и на авто-ночь.",
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
