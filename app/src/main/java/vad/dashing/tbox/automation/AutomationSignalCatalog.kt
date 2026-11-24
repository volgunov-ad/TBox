package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

import java.text.Collator
import java.util.Locale
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.isSpeedLimiterHiddenFromAutomationPicker
import vad.dashing.tbox.mbcan.AccCruiseDomain
import vad.dashing.tbox.mbcan.AccStatusDomain
import vad.dashing.tbox.mbcan.HuSystemModeDomain
import vad.dashing.tbox.mbcan.IgnitionKeyDomain
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.WiperStsDomain

data class AutomationSignalNamedValue(
    val value: String,
    val label: String,
)

/** User-facing group header for signal pickers. Pure Kotlin, unit-testable. */
enum class AutomationSignalGroup(val label: String) {
    ENGINE(ruEn("Двигатель и КПП", "Engine and gearbox")),
    CLIMATE(ruEn("Климат и воздух", "Climate and air")),
    BODY(ruEn("Кузов и комфорт", "Body and comfort")),
    ADAS(ruEn("ADAS и безопасность", "ADAS and safety")),
    AUDIO(ruEn("Аудио", "Audio")),
    GEO(ruEn("Геопозиция", "Location")),
    NETWORK(ruEn("Сеть и модем", "Network and modem")),
    PHONE(ruEn("Телефон", "Phone")),
    MEDIA(ruEn("Медиа", "Media")),
    TRIPS(ruEn("Поездки и топливо", "Trips and fuel")),
    ESP(ruEn("ESP-компаньон", "ESP companion")),
    APP(ruEn("Приложение", "App")),
    DCL("DCL"),
    CAMERAS(ruEn("Камеры", "Cameras")),
    ROADS(ruEn("Дороги", "Roads")),
}

data class AutomationSignalDescriptor(
    val id: AutomationSignalId,
    val label: String,
    val unit: String = "",
    val sources: Set<AutomationSignalSource>,
    val group: AutomationSignalGroup,
    val stateOptions: List<String> = emptyList(),
    val namedValues: List<AutomationSignalNamedValue> = emptyList(),
    val typicalRange: String = "",
    /** Русские синонимы для поиска (в дополнение к label и storageKey). */
    val keywords: List<String> = emptyList(),
) {
    fun valueHint(): String {
        val named = formatNamedValues(namedValues)
        return when {
            named.isNotEmpty() && typicalRange.isNotEmpty() ->
                ruEn("Возможные значения: $named. $typicalRange", "Possible values: $named. $typicalRange")
            named.isNotEmpty() -> ruEn("Возможные значения: $named", "Possible values: $named")
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
    private val windowPositionTypicalRange =
        ruEn("Только ГУ. Положение 0…100 %: 0 закрыто, штатная щель 20, комфортное открытие 80, ", "HU only. Position 0…100 %: 0 closed, stock vent 20, comfort open 80, ") +
            ruEn("100 полностью открыто; open (Открыто) — любое открытое положение: 20, 80, 100 и ", "100 fully open; open (Open) — any open position: 20, 80, 100 and ") +
            ruEn("промежуточное (A9 BCM отдаёт там −1). A9 BCM getVehicleWindow; A10 *_WIN_Position.", "in between (A9 BCM returns −1 there). A9 BCM getVehicleWindow; A10 *_WIN_Position.")
    private val turnSignalTypicalRange =
        ruEn("Только ГУ. Бинарный статус защёлки поворотника: on с первого кадра мигания ", "HU only. Binary turn-signal latch: on from the first flash frame ") +
            ruEn("(порог «≥4 вспышки» намеренного перестроения не применяется), off через 2,5 с ", "(the “≥4 flashes” intentional lane-change threshold is not applied), off 2.5 s ") +
            ruEn("после последнего мигания. Аварийка защёлку не включает. ", "after the last flash. Hazard lights do not set the latch. ") +
            ruEn("A9 eMBCAN_VEHICLE_TURNLIGHT (мигает), A10 DirectionInd CEM 1-bit (стабилен).", "A9 eMBCAN_VEHICLE_TURNLIGHT (flashing), A10 DirectionInd CEM 1-bit (steady).")

    val entries: List<AutomationSignalDescriptor> = listOf(
        number(
            AutomationSignalId.ENGINE_RPM,
            ruEn("Обороты двигателя", "Engine speed"),
            ruEn("об/мин", "rpm"),
            bothSources,
            typicalRange = ruEn("Значения в об/мин, обычно 0…8000", "Values in rpm, usually 0…8000"),
        ),
        number(
            AutomationSignalId.CAR_SPEED,
            ruEn("Скорость автомобиля", "Vehicle speed"),
            ruEn("км/ч", "km/h"),
            bothSources,
            typicalRange = ruEn("Значения в км/ч, обычно 0…240", "Values in km/h, usually 0…240"),
        ),
        number(
            AutomationSignalId.ENGINE_TEMPERATURE,
            ruEn("Температура двигателя", "Engine temperature"),
            "°C",
            bothSources,
            typicalRange = ruEn("Значения в °C. На Android 9 только TBox: mbCAN всегда отдаёт 0", "Values in °C. On Android 9, TBox only: mbCAN always returns 0"),
        ),
        number(
            AutomationSignalId.OUTSIDE_TEMPERATURE,
            ruEn("Температура снаружи", "Outside temperature"),
            "°C",
            bothSources,
            typicalRange = ruEn("Значения в °C", "Values in °C"),
        ),
        number(
            AutomationSignalId.INSIDE_TEMPERATURE,
            ruEn("Температура в салоне", "Cabin temperature"),
            "°C",
            tboxOnly,
            typicalRange = ruEn("Значения в °C", "Values in °C"),
        ),
        number(
            AutomationSignalId.FUEL_LEVEL_PERCENT,
            ruEn("Уровень топлива", "Fuel level"),
            "%",
            bothSources,
            typicalRange = ruEn("Значения в %, обычно 0…100", "Values in %, usually 0…100"),
        ),
        number(
            AutomationSignalId.ODOMETER_KM,
            ruEn("Одометр", "Odometer"),
            ruEn("км", "km"),
            bothSources,
            typicalRange = ruEn("Значения в км", "Values in km"),
        ),
        number(
            AutomationSignalId.CURRENT_FUEL_CONSUMPTION,
            ruEn("Текущий расход топлива", "Current fuel consumption"),
            ruEn("л/100 км", "L/100 km"),
            bothSources,
            typicalRange = ruEn("Значения в л/100 км", "Values in L/100 km"),
        ),
        number(
            AutomationSignalId.DISTANCE_TO_EMPTY_KM,
            ruEn("Запас хода", "Range"),
            ruEn("км", "km"),
            bothSources,
            typicalRange = ruEn("Значения в км", "Values in km"),
        ),
        number(
            AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM,
            ruEn("До обслуживания", "Distance to service"),
            ruEn("км", "km"),
            bothSources,
            typicalRange = ruEn("Значения в км", "Values in km"),
        ),
        number(
            AutomationSignalId.VOLTAGE,
            ruEn("Напряжение", "Voltage"),
            ruEn("В", "V"),
            bothSources,
            typicalRange = ruEn("Значения в В, обычно 11…15. На Android 10 головное устройство отдаёт кадр MCU 0x8305 (шаг 0.1 В)", "Values in V, usually 11…15. On Android 10 the head unit sends MCU frame 0x8305 (step 0.1 V)"),
        ),
        number(
            AutomationSignalId.STEERING_ANGLE,
            ruEn("Угол руля", "Steering angle"),
            "°",
            bothSources,
            typicalRange = ruEn("Значения в градусах", "Values in degrees"),
        ),
        number(
            AutomationSignalId.STEERING_SPEED,
            ruEn("Скорость вращения руля", "Steering speed"),
            ruEn("°/с", "°/s"),
            bothSources,
            typicalRange = ruEn("Значения в °/с. На Android 10 только TBox: в VHAL её нет", "Values in °/s. On Android 10, TBox only: it is not in VHAL"),
        ),
        number(
            AutomationSignalId.CRUISE_SET_SPEED,
            ruEn("Уставка круиза", "Cruise setpoint"),
            ruEn("км/ч", "km/h"),
            bothSources,
            typicalRange = ruEn("Значения в км/ч", "Values in km/h"),
        ),
        state(AutomationSignalId.GEAR_MODE, ruEn("Режим КПП", "Gearbox mode"), bothSources, listOf("P", "R", "N", "D")),
        state(
            AutomationSignalId.ACC_STATUS,
            ruEn("Статус ACC (ключ)", "ACC status (key)"),
            headUnitOnly,
            AccStatusDomain.STATE_OPTIONS,
            typicalRange = ruEn("Android 9: AccStatus 4=ACC ON, 5=ON, 0…3=выкл. ", "Android 9: AccStatus 4=ACC ON, 5=ON, 0…3=off. ") +
                ruEn("Android 10: MCU_REPLY_ACC_STATUS 1 и 2=ACC ON, 0 и 3=выкл (шкала не 4/5).", "Android 10: MCU_REPLY_ACC_STATUS 1 and 2=ACC ON, 0 and 3=off (scale is not 4/5)."),
        ),
        state(
            AutomationSignalId.IGNITION_KEY,
            ruEn("Положение ключа", "Key position"),
            headUnitOnly,
            IgnitionKeyDomain.OPTIONS,
            typicalRange = ruEn("Положение ключа CEM_2_KeySts / getIgnSts: ", "Key position CEM_2_KeySts / getIgnSts: ") +
                ruEn("0=выключено (off), 1=ACC (acc), 2=зажигание (on), 3=стартер (crank). ", "0=off (off), 1=ACC (acc), 2=ignition (on), 3=starter (crank). ") +
                ruEn("Android 9 и Android 10 одна шкала. Это не «Статус ACC (ключ)».", "Android 9 and Android 10 share one scale. This is not “ACC status (key)”."),
            namedValues = listOf(
                AutomationSignalNamedValue("off", ruEn("Выключено", "Off")),
                AutomationSignalNamedValue("acc", "ACC"),
                AutomationSignalNamedValue("on", ruEn("Зажигание", "Ignition")),
                AutomationSignalNamedValue("crank", ruEn("Стартер", "Starter")),
            ),
        ),
        state(
            AutomationSignalId.HU_SYSTEM_MODE,
            ruEn("Режим головного устройства", "Head unit mode"),
            headUnitOnly,
            HuSystemModeDomain.OPTIONS,
            typicalRange = ruEn("Только Android 9, eMBCAN_SYSTEMMODE. ", "Android 9 only, eMBCAN_SYSTEMMODE. ") +
                ruEn("1=ожидание (standby), 2=работа (active), ", "1=waiting (standby), 2=running (active), ") +
                ruEn("6=15 минут работы ГУ после снятия ACC/зажигания (hold_15m), 7=сон (sleep). ", "6=15 minutes of HU runtime after ACC/ignition off (hold_15m), 7=sleep (sleep). ") +
                ruEn("hold_15m — не дистанционный запуск. На Android 10 значения нет.", "hold_15m — not a remote start. No value on Android 10."),
            namedValues = listOf(
                AutomationSignalNamedValue("standby", ruEn("Ожидание", "Standby")),
                AutomationSignalNamedValue("active", ruEn("Работа", "Active")),
                AutomationSignalNamedValue("hold_15m", ruEn("15 минут работы ГУ", "15 minutes of HU runtime")),
                AutomationSignalNamedValue("sleep", ruEn("Сон", "Sleep")),
            ),
        ),
        state(
            AutomationSignalId.ACC_CRUISE_STATE,
            ruEn("Состояние ACC (круиз)", "ACC state (cruise)"),
            headUnitOnly,
            AccCruiseDomain.ACC_AUTOMATION_STATE_OPTIONS,
            typicalRange = ruEn("Только ГУ. Off/Standby/Active/Override/Fault из ACCMode: ", "HU only. Off/Standby/Active/Override/Fault from ACCMode: ") +
                ruEn("0=off; 1,2,6=standby; 3,4,5=active; 7=override (газ перебивает ACC); 9=fault. ", "0=off; 1,2,6=standby; 3,4,5=active; 7=override (throttle overrides ACC); 9=fault. ") +
                ruEn("Не путать со «Статус ACC (ключ)» (acc_status).", "Do not confuse with “ACC status (key)” (acc_status)."),
        ),
        state(
            AutomationSignalId.CCS_CRUISE_STATE,
            ruEn("Состояние CCS (круиз)", "CCS state (cruise)"),
            headUnitOnly,
            AccCruiseDomain.CCS_AUTOMATION_STATE_OPTIONS,
            typicalRange = ruEn("Только ГУ. Off/Standby/Active из CruiseControlStatus: ", "HU only. Off/Standby/Active from CruiseControlStatus: ") +
                ruEn("0=off; 1=active; 2=standby. Fault нет (только у ACC).", "0=off; 1=active; 2=standby. No Fault (ACC only)."),
        ),
        number(
            AutomationSignalId.GAS_PEDAL,
            ruEn("Педаль газа", "Accelerator pedal"),
            "%",
            headUnitOnly,
            typicalRange = ruEn("Значения в %, обычно 0…100. Невалидный EMS-флаг или вне диапазона — нет значения.", "Values in %, usually 0…100. An invalid EMS flag or a value out of range — no value."),
        ),
        state(
            AutomationSignalId.BRAKE_PEDAL,
            ruEn("Педаль тормоза", "Brake pedal"),
            headUnitOnly,
            binaryStates,
            typicalRange = ruEn("Только ГУ. A9 BrakePedalSts: 2=нажата (on), 1=отпущена (off); ", "HU only. ") +
                ruEn("A10 VHAL: 1=нажата, 0=отпущена. Прочие — нет значения.", "A10 VHAL: 1=pressed, 0=released. Others — no value."),
        ),
        number(
            AutomationSignalId.CURRENT_GEAR,
            ruEn("Текущая передача", "Current gear"),
            "",
            bothSources,
            typicalRange = ruEn("Номер передачи в D, обычно 1…8; вне D часто 0. ", "Gear number in D, usually 1…8; outside D it is often 0. ") +
                "A9 BCM getGSM_GearShiftPos / A10 VHAL GSM_GearShiftPos; TBox gearBoxCurrentGear.",
        ),
        number(
            AutomationSignalId.TARGET_GEAR,
            ruEn("Целевая передача", "Target gear"),
            "",
            bothSources,
            typicalRange = ruEn("Подготовленная / целевая передача. ", "Prepared / target gear. ") +
                ruEn("A10 VHAL EMS_TargetGearPosition; на Android 9 только TBox gearBoxPreparedGear (в mbCAN нет).", "A10 VHAL EMS_TargetGearPosition; on Android 9, TBox gearBoxPreparedGear only (not in mbCAN)."),
        ),
        number(
            AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE,
            ruEn("Давление переднего левого колеса", "Front left tire pressure"),
            ruEn("бар", "bar"),
            bothSources,
            typicalRange = ruEn("Значения в бар", "Values in bar"),
        ),
        number(
            AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE,
            ruEn("Давление переднего правого колеса", "Front right tire pressure"),
            ruEn("бар", "bar"),
            bothSources,
            typicalRange = ruEn("Значения в бар", "Values in bar"),
        ),
        number(
            AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE,
            ruEn("Давление заднего левого колеса", "Rear left tire pressure"),
            ruEn("бар", "bar"),
            bothSources,
            typicalRange = ruEn("Значения в бар", "Values in bar"),
        ),
        number(
            AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE,
            ruEn("Давление заднего правого колеса", "Rear right tire pressure"),
            ruEn("бар", "bar"),
            bothSources,
            typicalRange = ruEn("Значения в бар", "Values in bar"),
        ),
        number(
            AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE,
            ruEn("Температура переднего левого колеса", "Front left tire temperature"),
            "°C",
            bothSources,
            typicalRange = ruEn("Значения в °C", "Values in °C"),
        ),
        number(
            AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE,
            ruEn("Температура переднего правого колеса", "Front right tire temperature"),
            "°C",
            bothSources,
            typicalRange = ruEn("Значения в °C", "Values in °C"),
        ),
        number(
            AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE,
            ruEn("Температура заднего левого колеса", "Rear left tire temperature"),
            "°C",
            bothSources,
            typicalRange = ruEn("Значения в °C", "Values in °C"),
        ),
        number(
            AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE,
            ruEn("Температура заднего правого колеса", "Rear right tire temperature"),
            "°C",
            bothSources,
            typicalRange = ruEn("Значения в °C", "Values in °C"),
        ),
        number(
            AutomationSignalId.INSIDE_AIR_QUALITY,
            ruEn("Качество воздуха в салоне", "Cabin air quality"),
            "",
            bothSources,
            typicalRange = ruEn("PM2.5, мкг/м³; валидно 1…65534", "PM2.5, µg/m³; valid 1…65534"),
        ),
        number(
            AutomationSignalId.OUTSIDE_AIR_QUALITY,
            ruEn("Качество наружного воздуха", "Outside air quality"),
            "",
            bothSources,
            typicalRange = ruEn("PM2.5, мкг/м³; валидно 1…65534", "PM2.5, µg/m³; valid 1…65534"),
        ),
        state(AutomationSignalId.STEERING_WHEEL_HEAT, ruEn("Обогрев руля", "Steering wheel heat"), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.WIPER_MAINTENANCE,
            ruEn("Сервисное положение дворников", "Wiper service position"),
            headUnitOnly,
            binaryStates,
        ),
        state(
            AutomationSignalId.WIPER_STS,
            ruEn("Режим дворников", "Wiper mode"),
            headUnitOnly,
            WiperStsDomain.STATE_OPTIONS,
            typicalRange = ruEn("Только ГУ. TTG: 0=выкл, 1=INT (на части комплектаций иконка AUTO), ", "HU only. TTG: 0=off, 1=INT (on some trims the icon is AUTO), ") +
                ruEn("2=Low, 3=High. Не сервисное положение дворников.", "2=Low, 3=High. Not the wiper service position."),
        ),
        state(
            AutomationSignalId.RAIN_DETECTED,
            ruEn("Детектор дождя", "Rain sensor"),
            headUnitOnly,
            binaryStates,
            typicalRange = ruEn("Только ГУ. CEM 1-bit: 1=дождь (S_RAIN TRUE), 0=сухо. ", "HU only. CEM 1-bit: 1=rain (S_RAIN TRUE), 0=dry. ") +
                "A9 BCM getRainDetectedSts, A10 R_0400_CEM_2_RainDetected. " +
                ruEn("Не отказ датчика RainSensorFailSts.", "Not a RainSensorFailSts sensor failure."),
        ),
        state(
            AutomationSignalId.HIGH_BEAM,
            ruEn("Дальний свет", "High beam"),
            headUnitOnly,
            binaryStates,
            typicalRange = ruEn("Только ГУ. Бинарный статус: включён/выключен. ", "HU only. Binary status: on/off. ") +
                "A9 BCM stLightSts.nHighBeamSts, A10 R_0404_CEM_2_HighBeamSts (CEM 1-bit). " +
                ruEn("Не режим LIGHTCONTROL 1…4.", "Not LIGHTCONTROL mode 1…4."),
        ),
        state(
            AutomationSignalId.HAZARD,
            ruEn("Аварийка", "Hazard lights"),
            headUnitOnly,
            binaryStates,
            typicalRange = ruEn("Только ГУ. Бинарный статус: включена/выключена. ", "HU only. Binary status: on/off. ") +
                ruEn("A9 BCM nTurnSts+nDirectionLightSts: 3+1 включена (кнопка и открытый багажник), ", "A9 BCM nTurnSts+nDirectionLightSts: 3+1 on (button and trunk open), ") +
                ruEn("0+0 / 2+2 / 1+1 выключена. Не мигающий объект поворотников и не nHazardLightSts. ", "0+0 / 2+2 / 1+1 is off. Not a flashing turn-signal object and not nHazardLightSts. ") +
                "A10 R_0400_CEM_1_HazardLightSW (CEM 1-bit).",
        ),
        state(
            AutomationSignalId.TURN_SIGNAL_LEFT,
            ruEn("Поворотник левый", "Left turn signal"),
            headUnitOnly,
            binaryStates,
            typicalRange = turnSignalTypicalRange,
        ),
        state(
            AutomationSignalId.TURN_SIGNAL_RIGHT,
            ruEn("Поворотник правый", "Right turn signal"),
            headUnitOnly,
            binaryStates,
            typicalRange = turnSignalTypicalRange,
        ),
        state(
            AutomationSignalId.TURN_STABILIZED,
            ruEn("Поворотник (стабилизированный)", "Turn signal (stabilized)"),
            headUnitOnly,
            binaryStates,
            typicalRange = ruEn("Устаревший (скрыт в пикере). Любой борт: on если защёлкнут левый ", "Legacy (hidden in the picker). Either side: on if the left ") +
                ruEn("или правый; аварийка не включает. Предпочитайте turn_signal_left / ", "or the right is latched; hazard lights do not turn it on. Prefer turn_signal_left / ") +
                "turn_signal_right. $turnSignalTypicalRange",
        ),
        state(
            AutomationSignalId.DRL,
            ruEn("ДХО", "DRL"),
            headUnitOnly,
            binaryStates,
            typicalRange = ruEn("Только ГУ. Бинарный статус: включены/выключены. ", "HU only. Binary status: on/off. ") +
                ruEn("A9 BCM stLightSts.nDRLSts: 2 включены / 1 выключены ", "A9 BCM stLightSts.nDRLSts: 2 on / 1 off ") +
                ruEn("(журнал: гаснут при аварийке и при открытом багажнике). ", "(log: they go out with the hazard lights and with the trunk open). ") +
                ruEn("A10 R_0404_CEM_2_DRLSts — CEM 1-bit, шкала на этой машине не подтверждена.", "A10 R_0404_CEM_2_DRLSts — CEM 1-bit, scale not confirmed on this car."),
        ),
        state(
            AutomationSignalId.EPB_PARK_LAMP,
            ruEn("Лампа EPB (паркинг)", "EPB lamp (parking)"),
            headUnitOnly,
            binaryStates,
            typicalRange = ruEn("Только ГУ. Бинарный статус лампы EPB. ", "HU only. Binary EPB lamp status. ") +
                ruEn("A9 BCM getEPBParkLampSts (предположена шкала CEM-switch 2=on); ", "A9 BCM getEPBParkLampSts (assumed CEM-switch scale 2=on); ") +
                ruEn("A10 VHAL R_0900_ICM_7_EPBWarningLampSts (прокси, CEM 1-bit) — уточнить на машине.", "A10 VHAL R_0900_ICM_7_EPBWarningLampSts (proxy, CEM 1-bit) — confirm on the car."),
        ),
        state(
            AutomationSignalId.ENGINE_OIL_PRESSURE,
            ruEn("Давление масла (предупреждение)", "Oil pressure (warning)"),
            headUnitOnly,
            binaryStates,
            typicalRange = ruEn("Только ГУ. on = предупреждение (проблема), off = норма. ", "HU only. on = warning (a problem), off = normal. ") +
                ruEn("A9 ICM drive info getICM_EngineOil (type 44, только poll); ", "A9 ICM drive info getICM_EngineOil (type 44, poll only); ") +
                ruEn("A10 VHAL R_0900_ICM_4_Engine_Oil_Pressure (CEM 1-bit, TBD на машине).", "A10 VHAL R_0900_ICM_4_Engine_Oil_Pressure (CEM 1-bit, TBD on the car)."),
        ),
        state(
            AutomationSignalId.BRAKE_FLUID,
            ruEn("Тормозная жидкость (предупреждение)", "Brake fluid (warning)"),
            headUnitOnly,
            binaryStates,
            typicalRange = ruEn("Только ГУ. on = предупреждение (проблема), off = норма. ", "HU only. on = warning (a problem), off = normal. ") +
                ruEn("A9 ICM drive info getICM_Brakefluid (type 44, только poll); ", "A9 ICM drive info getICM_Brakefluid (type 44, poll only); ") +
                ruEn("A10 VHAL R_0900_ICM_4_Brake_Fuel_Level (CEM 1-bit, TBD на машине).", "A10 VHAL R_0900_ICM_4_Brake_Fuel_Level (CEM 1-bit, TBD on the car)."),
        ),
        number(
            AutomationSignalId.FRM_DX_TAR_OBJ,
            "FRM DxTarObj",
            "",
            headUnitOnly,
            typicalRange = ruEn("Только ГУ. Сырое FRM_3_DxTarObj при ObjValid=1; иначе нет значения.", "HU only. Raw FRM_3_DxTarObj when ObjValid=1; otherwise no value."),
        ),
        state(
            AutomationSignalId.SUNSHADE,
            ruEn("Шторка", "Sunshade"),
            headUnitOnly,
            BodyComfortDomain.SHADE_STATE_OPTIONS,
            typicalRange = ruEn("Только ГУ. Проценты 0…100 с шагом 10 (0% закрыто, 100% открыто). ", "HU only. Percent 0…100 in steps of 10 (0% closed, 100% open). ") +
                ruEn("A9: canGet/cfg 46 (в BCM шторки нет). A10: Abat_VentCMDSts.", "A9: canGet/cfg 46 (no sunshade in BCM). A10: Abat_VentCMDSts."),
        ),
        state(
            AutomationSignalId.SUNROOF,
            ruEn("Люк", "Sunroof"),
            headUnitOnly,
            BodyComfortDomain.ROOF_STATE_OPTIONS,
            typicalRange = ruEn("Только ГУ. Проценты 0…100 с шагом 10 + tilt (откинут: чтение 102 ", "HU only. Percent 0…100 in steps of 10 + tilt (tilted: reading 102 ") +
                ruEn("или 10%). A9: canGet/cfg 45 (не BCM getSunRoof: там −1). A10: PSRFCMDSts.", "or 10%). A9: canGet/cfg 45 (not BCM getSunRoof: it returns −1 there). A10: PSRFCMDSts."),
        ),
        state(
            AutomationSignalId.WINDOW_FRONT_LEFT,
            ruEn("Стекло переднее левое", "Front left window"),
            headUnitOnly,
            BodyComfortDomain.WINDOW_STATE_OPTIONS,
            typicalRange = windowPositionTypicalRange,
        ),
        state(
            AutomationSignalId.WINDOW_FRONT_RIGHT,
            ruEn("Стекло переднее правое", "Front right window"),
            headUnitOnly,
            BodyComfortDomain.WINDOW_STATE_OPTIONS,
            typicalRange = windowPositionTypicalRange,
        ),
        state(
            AutomationSignalId.WINDOW_REAR_LEFT,
            ruEn("Стекло заднее левое", "Rear left window"),
            headUnitOnly,
            BodyComfortDomain.WINDOW_STATE_OPTIONS,
            typicalRange = windowPositionTypicalRange,
        ),
        state(
            AutomationSignalId.WINDOW_REAR_RIGHT,
            ruEn("Стекло заднее правое", "Rear right window"),
            headUnitOnly,
            BodyComfortDomain.WINDOW_STATE_OPTIONS,
            typicalRange = windowPositionTypicalRange,
        ),
        state(AutomationSignalId.PARKING_RADAR, ruEn("Парковочный радар", "Parking radar"), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.REAR_FOG,
            ruEn("Задний противотуманный фонарь", "Rear fog light"),
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
            typicalRange = ruEn("Стандартный режим движения ГУ (ECO, NOR, SPT и др.)", "HU standard drive mode (ECO, NOR, SPT, and others)"),
        ),
        state(
            AutomationSignalId.HEADLIGHT_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HEADLIGHT_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.headlightOptions,
        ),
        state(AutomationSignalId.REVERSE_GEAR, ruEn("Задняя передача", "Reverse"), headUnitOnly, binaryStates),
        state(
            AutomationSignalId.FRONT_LEFT_SEAT_MODE,
            ruEn("Левое переднее сиденье", "Front left seat"),
            headUnitOnly,
            frontSeatStates,
        ),
        state(
            AutomationSignalId.FRONT_RIGHT_SEAT_MODE,
            ruEn("Правое переднее сиденье", "Front right seat"),
            headUnitOnly,
            frontSeatStates,
        ),
        state(
            AutomationSignalId.REAR_LEFT_SEAT_MODE,
            ruEn("Левое заднее сиденье", "Rear left seat"),
            headUnitOnly,
            rearSeatStates,
        ),
        state(
            AutomationSignalId.REAR_RIGHT_SEAT_MODE,
            ruEn("Правое заднее сиденье", "Rear right seat"),
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
            typicalRange = ruEn("Уровень 1…4", "Level 1…4"),
        ),
        state(AutomationSignalId.REAR_WIPER, AutomationParameterLabels.signalLabel(AutomationSignalId.REAR_WIPER), headUnitOnly, binaryStates),
        state(AutomationSignalId.MIRROR_AUTO_FOLD, AutomationParameterLabels.signalLabel(AutomationSignalId.MIRROR_AUTO_FOLD), headUnitOnly, binaryStates),
        number(
            AutomationSignalId.LOW_BEAM_HEIGHT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.LOW_BEAM_HEIGHT),
            "",
            headUnitOnly,
            typicalRange = ruEn("Уровень 1…4", "Level 1…4"),
        ),
        number(
            AutomationSignalId.TURN_FLASH_COUNT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.TURN_FLASH_COUNT),
            "",
            headUnitOnly,
            typicalRange = ruEn("CAN 1/2/3 → 3/5/7 миганий", "CAN 1/2/3 → 3/5/7 flashes"),
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
            AutomationSignalId.ACC_TIME_GAP,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ACC_TIME_GAP),
            headUnitOnly,
            AutomationSignalStateEncoding.accTimeGapOptions,
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
            typicalRange = ruEn("Только Android 9 mbCAN", "Android 9 mbCAN only"),
        ),
        state(
            AutomationSignalId.FRAGRANCE_CONCENTRATION,
            AutomationParameterLabels.signalLabel(AutomationSignalId.FRAGRANCE_CONCENTRATION),
            headUnitOnly,
            AutomationSignalStateEncoding.fragranceConcentrationOptions,
            typicalRange = ruEn("Только Android 9 mbCAN", "Android 9 mbCAN only"),
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
            typicalRange = ruEn("Температура в °C", "Temperature in °C"),
        ),
        number(
            AutomationSignalId.HVAC_TEMPERATURE_RIGHT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_TEMPERATURE_RIGHT),
            "°C",
            headUnitOnly,
            typicalRange = ruEn("Температура в °C", "Temperature in °C"),
        ),
        number(
            AutomationSignalId.HVAC_FAN_SPEED,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_FAN_SPEED),
            "",
            headUnitOnly,
            typicalRange = ruEn("Скорость вентилятора 1…7", "Fan speed 1…7"),
        ),
        state(AutomationSignalId.HVAC_FRONT_OFF, AutomationParameterLabels.signalLabel(AutomationSignalId.HVAC_FRONT_OFF), headUnitOnly, binaryStates),
        state(AutomationSignalId.HUD, AutomationParameterLabels.signalLabel(AutomationSignalId.HUD), headUnitOnly, binaryStates),
        number(
            AutomationSignalId.HUD_HEIGHT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HUD_HEIGHT),
            "",
            headUnitOnly,
            typicalRange = ruEn("Уровень 1…10", "Level 1…10"),
        ),
        number(
            AutomationSignalId.HUD_BRIGHTNESS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HUD_BRIGHTNESS),
            "",
            headUnitOnly,
            typicalRange = ruEn("Уровень 1…10 — яркость проекции HUD (не экран ГУ и не приборная панель).", "Level 1…10 — HUD projection brightness (not the HU screen and not the instrument cluster)."),
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
            typicalRange = ruEn("Уровень 1…10 — яркость комбинации приборов / ICM (не экран ГУ и не HUD).", "Level 1…10 — instrument cluster / ICM brightness (not the HU screen and not the HUD)."),
        ),
        number(
            AutomationSignalId.OVERSPEED_ALARM,
            AutomationParameterLabels.signalLabel(AutomationSignalId.OVERSPEED_ALARM),
            ruEn("км/ч", "km/h"),
            headUnitOnly,
            typicalRange = ruEn("30…230 км/ч с шагом 5", "30…230 km/h in steps of 5"),
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
            AutomationSignalId.DOOR_FRONT_LEFT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.DOOR_FRONT_LEFT),
            headUnitOnly,
            AutomationSignalStateEncoding.doorAjarOptions,
        ),
        state(
            AutomationSignalId.DOOR_FRONT_RIGHT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.DOOR_FRONT_RIGHT),
            headUnitOnly,
            AutomationSignalStateEncoding.doorAjarOptions,
        ),
        state(
            AutomationSignalId.DOOR_REAR_LEFT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.DOOR_REAR_LEFT),
            headUnitOnly,
            AutomationSignalStateEncoding.doorAjarOptions,
        ),
        state(
            AutomationSignalId.DOOR_REAR_RIGHT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.DOOR_REAR_RIGHT),
            headUnitOnly,
            AutomationSignalStateEncoding.doorAjarOptions,
        ),
        state(
            AutomationSignalId.AUDIO_VOLUME_SPEED_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_VOLUME_SPEED_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.audioVolumeSpeedOptions,
            typicalRange = ruEn("Только Android 9 mbCAN", "Android 9 mbCAN only"),
        ),
        number(
            AutomationSignalId.AUDIO_KEY_TONE_VOLUME,
            AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_KEY_TONE_VOLUME),
            "",
            headUnitOnly,
            typicalRange = ruEn("0 выкл, 1…3 уровень. Только Android 9 mbCAN", "0 off, 1…3 level. Android 9 mbCAN only"),
        ),
        state(
            AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME,
            AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME),
            headUnitOnly,
            AutomationSignalStateEncoding.audioRadarVolumeOptions,
            typicalRange = ruEn("Только Android 9 mbCAN", "Android 9 mbCAN only"),
        ),
        state(
            AutomationSignalId.AUDIO_EQ_MODE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_EQ_MODE),
            headUnitOnly,
            AutomationSignalStateEncoding.audioEqModeOptions,
            typicalRange = ruEn("Только Android 9 mbCAN", "Android 9 mbCAN only"),
        ),
        number(AutomationSignalId.AUDIO_EQ_BASS, AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_EQ_BASS), "", headUnitOnly, typicalRange = ruEn("Уровень 0…14, Android 9", "Level 0…14, Android 9")),
        number(AutomationSignalId.AUDIO_EQ_MIDDLE, AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_EQ_MIDDLE), "", headUnitOnly, typicalRange = ruEn("Уровень 0…14, Android 9", "Level 0…14, Android 9")),
        number(AutomationSignalId.AUDIO_EQ_TREBLE, AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_EQ_TREBLE), "", headUnitOnly, typicalRange = ruEn("Уровень 0…14, Android 9", "Level 0…14, Android 9")),
        number(AutomationSignalId.AUDIO_BALANCE, AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_BALANCE), "", headUnitOnly, typicalRange = ruEn("Уровень 0…14, Android 9", "Level 0…14, Android 9")),
        number(AutomationSignalId.AUDIO_FADER, AutomationParameterLabels.signalLabel(AutomationSignalId.AUDIO_FADER), "", headUnitOnly, typicalRange = ruEn("Уровень 0…14, Android 9", "Level 0…14, Android 9")),
        AutomationSignalDescriptor(
            id = AutomationSignalId.GEO_POSITION,
            label = ruEn("Геопозиция", "Location"),
            sources = appOnly,
            group = AutomationSignalGroup.GEO,
            typicalRange = ruEn("Текущая точка GeoDisplay (GNSS или подмена)", "Current GeoDisplay point (GNSS or mock)"),
            keywords = signalKeywords(AutomationSignalId.GEO_POSITION),
        ),
        number(
            AutomationSignalId.GEO_ALTITUDE_M,
            AutomationParameterLabels.signalLabel(AutomationSignalId.GEO_ALTITUDE_M),
            ruEn("м", "m"),
            appOnly,
            typicalRange = ruEn("Высота GeoDisplay, м. Unavailable при NONE/LOST.", "GeoDisplay altitude, m. Unavailable on NONE/LOST."),
        ),
        number(
            AutomationSignalId.GEO_SPEED_KMH,
            AutomationParameterLabels.signalLabel(AutomationSignalId.GEO_SPEED_KMH),
            ruEn("км/ч", "km/h"),
            appOnly,
            typicalRange = ruEn("Отображаемая скорость GeoDisplay (GNSS/CAN/retention). ", "GeoDisplay displayed speed (GNSS/CAN/retention). ") +
                ruEn("Unavailable при NONE/LOST.", "Unavailable on NONE/LOST."),
        ),
        state(
            AutomationSignalId.GEO_SPEED_SOURCE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.GEO_SPEED_SOURCE),
            appOnly,
            GeoAutomationSignals.SPEED_SOURCE_OPTIONS,
            typicalRange = ruEn("gnss / can / retention — источник скорости GeoDisplay; ", "gnss / can / retention — GeoDisplay speed source; ") +
                ruEn("none — нет LIVE/RETAINING снимка.", "none — no LIVE/RETAINING snapshot."),
            namedValues = listOf(
                AutomationSignalNamedValue("gnss", "GNSS"),
                AutomationSignalNamedValue("can", "CAN"),
                AutomationSignalNamedValue("retention", ruEn("Удержание", "Hold")),
                AutomationSignalNamedValue("none", ruEn("Нет данных", "No data")),
            ),
        ),
        number(
            AutomationSignalId.GEO_BEARING_DEG,
            AutomationParameterLabels.signalLabel(AutomationSignalId.GEO_BEARING_DEG),
            "°",
            appOnly,
            typicalRange = ruEn("Курс / bearing, градусы. Unavailable без надёжного bearing или при NONE/LOST.", "Course / bearing, degrees. Unavailable without a reliable bearing or on NONE/LOST."),
        ),
        number(
            AutomationSignalId.GEO_SATELLITES_VISIBLE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.GEO_SATELLITES_VISIBLE),
            "",
            appOnly,
            typicalRange = ruEn("Спутники в поле зрения. Unavailable при NONE/LOST.", "Satellites in view. Unavailable on NONE/LOST."),
        ),
        number(
            AutomationSignalId.GEO_SATELLITES_USED,
            AutomationParameterLabels.signalLabel(AutomationSignalId.GEO_SATELLITES_USED),
            "",
            appOnly,
            typicalRange = ruEn("Спутники в решении фикса. Unavailable при NONE/LOST.", "Satellites used in the fix. Unavailable on NONE/LOST."),
        ),
        state(
            AutomationSignalId.GEO_FIX_VALID,
            AutomationParameterLabels.signalLabel(AutomationSignalId.GEO_FIX_VALID),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off — правдивость GNSS (gnssTruthful), отдельно от locate_status.", "on/off — GNSS truthfulness (gnssTruthful), separate from locate_status."),
        ),
        state(
            AutomationSignalId.GEO_SOURCE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.GEO_SOURCE),
            appOnly,
            GeoAutomationSignals.GEO_SOURCE_OPTIONS,
            typicalRange = ruEn("Активный источник геопозиции из настроек: tbox / companion / android / usb / phone.", "Active location source from settings: tbox / companion / android / usb / phone."),
        ),
        state(AutomationSignalId.ESP_GPIO_IN_0, ruEn("ESP-вход 0", "ESP input 0"), appOnly, binaryStates),
        state(AutomationSignalId.ESP_GPIO_IN_1, ruEn("ESP-вход 1", "ESP input 1"), appOnly, binaryStates),
        state(AutomationSignalId.ESP_GPIO_IN_2, ruEn("ESP-вход 2", "ESP input 2"), appOnly, binaryStates),
        state(AutomationSignalId.ESP_GPIO_IN_3, ruEn("ESP-вход 3", "ESP input 3"), appOnly, binaryStates),
        state(AutomationSignalId.ESP_RELAY_0, ruEn("ESP-реле 0", "ESP relay 0"), appOnly, binaryStates),
        state(AutomationSignalId.ESP_RELAY_1, ruEn("ESP-реле 1", "ESP relay 1"), appOnly, binaryStates),
        state(AutomationSignalId.ESP_BLE_RELAY_0, ruEn("BLE-реле 0", "BLE relay 0"), appOnly, binaryStates),
        state(AutomationSignalId.ESP_BLE_RELAY_1, ruEn("BLE-реле 1", "BLE relay 1"), appOnly, binaryStates),
        state(AutomationSignalId.ESP_BLE_BOUND, ruEn("ESP BLE пульт привязан", "ESP BLE remote paired"), appOnly, binaryStates),
        number(
            AutomationSignalId.ESP_BLE_BATTERY,
            ruEn("ESP BLE батарея пульта", "ESP BLE remote battery"),
            "%",
            appOnly,
            typicalRange = ruEn("0…100 %, последнее значение с allowlisted Shelly Blu", "0…100 %, last value from an allowlisted Shelly Blu"),
        ),
        state(
            AutomationSignalId.WIFI_ENABLED,
            "Wi-Fi",
            appOnly,
            binaryStates,
            typicalRange = ruEn("Только клиентский Wi-Fi ГУ (wlan0). on — радио включено, off — выключено. ", "HU client Wi-Fi only (wlan0). on — the radio is on, off — it is off. ") +
                ruEn("Точка доступа ГУ (SoftAP) не учитывается.", "The HU hotspot (SoftAP) is not counted."),
        ),
        state(
            AutomationSignalId.WIFI_ASSOCIATED,
            ruEn("Wi-Fi: подключение к сети", "Wi-Fi: network connection"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on — есть ассоциация с любой сохранённой сетью; off — нет ассоциации ", "on — associated with any saved network; off — no association ") +
                ruEn("или радио выключено (явный off, не «нет значения»).", "or the radio is off (an explicit off, not “no value”)."),
        ),
        state(
            AutomationSignalId.WIFI_SSID,
            ruEn("Wi-Fi: точка доступа", "Wi-Fi: hotspot"),
            appOnly,
            typicalRange = ruEn("SSID текущей сети или none, если радио выключено или ассоциации нет. ", "SSID of the current network, or none if the radio is off or there is no association. ") +
                ruEn("Выбор из сохранённых сетей ГУ.", "Chosen from the saved networks on the HU."),
        ),
        state(
            AutomationSignalId.HU_INTERNET_STATUS,
            ruEn("Интернет ГУ", "HU internet"),
            appOnly,
            listOf("unknown", "checking", "online", "offline"),
            typicalRange = ruEn("HTTP(S) проверка URL с вкладки «Модем». online — сразу после удачной ", "HTTP(S) URL check from the Modem tab. online — right after a successful ") +
                ruEn("проверки; offline — после двух подряд неудачных; unknown — монитор не запущен. ", "check; offline — after two failures in a row; unknown — the monitor is not running. ") +
                ruEn("checking почти не публикуется (чтобы не дёргать автоматизации).", "checking is rarely published (so automations are not triggered)."),
        ),
        state(
            AutomationSignalId.WIFI_MODEM_LINK_STATUS,
            ruEn("Wi‑Fi модем: связь", "Wi‑Fi modem: link"),
            appOnly,
            vad.dashing.tbox.wifimodem.ModemAutomationStates.LINK_OPTIONS,
            typicalRange = ruEn("HTTP-связь с админкой Wi‑Fi модема при источнике «Wi‑Fi HTTP»: ", "HTTP link to the Wi‑Fi modem admin page when the source is “Wi‑Fi HTTP”: ") +
                ruEn("ok / auth_failed / unreachable / error; idle — источник TBox или поллер не запущен.", "ok / auth_failed / unreachable / error; idle — TBox source, or the poller is not running."),
        ),
        state(
            AutomationSignalId.PHONE_HFP_CONNECTED,
            ruEn("Телефон HFP", "HFP phone"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off — профиль Headset Client подключён к телефону. ", "on/off — the Headset Client profile is connected to the phone. ") +
                ruEn("Нужен для исходящего place_call и плитки «Набрать номер».", "Required for an outgoing place_call and the “Dial number” tile."),
        ),
        state(
            AutomationSignalId.PHONE_CALL_STATE,
            ruEn("Состояние звонка", "Call state"),
            appOnly,
            vad.dashing.tbox.phone.PhoneCallState.OPTIONS,
            typicalRange = "idle / dialing / active / incoming / held / unknown. " +
                ruEn("Наблюдение через BluetoothHeadsetClient (best-effort из процесса Monitor); ", "Observed via BluetoothHeadsetClient (best-effort from the Monitor process); ") +
                ruEn("unknown — observer не поднят / нет permission / список звонков не читается.", "unknown — observer not started / no permission / the call list cannot be read."),
        ),
        state(
            AutomationSignalId.PHONE_CALL_DIRECTION,
            ruEn("Направление звонка", "Call direction"),
            appOnly,
            vad.dashing.tbox.phone.PhoneCallDirection.OPTIONS,
            typicalRange = ruEn("none / outgoing / incoming по текущему HFP-вызову.", "none / outgoing / incoming for the current HFP call."),
        ),
        state(
            AutomationSignalId.PHONE_CALL_NUMBER,
            ruEn("Номер звонка", "Call number"),
            appOnly,
            typicalRange = ruEn("Текущий или последний известный номер (строка). Может быть пустым. ", "Current or last known number (string). It may be empty. ") +
                ruEn("PII: отдаётся в /v1/signals по запросу ids.", "PII: exposed in /v1/signals when ids are requested."),
        ),
        state(
            AutomationSignalId.MODEM_MOBILE_DATA,
            ruEn("Модем: передача данных", "Modem: mobile data"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off по apnStatus (TBox MDC или Wi‑Fi модем). ", "on/off from apnStatus (TBox MDC or Wi‑Fi modem). ") +
                ruEn("То же readback, что после действия wifi_modem_set_data.", "The same readback as after the wifi_modem_set_data action."),
        ),
        state(
            AutomationSignalId.MODEM_NET_TYPE,
            ruEn("Модем: тип сети", "Modem: network type"),
            appOnly,
            vad.dashing.tbox.wifimodem.ModemAutomationStates.NET_TYPE_OPTIONS,
            typicalRange = ruEn("2g / 3g / 4g / none из netStatus вкладки «Модем» (TBox или Wi‑Fi HTTP).", "2g / 3g / 4g / none from netStatus on the Modem tab (TBox or Wi‑Fi HTTP)."),
        ),
        state(
            AutomationSignalId.MODEM_SIM_STATUS,
            ruEn("Модем: SIM", "Modem: SIM"),
            appOnly,
            vad.dashing.tbox.wifimodem.ModemAutomationStates.SIM_OPTIONS,
            typicalRange = ruEn("none / ready / pin / error / unknown из simStatus вкладки «Модем».", "none / ready / pin / error / unknown from simStatus on the Modem tab."),
        ),
        state(
            AutomationSignalId.FOREGROUND_APP,
            ruEn("Приложение на экране", "App on screen"),
            appOnly,
            typicalRange = ruEn("Пакет приложения на переднем плане. Нужен доступ к статистике ", "Foreground app package. Needs access to usage ") +
                ruEn("использования. Опрос 1 с, окно событий 10 с; пустой опрос держит последний пакет. ", "stats. Polling is 1 s, the event window is 10 s; an empty poll keeps the last package. ") +
                ruEn("Пакет самого TBox учитывается, только если открыт главный экран. Без разрешения ", "The TBox package itself counts only when the home screen is open. Without the permission ") +
                ruEn("сигнала нет. Камера 360 com.mengbo.avm учитывается по штатному оверлею ", "there is no signal. The 360 camera com.mengbo.avm is counted from the stock overlay ") +
                ruEn("(Settings.Global avm_state), даже если UsageStats держит предыдущее приложение.", "(Settings.Global avm_state), even if UsageStats still holds the previous app."),
        ),
        state(
            AutomationSignalId.DCL_ADAS_PANEL_VISIBLE,
            ruEn("DCL: панель ADAS", "DCL: ADAS panel"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off из DCL UI state (adasPanelVisible). Нет значения, пока лаунчер ", "on/off from DCL UI state (adasPanelVisible). No value until the ") +
                ruEn("DCL не отдал снимок. Не путать с UsageStats sticky-hide.", "DCL launcher returns a snapshot. Do not confuse with UsageStats sticky-hide."),
        ),
        state(
            AutomationSignalId.DCL_TOP_BAR_VISIBLE,
            ruEn("DCL: верхняя панель", "DCL: top bar"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off из DCL UI state (topBarVisible).", "on/off from DCL UI state (topBarVisible)."),
        ),
        state(
            AutomationSignalId.DCL_BOTTOM_BAR_VISIBLE,
            ruEn("DCL: нижняя панель", "DCL: bottom bar"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off из DCL UI state (bottomBarVisible).", "on/off from DCL UI state (bottomBarVisible)."),
        ),
        state(
            AutomationSignalId.DCL_NOTIFICATION_SHADE_EXPANDED,
            ruEn("DCL: шторка уведомлений", "DCL: notification shade"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off из DCL UI state (notificationShadeExpanded). Нет значения, ", "on/off from DCL UI state (notificationShadeExpanded). No value ") +
                ruEn("пока лаунчер не отдал снимок или поле отсутствует (старый DCL).", "until the launcher returns a snapshot or the field is missing (old DCL)."),
        ),
        state(
            AutomationSignalId.DCL_SCREEN_MODE,
            ruEn("DCL: режим экрана", "DCL: screen mode"),
            appOnly,
            listOf("home", "single", "full_width", "fullscreen", "split"),
            typicalRange = ruEn("home / single / full_width / fullscreen / split из DCL screenMode. ", "home / single / full_width / fullscreen / split from DCL screenMode. ") +
                ruEn("Типичный рецепт: скрыть плавающие панели при screenMode=fullscreen или когда ", "Typical recipe: hide floating panels when screenMode=fullscreen or when ") +
                ruEn("бары скрыты (state_equals + toggle_hide_floating_panels).", "the bars are hidden (state_equals + toggle_hide_floating_panels)."),
        ),
        state(
            AutomationSignalId.DCL_SMART_ACC_ACTIVE,
            ruEn("DCL: Умный ACC", "DCL: Smart ACC"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off из DCL Smart ACC (smartAccActive). Не путать с CAN ", "on/off from DCL Smart ACC (smartAccActive). Do not confuse with CAN ") +
                ruEn("acc_cruise_state. Нет значения, пока лаунчер не отдал снимок.", "acc_cruise_state. No value until the launcher returns a snapshot."),
        ),
        state(
            AutomationSignalId.DCL_SMART_ACC_CONFIG_ENABLED,
            ruEn("DCL: АСС по ограничению (настройка)", "DCL: ACC by speed limit (setting)"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off — опция лаунчера «Скорость АСС по ограничению скорости» ", "on/off — launcher option “ACC speed from the speed limit” ") +
                "(smartAccConfigEnabled).",
        ),
        state(
            AutomationSignalId.DCL_SMART_ACC_CRUISE_STATE,
            ruEn("DCL: состояние круиза (Smart ACC)", "DCL: cruise state (Smart ACC)"),
            appOnly,
            listOf("Off", "Standby", "Active", "Override", "Fault"),
            typicalRange = ruEn("Off / Standby / Active / Override / Fault из DCL cruiseState. ", "Off / Standby / Active / Override / Fault from DCL cruiseState. ") +
                ruEn("Не путать с CAN acc_cruise_state.", "Do not confuse with CAN acc_cruise_state."),
        ),
        number(
            AutomationSignalId.DCL_EFFECTIVE_SPEED_LIMIT,
            ruEn("DCL: эффективный лимит, км/ч", "DCL: effective limit, km/h"),
            ruEn("км/ч", "km/h"),
            appOnly,
            typicalRange = ruEn("Распознанное ограничение со знака или навигатора (effectiveSpeedLimit). ", "Recognized limit from a sign or the navigator (effectiveSpeedLimit). ") +
                ruEn("Нет значения, если лимит неизвестен или поле отсутствует.", "No value if the limit is unknown or the field is missing."),
        ),
        number(
            AutomationSignalId.DCL_SMART_ACC_SPEED_OFFSET,
            ruEn("DCL: оффсет АСС, км/ч", "DCL: ACC offset, km/h"),
            ruEn("км/ч", "km/h"),
            appOnly,
            typicalRange = ruEn("adasAccSpeedOffset из getSmartAccState (например +0 / +19).", "adasAccSpeedOffset from getSmartAccState (for example +0 / +19)."),
        ),
        number(
            AutomationSignalId.DCL_SMART_ACC_TARGET_SPEED,
            ruEn("DCL: целевая скорость АСС, км/ч", "DCL: ACC target speed, km/h"),
            ruEn("км/ч", "km/h"),
            appOnly,
            typicalRange = ruEn("targetSpeed = limit + offset из getSmartAccState.", "targetSpeed = limit + offset from getSmartAccState."),
        ),
        state(
            AutomationSignalId.CAMD_RECORDER_ENABLED,
            ruEn("Камеры ГУ: регистратор включён", "HU Cameras: recorder on"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off из STATUS rec_enabled= (мастер-переключатель REC ON/OFF). ", "on/off from STATUS rec_enabled= (REC ON/OFF master switch). ") +
                ruEn("Не путать с camd_recording_active. Нет значения, пока camd не ответил STATUS ", "Do not confuse with camd_recording_active. No value until camd replies STATUS ") +
                ruEn("с этим полем или пакет не установлен.", "with this field, or the pack is not installed."),
        ),
        state(
            AutomationSignalId.CAMD_RECORDING_ACTIVE,
            ruEn("Камеры ГУ: запись идёт", "HU Cameras: recording"),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off из STATUS rec_active= (сегменты сейчас пишутся). ", "on/off from STATUS rec_active= (segments are being written now). ") +
                ruEn("Отдельно от camd_recorder_enabled: enabled может быть on при ожидании носителя.", "Separate from camd_recorder_enabled: enabled can be on while waiting for storage."),
        ),
        state(
            AutomationSignalId.APP_THEME_MODE,
            ruEn("Тема приложения: режим", "App theme: mode"),
            appOnly,
            listOf("manual_day", "manual_night", "auto_day", "auto_night"),
            typicalRange = ruEn("manual_day / manual_night — ручная тема (ГУ или локальная тема ", "manual_day / manual_night — manual theme (HU or local theme ") +
                ruEn("приложения, если оно отвязано от системы), auto_day / auto_night — штатный ", "of the app, if it is detached from the system), auto_day / auto_night — the stock ") +
                ruEn("авто день/ночь ГУ с текущим разрешением. Один триггер «любой день/ночь» — ", "HU auto day/night with the current resolution. One “any day/night” trigger — ") +
                ruEn("сигнал app_theme. Тот же режим, что у переключателя темы и действий ", "the app_theme signal. The same mode as the theme switch and the actions ") +
                "toggle_app_day_night_theme / enable_head_unit_auto_theme / " +
                "set_hu_day_night_theme (light/dark/auto).",
        ),
        state(
            AutomationSignalId.APP_THEME,
            ruEn("Тема приложения: сейчас", "App theme: now"),
            appOnly,
            listOf("day", "night"),
            typicalRange = ruEn("Эффективная тема в моменте: day — светлая, night — тёмная, ", "Effective theme right now: day — light, night — dark, ") +
                ruEn("ручная или разрешённая авто-режимом. Триггер «День» срабатывает и на ручной, ", "manual or allowed by auto mode. The “Day” trigger fires on manual ") +
                ruEn("и на авто-день; «Ночь» — и на ручную, и на авто-ночь.", "and on auto day; “Night” fires on both manual and auto night."),
        ),
        number(
            AutomationSignalId.HU_SCREEN_BRIGHTNESS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HU_SCREEN_BRIGHTNESS),
            "",
            appOnly,
            typicalRange = ruEn("Только приложение. Уровень 1…10 — подсветка экрана ГУ ", "App only. Level 1…10 — HU screen backlight ") +
                ruEn("(Car Settings → Экраны). Три яркости: экран ГУ / HUD / приборная панель (ICM) — ", "(Car Settings → Displays). Three brightnesses: HU screen / HUD / instrument cluster (ICM) — ") +
                ruEn("разные сигналы. A9: Settings.System screen_brightness; A10: Adayo getSysBacklight.", "different signals. A9: Settings.System screen_brightness; A10: Adayo getSysBacklight."),
        ),
        state(
            AutomationSignalId.HU_SCREEN_AUTO_BRIGHTNESS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HU_SCREEN_AUTO_BRIGHTNESS),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on/off — автоподсветка экрана ГУ (не тема день/ночь, не HUD/ICM). ", "on/off — HU screen auto-brightness (not the day/night theme, not HUD/ICM). ") +
                ruEn("A9: auto_bright; A10: getDayNightMode 1=auto / 4=manual для яркости.", "A9: auto_bright; A10: getDayNightMode 1=auto / 4=manual for brightness."),
        ),
        number(
            AutomationSignalId.HU_MEDIA_VOLUME,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HU_MEDIA_VOLUME),
            "",
            appOnly,
            typicalRange = ruEn("Микшер ГУ 0…31 (не mbCAN EQ). Car Settings → Аудио.", "HU mixer 0…31 (not mbCAN EQ). Car Settings → Audio."),
        ),
        number(
            AutomationSignalId.HU_PHONE_VOLUME,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HU_PHONE_VOLUME),
            "",
            appOnly,
            typicalRange = ruEn("Микшер ГУ 1…31. Car Settings → Аудио.", "HU mixer 1…31. Car Settings → Audio."),
        ),
        number(
            AutomationSignalId.HU_NAVI_VOLUME,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HU_NAVI_VOLUME),
            "",
            appOnly,
            typicalRange = ruEn("Микшер ГУ 0…10. Car Settings → Аудио.", "HU mixer 0…10. Car Settings → Audio."),
        ),
        number(
            AutomationSignalId.HU_VOICE_VOLUME,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HU_VOICE_VOLUME),
            "",
            appOnly,
            typicalRange = ruEn("Микшер ГУ 2…10 (TTS). Car Settings → Аудио.", "HU mixer 2…10 (TTS). Car Settings → Audio."),
        ),
        state(
            AutomationSignalId.HU_HEADREST_SPEAKER,
            AutomationParameterLabels.signalLabel(AutomationSignalId.HU_HEADREST_SPEAKER),
            appOnly,
            listOf("only", "assist", "off"),
            typicalRange = "only / assist / off (UI 1/2/3). A9 mbCAN audio 37; A10 SettingsSvc.",
        ),
        state(
            AutomationSignalId.TBOX_CONNECTED,
            AutomationParameterLabels.signalLabel(AutomationSignalId.TBOX_CONNECTED),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on — UDP-связь с TBox через tbox-proxy; off — нет связи. ", "on — UDP link with TBox via tbox-proxy; off — no link. ") +
                ruEn("Источник app, чтобы при обрыве было значение off, а не «нет сигнала».", "The source is app, so a dropout is the value off rather than “no signal”."),
        ),
        number(
            AutomationSignalId.MODEM_SIGNAL_LEVEL,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MODEM_SIGNAL_LEVEL),
            "",
            appOnly,
            typicalRange = ruEn("Уровень сигнала модема (CSQ → «палочки»), обычно 0…5; 0 — нет данных. ", "Modem signal level (CSQ → “bars”), usually 0…5; 0 — no data. ") +
                ruEn("TBox MDC или Wi‑Fi HTTP sink в netState.", "TBox MDC or Wi‑Fi HTTP sink in netState."),
        ),
        number(
            AutomationSignalId.MODEM_RSSI_DBM,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MODEM_RSSI_DBM),
            ruEn("дБм", "dBm"),
            appOnly,
            typicalRange = ruEn("RSSI/RSRP дБм: signalDbm с Wi‑Fi модема или CSQ→dBm (2·CSQ−113) ", "RSSI/RSRP dBm: signalDbm from the Wi‑Fi modem or CSQ→dBm (2·CSQ−113) ") +
                ruEn("для TBox при CSQ 0…31. Unavailable при CSQ 99 / без значения. ", "for TBox when CSQ is 0…31. Unavailable at CSQ 99 / when there is no value. ") +
                ruEn("IMEI/ICCID/IMSI не отдаются.", "IMEI/ICCID/IMSI are not exposed."),
        ),
        state(
            AutomationSignalId.MODEM_REGISTERED,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MODEM_REGISTERED),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on — домашняя сеть или роуминг; off — нет сети / поиск / отказ / иное.", "on — home network or roaming; off — no network / searching / denied / other."),
        ),
        state(
            AutomationSignalId.MODEM_OPERATOR,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MODEM_OPERATOR),
            appOnly,
            typicalRange = ruEn("Имя оператора (строка) или нет значения. Без IMEI/ICCID/IMSI.", "Operator name (string), or no value. No IMEI/ICCID/IMSI."),
        ),
        state(
            AutomationSignalId.MODEM_SOURCE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MODEM_SOURCE),
            appOnly,
            vad.dashing.tbox.wifimodem.ModemAutomationStates.MODEM_SOURCE_OPTIONS,
            typicalRange = ruEn("Активный источник модема из настроек: tbox / wifi_http.", "Active modem source from settings: tbox / wifi_http."),
        ),
        number(
            AutomationSignalId.MODEM_THROUGHPUT_DOWN_KBPS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MODEM_THROUGHPUT_DOWN_KBPS),
            ruEn("кбит/с", "kbit/s"),
            appOnly,
            typicalRange = ruEn("Downlink throughput Wi‑Fi HTTP модема (из bytes/s). ", "Downlink throughput of the Wi‑Fi HTTP modem (from bytes/s). ") +
                ruEn("Unavailable на пути TBox MDC или когда модем не отдал скорость.", "Unavailable on the TBox MDC path, or when the modem did not report a speed."),
        ),
        number(
            AutomationSignalId.MODEM_THROUGHPUT_UP_KBPS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MODEM_THROUGHPUT_UP_KBPS),
            ruEn("кбит/с", "kbit/s"),
            appOnly,
            typicalRange = ruEn("Uplink throughput Wi‑Fi HTTP модема (из bytes/s). ", "Uplink throughput of the Wi‑Fi HTTP modem (from bytes/s). ") +
                ruEn("Unavailable на пути TBox MDC или когда модем не отдал скорость.", "Unavailable on the TBox MDC path, or when the modem did not report a speed."),
        ),
        state(
            AutomationSignalId.LOCATE_STATUS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.LOCATE_STATUS),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on — у активного источника геопозиции (TBox / компаньон / Android / USB) ", "on — the active location source (TBox / companion / Android / USB) ") +
                ruEn("есть фиксация; off — нет. GeoDisplayRepository.state.locateStatus.", "has a fix; off — it does not. GeoDisplayRepository.state.locateStatus."),
        ),
        number(
            AutomationSignalId.FUEL_LEVEL_PERCENT_FILTERED,
            AutomationParameterLabels.signalLabel(AutomationSignalId.FUEL_LEVEL_PERCENT_FILTERED),
            "%",
            appOnly,
            typicalRange = ruEn("Отфильтрованный % топлива во время активной поездки ", "Filtered fuel % during an active trip ") +
                ruEn("(TripTelemetryRepository). Без активной поездки — нет значения.", "(TripTelemetryRepository). No active trip — no value."),
        ),
        number(
            AutomationSignalId.FUEL_LEVEL_LITERS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.FUEL_LEVEL_LITERS),
            ruEn("л", "L"),
            appOnly,
            typicalRange = ruEn("Калиброванные литры (+15 °C) во время активной поездки. ", "Calibrated liters (+15 °C) during an active trip. ") +
                ruEn("Без поездки / калибровки — нет значения.", "No trip / no calibration — no value."),
        ),
        number(
            AutomationSignalId.GEAR_BOX_OIL_TEMPERATURE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.GEAR_BOX_OIL_TEMPERATURE),
            "°C",
            tboxOnly,
            typicalRange = ruEn("Температура масла КПП с TBox CAN, °C.", "Gearbox oil temperature from TBox CAN, °C."),
        ),
        number(
            AutomationSignalId.ACTIVE_TRIP_DISTANCE_KM,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ACTIVE_TRIP_DISTANCE_KM),
            ruEn("км", "km"),
            appOnly,
            typicalRange = ruEn("Пробег открытой текущей поездки (не «суточной»). Нет активной — нет значения.", "Distance of the open current trip (not the “daily” one). No active trip — no value."),
        ),
        number(
            AutomationSignalId.ACTIVE_TRIP_AVG_FUEL_L100KM,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ACTIVE_TRIP_AVG_FUEL_L100KM),
            ruEn("л/100км", "L/100km"),
            appOnly,
            typicalRange = ruEn("Средний расход активной поездки, л/100 км. Нужен ненулевой пробег.", "Average consumption of the active trip, L/100 km. A non-zero distance is required."),
        ),
        number(
            AutomationSignalId.ACTIVE_TRIP_DURATION_S,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ACTIVE_TRIP_DURATION_S),
            ruEn("с", "s"),
            appOnly,
            typicalRange = ruEn("Длительность активной поездки в секундах (движение+простой+стоянка).", "Active trip duration in seconds (moving+idle+parked)."),
        ),
        number(
            AutomationSignalId.ACTIVE_TRIP_MOTOR_HOURS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ACTIVE_TRIP_MOTOR_HOURS),
            ruEn("ч", "h"),
            appOnly,
            typicalRange = ruEn("Моточасы активной поездки (движение+простой), десятичные часы.", "Engine hours of the active trip (moving+idle), decimal hours."),
        ),
        number(
            AutomationSignalId.MOTOR_HOURS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MOTOR_HOURS),
            ruEn("ч", "h"),
            appOnly,
            typicalRange = ruEn("Накопленные моточасы приложения (CarDataRepository).", "Accumulated app engine hours (CarDataRepository)."),
        ),
        state(
            AutomationSignalId.MEDIA_TITLE,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MEDIA_TITLE),
            appOnly,
            typicalRange = ruEn("Название трека из активной MediaSession. ", "Track title from the active MediaSession. ") +
                ruEn("Любой плеер, который сейчас играет; виджет музыки не нужен. ", "Any player that is playing now; a music widget is not needed. ") +
                ruEn("Нужен доступ к уведомлениям. Без сессии — нет значения.", "Notification access is required. No session — no value."),
        ),
        state(
            AutomationSignalId.MEDIA_ARTIST,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MEDIA_ARTIST),
            appOnly,
            typicalRange = ruEn("Исполнитель из активной MediaSession. ", "Artist from the active MediaSession. ") +
                ruEn("Любой плеер, который сейчас играет; виджет музыки не нужен. ", "Any player that is playing now; a music widget is not needed. ") +
                ruEn("Нужен доступ к уведомлениям. Без сессии — нет значения.", "Notification access is required. No session — no value."),
        ),
        state(
            AutomationSignalId.MEDIA_PLAYING,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MEDIA_PLAYING),
            appOnly,
            binaryStates,
            typicalRange = ruEn("on — сессия играет, off — на паузе. ", "on — the session is playing, off — paused. ") +
                ruEn("Берётся играющая сессия, иначе последняя с названием. Виджет не нужен.", "Uses the playing session, otherwise the last one with a title. No widget is needed."),
        ),
        number(
            AutomationSignalId.MEDIA_POSITION_MS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MEDIA_POSITION_MS),
            ruEn("мс", "ms"),
            appOnly,
            typicalRange = ruEn("Позиция в миллисекундах на момент чтения. ", "Position in milliseconds at the time of the read. ") +
                ruEn("Между событиями плеера дорисовывается по скорости, как у виджета музыки.", "Between player events it is filled in from the playback rate, like the music widget."),
        ),
        number(
            AutomationSignalId.MEDIA_DURATION_MS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.MEDIA_DURATION_MS),
            ruEn("мс", "ms"),
            appOnly,
            typicalRange = ruEn("Длительность трека в миллисекундах. 0 — сессия длительность не сообщила.", "Track duration in milliseconds. 0 — the session did not report a duration."),
        ),
        state(
            AutomationSignalId.ROAD_HIGHWAY_CLASS,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ROAD_HIGHWAY_CLASS),
            appOnly,
            AutomationRoadMatchSignals.HIGHWAY_CLASS_OPTIONS,
            typicalRange = ruEn("Класс OSM-ребра после привязки к карте (motorway, primary, residential, …). ", "OSM edge class after map matching (motorway, primary, residential, …). ") +
                ruEn("Без match / без ребра — сигнала нет. Поднимает matcher без коррекции позы; ", "No match / no edge — no signal. Starts the matcher without pose correction; ") +
                ruEn("не SpeedCam и не SLA с шины.", "not SpeedCam and not SLA from the bus."),
        ),
        number(
            AutomationSignalId.ROAD_SPEED_LIMIT_KMH,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ROAD_SPEED_LIMIT_KMH),
            ruEn("км/ч", "km/h"),
            appOnly,
            typicalRange = ruEn("Текущий posted OSM maxspeed на matched-ребре (км/ч). ", "Current posted OSM maxspeed on the matched edge (km/h). ") +
                ruEn("Без match или без maxspeed в паке — сигнала нет. Только OSM match, ", "No match, or no maxspeed in the pack — no signal. OSM match only, ") +
                ruEn("не fallback с радара SpeedCam и не slaSpeedLimitWidget. ", "not a fallback from the SpeedCam radar and not slaSpeedLimitWidget. ") +
                ruEn("Смена лимита: NumericThreshold с выключенным rearm (каждое новое число).", "Limit change: NumericThreshold with rearm off (every new number)."),
        ),
        state(
            AutomationSignalId.ROAD_TUNNEL,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ROAD_TUNNEL),
            appOnly,
            binaryStates,
            typicalRange = ruEn("Тоннель / Tunnel (OSM на matched-ребре). ", "Tunnel (OSM on the matched edge). ") +
                ruEn("Условие: on — в тоннеле, off — вне тоннеля. ", "Condition: on — in a tunnel, off — outside a tunnel. ") +
                ruEn("Триггер: on — въезд в тоннель (off→on), off — выезд из тоннеля (on→off). ", "Trigger: on — enter tunnel (off→on), off — exit tunnel (on→off). ") +
                ruEn("Без match — сигнала нет. Поднимает matcher без коррекции позы; не SpeedCam.", "No match — no signal. Starts the matcher without pose correction; not SpeedCam."),
            namedValues = listOf(
                AutomationSignalNamedValue("off", ruEn("Вне тоннеля", "Outside")),
                AutomationSignalNamedValue("on", ruEn("В тоннеле", "Inside")),
            ),
        ),
        state(
            AutomationSignalId.ROAD_SETTLEMENT,
            AutomationParameterLabels.signalLabel(AutomationSignalId.ROAD_SETTLEMENT),
            appOnly,
            binaryStates,
            typicalRange = ruEn("Населённый пункт / Settlement (OSM на matched-ребре). ", "Settlement (OSM on the matched edge). ") +
                ruEn("Условие: on — в НП, off — вне НП. ", "Condition: on — in a settlement, off — outside a settlement. ") +
                ruEn("Триггер: on — въезд в НП (off→on), off — выезд из НП (on→off). ", "Trigger: on — enter settlement (off→on), off — exit settlement (on→off). ") +
                ruEn("Без match — сигнала нет. Нужен пакет с полем settlement. ", "No match — no signal. A pack with a settlement field is required. ") +
                ruEn("Поднимает matcher без коррекции позы; не SpeedCam.", "Starts the matcher without pose correction; not SpeedCam."),
            namedValues = listOf(
                AutomationSignalNamedValue("off", ruEn("Вне НП", "Outside")),
                AutomationSignalNamedValue("on", ruEn("В НП", "Inside")),
            ),
        ),
    )

    private val byId = entries.associateBy { it.id }

    /** Mirrors [vad.dashing.tbox.mbcan.UniversalCanRepository.mode]; kept here so the catalog has no Android deps. */
    @Volatile
    var headUnitMode: HeadUnitCanMode = HeadUnitCanMode.Android9MbCan
        private set

    fun setHeadUnitMode(mode: HeadUnitCanMode) {
        headUnitMode = mode
    }

    fun get(id: AutomationSignalId): AutomationSignalDescriptor = requireNotNull(byId[id])

    /**
     * HU backends that answer for a signal but never carry it: A9 mbCAN coolant is always 0.0,
     * A9 has no target gear and no MCU board-voltage frame, A10 VHAL has no steering speed and no HU system mode.
     * TBox covers coolant, target gear, and steering speed. System mode stays head-unit only.
     */
    fun headUnitUnsupported(id: AutomationSignalId, mode: HeadUnitCanMode): Boolean = when (mode) {
        HeadUnitCanMode.Android9MbCan ->
            id == AutomationSignalId.ENGINE_TEMPERATURE ||
                id == AutomationSignalId.TARGET_GEAR ||
                id == AutomationSignalId.VOLTAGE
        HeadUnitCanMode.Android10Vhal ->
            id == AutomationSignalId.STEERING_SPEED || id == AutomationSignalId.HU_SYSTEM_MODE
    }

    /** Sources that give real values on [mode]. [AutomationSignalDescriptor.sources] is the union over platforms. */
    fun sources(id: AutomationSignalId, mode: HeadUnitCanMode = headUnitMode): Set<AutomationSignalSource> {
        val all = get(id).sources
        if (!headUnitUnsupported(id, mode)) return all
        val usable = all - AutomationSignalSource.HEAD_UNIT
        return usable.ifEmpty { all }
    }

    /** Saved rules and API clients may still name the HU for these signals; read them from what works. */
    fun resolveSource(
        id: AutomationSignalId,
        requested: AutomationSignalSource,
        mode: HeadUnitCanMode = headUnitMode,
    ): AutomationSignalSource {
        val usable = sources(id, mode)
        return if (requested in usable) requested else preferredSource(usable)
    }

    fun preferredSource(id: AutomationSignalId): AutomationSignalSource =
        preferredSource(sources(id))

    fun preferredSource(sources: Set<AutomationSignalSource>): AutomationSignalSource {
        require(sources.isNotEmpty()) { "signal sources must not be empty" }
        return SOURCE_UI_ORDER.firstOrNull { it in sources } ?: sources.first()
    }

    fun sourcesForUi(id: AutomationSignalId): List<AutomationSignalSource> =
        sourcesForUi(sources(id))

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

    /**
     * Trigger/condition pickers: hides speed-limiter signals while
     * [vad.dashing.tbox.SPEED_LIMITER_UI_HIDDEN], the cruise-state signal that does not
     * match the global [vad.dashing.tbox.VehicleFeatureSettings.cruiseControlType],
     * and the legacy either-side [AutomationSignalId.TURN_STABILIZED] (use L/R instead).
     */
    fun pickerSignalsOfType(valueType: AutomationSignalValueType): List<AutomationSignalId> =
        signalsOfType(valueType)
            .filterNot { isSpeedLimiterHiddenFromAutomationPicker(it) }
            .filterNot { isCruiseSignalHiddenFromAutomationPicker(it) }
            .filterNot { it == AutomationSignalId.TURN_STABILIZED }

    private fun isCruiseSignalHiddenFromAutomationPicker(id: AutomationSignalId): Boolean {
        val global = vad.dashing.tbox.VehicleFeatureSettings.cruiseControlType
        return when (id) {
            AutomationSignalId.ACC_CRUISE_STATE ->
                global != vad.dashing.tbox.GlobalCruiseControlType.ACC
            AutomationSignalId.CCS_CRUISE_STATE ->
                global != vad.dashing.tbox.GlobalCruiseControlType.CCS
            else -> false
        }
    }

    fun stateOptionLabel(raw: String): String = AutomationSignalStateEncoding.stateOptionLabel(raw)

    private fun number(
        id: AutomationSignalId,
        label: String,
        unit: String,
        sources: Set<AutomationSignalSource>,
        typicalRange: String = "",
        namedValues: List<AutomationSignalNamedValue> = emptyList(),
        keywords: List<String> = emptyList(),
    ) = AutomationSignalDescriptor(
        id = id,
        label = label,
        unit = unit,
        sources = sources,
        group = signalGroup(id),
        namedValues = namedValues,
        typicalRange = typicalRange,
        keywords = keywords,
    )

    private fun state(
        id: AutomationSignalId,
        label: String,
        sources: Set<AutomationSignalSource>,
        options: List<String> = emptyList(),
        typicalRange: String = "",
        namedValues: List<AutomationSignalNamedValue>? = null,
        keywords: List<String> = emptyList(),
    ) = AutomationSignalDescriptor(
        id = id,
        label = label,
        sources = sources,
        group = signalGroup(id),
        stateOptions = options,
        namedValues = namedValues
            ?: options.map { AutomationSignalNamedValue(it, stateOptionLabel(it)) },
        typicalRange = typicalRange,
        keywords = keywords,
    )

    /** Picker group of a signal; exhaustive `when` — new ids must declare their group here. */
    fun signalGroup(id: AutomationSignalId): AutomationSignalGroup = when (id) {
        AutomationSignalId.ENGINE_RPM,
        AutomationSignalId.CAR_SPEED,
        AutomationSignalId.ENGINE_TEMPERATURE,
        AutomationSignalId.STEERING_ANGLE,
        AutomationSignalId.STEERING_SPEED,
        AutomationSignalId.GAS_PEDAL,
        AutomationSignalId.BRAKE_PEDAL,
        AutomationSignalId.CURRENT_GEAR,
        AutomationSignalId.TARGET_GEAR,
        AutomationSignalId.GEAR_BOX_OIL_TEMPERATURE,
        -> AutomationSignalGroup.ENGINE

        AutomationSignalId.OUTSIDE_TEMPERATURE,
        AutomationSignalId.INSIDE_TEMPERATURE,
        AutomationSignalId.INSIDE_AIR_QUALITY,
        AutomationSignalId.OUTSIDE_AIR_QUALITY,
        -> AutomationSignalGroup.CLIMATE

        AutomationSignalId.FUEL_LEVEL_PERCENT,
        AutomationSignalId.ODOMETER_KM,
        AutomationSignalId.CURRENT_FUEL_CONSUMPTION,
        AutomationSignalId.DISTANCE_TO_EMPTY_KM,
        AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM,
        AutomationSignalId.VOLTAGE,
        AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE,
        AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE,
        AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE,
        AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE,
        AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE,
        AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE,
        AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE,
        AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE,
        AutomationSignalId.ENGINE_OIL_PRESSURE,
        AutomationSignalId.BRAKE_FLUID,
        AutomationSignalId.EPB_PARK_LAMP,
        -> AutomationSignalGroup.ENGINE

        AutomationSignalId.GEAR_MODE,
        AutomationSignalId.ACC_STATUS,
        AutomationSignalId.IGNITION_KEY,
        AutomationSignalId.HU_SYSTEM_MODE,
        AutomationSignalId.ACC_CRUISE_STATE,
        AutomationSignalId.CCS_CRUISE_STATE,
        AutomationSignalId.CRUISE_SET_SPEED,
        AutomationSignalId.REVERSE_GEAR,
        AutomationSignalId.DRIVE_MODE,
        AutomationSignalId.DRIVE_MODE_6DCT,
        AutomationSignalId.STEERING_MODE,
        AutomationSignalId.EPS_MODE,
        AutomationSignalId.FRM_DX_TAR_OBJ,
        -> AutomationSignalGroup.ENGINE

        AutomationSignalId.STEERING_WHEEL_HEAT,
        AutomationSignalId.SUNSHADE,
        AutomationSignalId.SUNROOF,
        AutomationSignalId.WINDOW_FRONT_LEFT,
        AutomationSignalId.WINDOW_FRONT_RIGHT,
        AutomationSignalId.WINDOW_REAR_LEFT,
        AutomationSignalId.WINDOW_REAR_RIGHT,
        AutomationSignalId.TRUNK_DOOR,
        AutomationSignalId.DOOR_FRONT_LEFT,
        AutomationSignalId.DOOR_FRONT_RIGHT,
        AutomationSignalId.DOOR_REAR_LEFT,
        AutomationSignalId.DOOR_REAR_RIGHT,
        AutomationSignalId.DOOR_AUTO_LOCK,
        AutomationSignalId.DOOR_IGNOFF_UNLOCK,
        AutomationSignalId.DRIVER_UNLOCK_MODE,
        AutomationSignalId.REMOTE_LOCK_FEEDBACK,
        AutomationSignalId.FRONT_LEFT_SEAT_MODE,
        AutomationSignalId.FRONT_RIGHT_SEAT_MODE,
        AutomationSignalId.REAR_LEFT_SEAT_MODE,
        AutomationSignalId.REAR_RIGHT_SEAT_MODE,
        AutomationSignalId.MIRROR_AUTO_FOLD,
        -> AutomationSignalGroup.BODY

        AutomationSignalId.WIPER_MAINTENANCE,
        AutomationSignalId.WIPER_STS,
        AutomationSignalId.WIPER_SENSITIVITY,
        AutomationSignalId.REAR_WIPER,
        AutomationSignalId.RAIN_DETECTED,
        AutomationSignalId.HIGH_BEAM,
        AutomationSignalId.HAZARD,
        AutomationSignalId.TURN_SIGNAL_LEFT,
        AutomationSignalId.TURN_SIGNAL_RIGHT,
        AutomationSignalId.TURN_STABILIZED,
        AutomationSignalId.TURN_FLASH_COUNT,
        AutomationSignalId.DRL,
        AutomationSignalId.HEADLIGHT_MODE,
        AutomationSignalId.HEADLIGHTS_FOLLOW_ME_HOME,
        AutomationSignalId.LOW_BEAM_HEIGHT,
        AutomationSignalId.REAR_FOG,
        -> AutomationSignalGroup.BODY

        AutomationSignalId.PARKING_RADAR,
        AutomationSignalId.AVH,
        AutomationSignalId.HDC,
        AutomationSignalId.ESP_OFF,
        AutomationSignalId.TJA_ICA,
        AutomationSignalId.HMA,
        AutomationSignalId.LAS_MODE,
        AutomationSignalId.BLIND_SPOT_DETECTION,
        AutomationSignalId.DOOR_OPEN_WARNING,
        AutomationSignalId.FCW,
        AutomationSignalId.FCW_SENSITIVITY,
        AutomationSignalId.ACC_TIME_GAP,
        AutomationSignalId.LDW_SENSITIVITY,
        AutomationSignalId.TSR_SWITCH,
        -> AutomationSignalGroup.ADAS

        AutomationSignalId.HVAC_AC_MAX,
        AutomationSignalId.HVAC_POWER,
        AutomationSignalId.HVAC_AUTO,
        AutomationSignalId.HVAC_RECIRCULATION,
        AutomationSignalId.HVAC_SYNC,
        AutomationSignalId.HVAC_CUSTOM_MODE,
        AutomationSignalId.FRONT_WINDSCREEN_HEAT,
        AutomationSignalId.HVAC_REAR_DEFROSTER,
        AutomationSignalId.HVAC_AC_CLEAN_WHEN_LOCKED,
        AutomationSignalId.HVAC_ANION_PURIFY,
        AutomationSignalId.FRAGRANCE,
        AutomationSignalId.FRAGRANCE_SMELL,
        AutomationSignalId.FRAGRANCE_CONCENTRATION,
        AutomationSignalId.HVAC_FIRST_BLOWING,
        AutomationSignalId.BT_REDUCE_FAN,
        AutomationSignalId.HVAC_AUTO_VENTILATION,
        AutomationSignalId.HVAC_FAN_DIRECTION,
        AutomationSignalId.HVAC_TEMPERATURE_LEFT,
        AutomationSignalId.HVAC_TEMPERATURE_RIGHT,
        AutomationSignalId.HVAC_FAN_SPEED,
        AutomationSignalId.HVAC_FRONT_OFF,
        -> AutomationSignalGroup.CLIMATE

        AutomationSignalId.HUD,
        AutomationSignalId.HUD_HEIGHT,
        AutomationSignalId.HUD_BRIGHTNESS,
        AutomationSignalId.HUD_DISPLAY_MODE,
        AutomationSignalId.HUD_AUTO_BRIGHTNESS,
        AutomationSignalId.ICM_BRIGHTNESS_MODE,
        AutomationSignalId.ICM_BRIGHTNESS,
        AutomationSignalId.OVERSPEED_ALARM,
        -> AutomationSignalGroup.BODY

        AutomationSignalId.AUDIO_VOLUME_SPEED_MODE,
        AutomationSignalId.AUDIO_KEY_TONE_VOLUME,
        AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME,
        AutomationSignalId.AUDIO_EQ_MODE,
        AutomationSignalId.AUDIO_EQ_BASS,
        AutomationSignalId.AUDIO_EQ_MIDDLE,
        AutomationSignalId.AUDIO_EQ_TREBLE,
        AutomationSignalId.AUDIO_BALANCE,
        AutomationSignalId.AUDIO_FADER,
        -> AutomationSignalGroup.AUDIO

        AutomationSignalId.GEO_POSITION,
        AutomationSignalId.GEO_ALTITUDE_M,
        AutomationSignalId.GEO_SPEED_KMH,
        AutomationSignalId.GEO_SPEED_SOURCE,
        AutomationSignalId.GEO_BEARING_DEG,
        AutomationSignalId.GEO_SATELLITES_VISIBLE,
        AutomationSignalId.GEO_SATELLITES_USED,
        AutomationSignalId.GEO_FIX_VALID,
        AutomationSignalId.GEO_SOURCE,
        AutomationSignalId.LOCATE_STATUS,
        -> AutomationSignalGroup.GEO

        AutomationSignalId.ESP_GPIO_IN_0,
        AutomationSignalId.ESP_GPIO_IN_1,
        AutomationSignalId.ESP_GPIO_IN_2,
        AutomationSignalId.ESP_GPIO_IN_3,
        AutomationSignalId.ESP_RELAY_0,
        AutomationSignalId.ESP_RELAY_1,
        AutomationSignalId.ESP_BLE_RELAY_0,
        AutomationSignalId.ESP_BLE_RELAY_1,
        AutomationSignalId.ESP_BLE_BOUND,
        AutomationSignalId.ESP_BLE_BATTERY,
        -> AutomationSignalGroup.ESP

        AutomationSignalId.WIFI_ENABLED,
        AutomationSignalId.WIFI_ASSOCIATED,
        AutomationSignalId.WIFI_SSID,
        AutomationSignalId.HU_INTERNET_STATUS,
        AutomationSignalId.WIFI_MODEM_LINK_STATUS,
        AutomationSignalId.MODEM_MOBILE_DATA,
        AutomationSignalId.MODEM_NET_TYPE,
        AutomationSignalId.MODEM_SIM_STATUS,
        AutomationSignalId.MODEM_SIGNAL_LEVEL,
        AutomationSignalId.MODEM_RSSI_DBM,
        AutomationSignalId.MODEM_REGISTERED,
        AutomationSignalId.MODEM_OPERATOR,
        AutomationSignalId.MODEM_SOURCE,
        AutomationSignalId.MODEM_THROUGHPUT_DOWN_KBPS,
        AutomationSignalId.MODEM_THROUGHPUT_UP_KBPS,
        -> AutomationSignalGroup.NETWORK

        AutomationSignalId.PHONE_HFP_CONNECTED,
        AutomationSignalId.PHONE_CALL_STATE,
        AutomationSignalId.PHONE_CALL_DIRECTION,
        AutomationSignalId.PHONE_CALL_NUMBER,
        -> AutomationSignalGroup.PHONE

        AutomationSignalId.FOREGROUND_APP,
        AutomationSignalId.APP_THEME_MODE,
        AutomationSignalId.APP_THEME,
        AutomationSignalId.HU_SCREEN_BRIGHTNESS,
        AutomationSignalId.HU_SCREEN_AUTO_BRIGHTNESS,
        AutomationSignalId.HU_MEDIA_VOLUME,
        AutomationSignalId.HU_PHONE_VOLUME,
        AutomationSignalId.HU_NAVI_VOLUME,
        AutomationSignalId.HU_VOICE_VOLUME,
        AutomationSignalId.HU_HEADREST_SPEAKER,
        AutomationSignalId.TBOX_CONNECTED,
        -> AutomationSignalGroup.APP

        AutomationSignalId.DCL_ADAS_PANEL_VISIBLE,
        AutomationSignalId.DCL_TOP_BAR_VISIBLE,
        AutomationSignalId.DCL_BOTTOM_BAR_VISIBLE,
        AutomationSignalId.DCL_NOTIFICATION_SHADE_EXPANDED,
        AutomationSignalId.DCL_SCREEN_MODE,
        AutomationSignalId.DCL_SMART_ACC_ACTIVE,
        AutomationSignalId.DCL_SMART_ACC_CONFIG_ENABLED,
        AutomationSignalId.DCL_SMART_ACC_CRUISE_STATE,
        AutomationSignalId.DCL_EFFECTIVE_SPEED_LIMIT,
        AutomationSignalId.DCL_SMART_ACC_SPEED_OFFSET,
        AutomationSignalId.DCL_SMART_ACC_TARGET_SPEED,
        -> AutomationSignalGroup.DCL

        AutomationSignalId.CAMD_RECORDER_ENABLED,
        AutomationSignalId.CAMD_RECORDING_ACTIVE,
        -> AutomationSignalGroup.CAMERAS
        AutomationSignalId.FUEL_LEVEL_PERCENT_FILTERED,
        AutomationSignalId.FUEL_LEVEL_LITERS,
        AutomationSignalId.ACTIVE_TRIP_DISTANCE_KM,
        AutomationSignalId.ACTIVE_TRIP_AVG_FUEL_L100KM,
        AutomationSignalId.ACTIVE_TRIP_DURATION_S,
        AutomationSignalId.ACTIVE_TRIP_MOTOR_HOURS,
        AutomationSignalId.MOTOR_HOURS,
        -> AutomationSignalGroup.TRIPS

        AutomationSignalId.MEDIA_TITLE,
        AutomationSignalId.MEDIA_ARTIST,
        AutomationSignalId.MEDIA_PLAYING,
        AutomationSignalId.MEDIA_POSITION_MS,
        AutomationSignalId.MEDIA_DURATION_MS,
        -> AutomationSignalGroup.MEDIA

        AutomationSignalId.ROAD_HIGHWAY_CLASS,
        AutomationSignalId.ROAD_SPEED_LIMIT_KMH,
        AutomationSignalId.ROAD_TUNNEL,
        AutomationSignalId.ROAD_SETTLEMENT,
        -> AutomationSignalGroup.ROADS
    }

    /** Русские синонимы для поиска; label и storageKey входят в поиск всегда. */
    fun signalKeywords(id: AutomationSignalId): List<String> = when (id) {
        AutomationSignalId.ENGINE_RPM -> listOf("тахометр")
        AutomationSignalId.CAR_SPEED -> listOf("скорость")
        AutomationSignalId.ENGINE_TEMPERATURE -> listOf("охлаждающая", "антифриз")
        AutomationSignalId.OUTSIDE_TEMPERATURE -> listOf("улица", "за бортом")
        AutomationSignalId.INSIDE_TEMPERATURE -> listOf("салон")
        AutomationSignalId.STEERING_WHEEL_HEAT -> listOf("обогрев", "руль", "печка")
        AutomationSignalId.WIPER_MAINTENANCE -> listOf("дворники", "сервис", "стеклоочиститель")
        AutomationSignalId.WIPER_STS -> listOf("дворники", "стеклоочиститель")
        AutomationSignalId.WIPER_SENSITIVITY -> listOf("дворники", "чувствительность", "дождь")
        AutomationSignalId.REAR_WIPER -> listOf("дворники", "задний")
        AutomationSignalId.RAIN_DETECTED -> listOf("дождь", "датчик")
        AutomationSignalId.HIGH_BEAM -> listOf("фары", "дальний")
        AutomationSignalId.HAZARD -> listOf("аварийная", "сигнализация")
        AutomationSignalId.TURN_SIGNAL_LEFT -> listOf("поворотник", "левый", "указатель")
        AutomationSignalId.TURN_SIGNAL_RIGHT -> listOf("поворотник", "правый", "указатель")
        AutomationSignalId.TURN_STABILIZED -> listOf("поворотник")
        AutomationSignalId.TURN_FLASH_COUNT -> listOf("поворотник", "мигания")
        AutomationSignalId.DRL -> listOf("фары", "ходовые", "габариты")
        AutomationSignalId.HEADLIGHT_MODE -> listOf("фары", "свет", "режим")
        AutomationSignalId.HEADLIGHTS_FOLLOW_ME_HOME -> listOf("фары", "коридор", "задержка")
        AutomationSignalId.LOW_BEAM_HEIGHT -> listOf("фары", "наклон", "высота", "ближний")
        AutomationSignalId.REAR_FOG -> listOf("фонарь", "противотуманный", "задний")
        AutomationSignalId.SUNSHADE -> listOf("шторка", "панорама", "крыша")
        AutomationSignalId.SUNROOF -> listOf("люк", "панорама", "крыша", "откинуть")
        AutomationSignalId.WINDOW_FRONT_LEFT -> listOf("стекло", "окно", "левое", "переднее")
        AutomationSignalId.WINDOW_FRONT_RIGHT -> listOf("стекло", "окно", "правое", "переднее")
        AutomationSignalId.WINDOW_REAR_LEFT -> listOf("стекло", "окно", "левое", "заднее")
        AutomationSignalId.WINDOW_REAR_RIGHT -> listOf("стекло", "окно", "правое", "заднее")
        AutomationSignalId.TRUNK_DOOR -> listOf("багажник", "пятая", "дверь", "крышка")
        AutomationSignalId.DOOR_FRONT_LEFT -> listOf("дверь", "левая", "передняя")
        AutomationSignalId.DOOR_FRONT_RIGHT -> listOf("дверь", "правая", "передняя")
        AutomationSignalId.DOOR_REAR_LEFT -> listOf("дверь", "левая", "задняя")
        AutomationSignalId.DOOR_REAR_RIGHT -> listOf("дверь", "правая", "задняя")
        AutomationSignalId.FRONT_LEFT_SEAT_MODE ->
            listOf("сиденье", "подогрев", "вентиляция", "левое", "переднее")
        AutomationSignalId.FRONT_RIGHT_SEAT_MODE ->
            listOf("сиденье", "подогрев", "вентиляция", "правое", "переднее")
        AutomationSignalId.REAR_LEFT_SEAT_MODE ->
            listOf("сиденье", "подогрев", "левое", "заднее")
        AutomationSignalId.REAR_RIGHT_SEAT_MODE ->
            listOf("сиденье", "подогрев", "правое", "заднее")
        AutomationSignalId.MIRROR_AUTO_FOLD -> listOf("зеркала", "складывание")
        AutomationSignalId.HVAC_AC_MAX -> listOf("климат", "кондиционер", "охлаждение")
        AutomationSignalId.HVAC_POWER -> listOf("климат", "кондиционер", "ac")
        AutomationSignalId.HVAC_AUTO -> listOf("климат", "авто", "печка")
        AutomationSignalId.HVAC_RECIRCULATION -> listOf("климат", "рециркуляция", "воздух")
        AutomationSignalId.HVAC_SYNC -> listOf("климат", "синхронизация", "зоны")
        AutomationSignalId.HVAC_CUSTOM_MODE -> listOf("климат", "eco", "комфорт", "сильный")
        AutomationSignalId.FRONT_WINDSCREEN_HEAT -> listOf("обогрев", "лобовое", "стекло", "печка")
        AutomationSignalId.HVAC_REAR_DEFROSTER -> listOf("обогрев", "заднее", "зеркала")
        AutomationSignalId.HVAC_AC_CLEAN_WHEN_LOCKED -> listOf("климат", "сушка", "очистка")
        AutomationSignalId.HVAC_ANION_PURIFY -> listOf("воздух", "анионы", "ионизация")
        AutomationSignalId.FRAGRANCE -> listOf("аромат", "запах", "парфюм")
        AutomationSignalId.FRAGRANCE_SMELL -> listOf("аромат", "запах")
        AutomationSignalId.FRAGRANCE_CONCENTRATION -> listOf("аромат", "интенсивность")
        AutomationSignalId.HVAC_FIRST_BLOWING -> listOf("климат", "продувка")
        AutomationSignalId.BT_REDUCE_FAN -> listOf("вентилятор", "bluetooth", "звонок")
        AutomationSignalId.HVAC_AUTO_VENTILATION ->
            listOf("климат", "вентиляция", "проветривание")
        AutomationSignalId.HVAC_FAN_DIRECTION -> listOf("климат", "обдув", "ноги", "лобовое")
        AutomationSignalId.HVAC_TEMPERATURE_LEFT -> listOf("климат", "температура", "левая")
        AutomationSignalId.HVAC_TEMPERATURE_RIGHT -> listOf("климат", "температура", "правая")
        AutomationSignalId.HVAC_FAN_SPEED -> listOf("климат", "вентилятор", "скорость")
        AutomationSignalId.HVAC_FRONT_OFF -> listOf("климат", "выключить")
        AutomationSignalId.HUD -> listOf("проектор", "проекция", "лобовое")
        AutomationSignalId.HUD_HEIGHT -> listOf("проектор", "высота")
        AutomationSignalId.HUD_BRIGHTNESS -> listOf("проектор", "яркость")
        AutomationSignalId.HUD_DISPLAY_MODE -> listOf("проектор", "снег")
        AutomationSignalId.HUD_AUTO_BRIGHTNESS -> listOf("проектор", "яркость", "авто")
        AutomationSignalId.ICM_BRIGHTNESS_MODE -> listOf("приборная", "панель", "авто")
        AutomationSignalId.ICM_BRIGHTNESS -> listOf("приборная", "панель", "яркость")
        AutomationSignalId.OVERSPEED_ALARM -> listOf("скорость", "превышение", "лимит")
        AutomationSignalId.ACC_CRUISE_STATE -> listOf("круиз", "acc", "автопилот")
        AutomationSignalId.CCS_CRUISE_STATE -> listOf("круиз", "ccs")
        AutomationSignalId.CRUISE_SET_SPEED -> listOf("круиз", "уставка")
        AutomationSignalId.ACC_TIME_GAP -> listOf("круиз", "дистанция")
        AutomationSignalId.PARKING_RADAR -> listOf("парктроник", "радар")
        AutomationSignalId.AVH -> listOf("auto hold", "автодержание")
        AutomationSignalId.HDC -> listOf("спуск", "съезд")
        AutomationSignalId.ESP_OFF -> listOf("esp", "стабилизация", "занос")
        AutomationSignalId.TJA_ICA -> listOf("ngp", "пилот", "полоса")
        AutomationSignalId.HMA -> listOf("дальний", "авто", "свет")
        AutomationSignalId.LAS_MODE -> listOf("полоса", "lka", "ldw")
        AutomationSignalId.BLIND_SPOT_DETECTION -> listOf("слепые", "зоны", "bsd")
        AutomationSignalId.DOOR_OPEN_WARNING -> listOf("дверь", "dow")
        AutomationSignalId.FCW -> listOf("столкновение", "fcw")
        AutomationSignalId.FCW_SENSITIVITY -> listOf("столкновение", "чувствительность")
        AutomationSignalId.LDW_SENSITIVITY -> listOf("полоса", "чувствительность")
        AutomationSignalId.TSR_SWITCH -> listOf("знаки", "sla", "ограничения")
        AutomationSignalId.STEERING_ANGLE -> emptyList()
        AutomationSignalId.STEERING_SPEED -> emptyList()
        AutomationSignalId.GAS_PEDAL -> listOf("газ", "акселератор")
        AutomationSignalId.BRAKE_PEDAL -> listOf("тормоз")
        AutomationSignalId.ODOMETER_KM -> listOf("пробег")
        AutomationSignalId.DISTANCE_TO_EMPTY_KM -> listOf("запас", "ход")
        AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM -> listOf("то", "сервис", "обслуживание")
        AutomationSignalId.VOLTAGE -> listOf("аккумулятор", "бортовая")
        AutomationSignalId.GEAR_MODE -> listOf("кпп", "парковка", "передача")
        AutomationSignalId.ACC_STATUS -> listOf("ключ", "зажигание")
        AutomationSignalId.IGNITION_KEY -> listOf("ключ", "зажигание", "стартер")
        AutomationSignalId.HU_SYSTEM_MODE -> listOf("гу", "сон", "питание")
        AutomationSignalId.ACC_TIME_GAP -> listOf("круиз", "дистанция")
        AutomationSignalId.CURRENT_GEAR -> listOf("кпп", "передача")
        AutomationSignalId.TARGET_GEAR -> listOf("кпп", "передача")
        AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE -> listOf("колесо", "шина", "давление")
        AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE -> listOf("колесо", "шина", "давление")
        AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE -> listOf("колесо", "шина", "давление")
        AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE -> listOf("колесо", "шина", "давление")
        AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE -> listOf("колесо", "шина")
        AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE -> listOf("колесо", "шина")
        AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE -> listOf("колесо", "шина")
        AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE -> listOf("колесо", "шина")
        AutomationSignalId.INSIDE_AIR_QUALITY -> listOf("pm25", "салон", "воздух")
        AutomationSignalId.OUTSIDE_AIR_QUALITY -> listOf("pm25", "воздух", "улица")
        AutomationSignalId.EPB_PARK_LAMP -> listOf("паркинг", "ручник", "epb")
        AutomationSignalId.ENGINE_OIL_PRESSURE -> listOf("масло", "давление")
        AutomationSignalId.BRAKE_FLUID -> listOf("тормоз", "жидкость")
        AutomationSignalId.FRM_DX_TAR_OBJ -> emptyList()
        AutomationSignalId.DRIVE_MODE -> listOf("движение", "eco", "sport", "режим")
        AutomationSignalId.DRIVE_MODE_6DCT -> listOf("движение", "кпп", "6dct")
        AutomationSignalId.STEERING_MODE -> listOf("руль", "усилитель")
        AutomationSignalId.EPS_MODE -> listOf("руль", "усилитель", "eps")
        AutomationSignalId.DOOR_AUTO_LOCK -> listOf("замки", "двери")
        AutomationSignalId.DOOR_IGNOFF_UNLOCK -> listOf("замки", "двери", "отпирание")
        AutomationSignalId.DRIVER_UNLOCK_MODE -> listOf("замки", "двери", "водитель")
        AutomationSignalId.REMOTE_LOCK_FEEDBACK ->
            listOf("замки", "сигнал", "свет", "подтверждение")
        AutomationSignalId.REVERSE_GEAR -> listOf("кпп", "назад")
        AutomationSignalId.AUDIO_VOLUME_SPEED_MODE -> listOf("громкость", "скорость", "аудио")
        AutomationSignalId.AUDIO_KEY_TONE_VOLUME -> listOf("громкость", "клавиши", "аудио")
        AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME -> listOf("громкость", "радар", "парктроник")
        AutomationSignalId.AUDIO_EQ_MODE -> listOf("эквалайзер", "аудио", "режим")
        AutomationSignalId.AUDIO_EQ_BASS -> listOf("эквалайзер", "бас", "низкие")
        AutomationSignalId.AUDIO_EQ_MIDDLE -> listOf("эквалайзер", "середина")
        AutomationSignalId.AUDIO_EQ_TREBLE -> listOf("эквалайзер", "высокие")
        AutomationSignalId.AUDIO_BALANCE -> listOf("аудио", "баланс")
        AutomationSignalId.AUDIO_FADER -> listOf("аудио", "фейдер")
        AutomationSignalId.GEO_SPEED_SOURCE -> listOf("gps", "источник")
        AutomationSignalId.GEO_SOURCE -> listOf("gps", "источник")
        AutomationSignalId.ESP_GPIO_IN_0 -> listOf("компаньон", "вход", "датчик")
        AutomationSignalId.ESP_GPIO_IN_1 -> listOf("компаньон", "вход", "датчик")
        AutomationSignalId.ESP_GPIO_IN_2 -> listOf("компаньон", "вход", "датчик")
        AutomationSignalId.ESP_GPIO_IN_3 -> listOf("компаньон", "вход", "датчик")
        AutomationSignalId.ESP_RELAY_0 -> listOf("компаньон", "реле")
        AutomationSignalId.ESP_RELAY_1 -> listOf("компаньон", "реле")
        AutomationSignalId.ESP_BLE_BOUND -> listOf("компаньон", "пульт", "shelly")
        AutomationSignalId.ESP_BLE_BATTERY -> listOf("компаньон", "пульт", "shelly", "заряд")
        AutomationSignalId.MODEM_NET_TYPE -> listOf("модем", "сеть", "2g", "3g", "4g")
        AutomationSignalId.MODEM_SIM_STATUS -> listOf("модем", "sim", "сим")
        AutomationSignalId.DCL_ADAS_PANEL_VISIBLE -> listOf("dcl", "панель", "adas")
        AutomationSignalId.DCL_TOP_BAR_VISIBLE -> listOf("dcl", "панель", "верхняя")
        AutomationSignalId.DCL_BOTTOM_BAR_VISIBLE -> listOf("dcl", "панель", "нижняя")
        AutomationSignalId.DCL_NOTIFICATION_SHADE_EXPANDED ->
            listOf("dcl", "шторка", "уведомления")
        AutomationSignalId.DCL_SCREEN_MODE -> listOf("dcl", "экран", "режим")
        AutomationSignalId.DCL_SMART_ACC_ACTIVE -> listOf("dcl", "умный", "acc", "круиз")
        AutomationSignalId.DCL_SMART_ACC_CONFIG_ENABLED ->
            listOf("dcl", "умный", "acc", "ограничение")
        AutomationSignalId.DCL_SMART_ACC_CRUISE_STATE ->
            listOf("dcl", "умный", "acc", "круиз")
        AutomationSignalId.DCL_EFFECTIVE_SPEED_LIMIT ->
            listOf("dcl", "лимит", "ограничение", "скорость")
        AutomationSignalId.DCL_SMART_ACC_SPEED_OFFSET -> listOf("dcl", "acc", "оффсет")
        AutomationSignalId.DCL_SMART_ACC_TARGET_SPEED -> listOf("dcl", "acc", "цель", "уставка")
        AutomationSignalId.PHONE_HFP_CONNECTED -> listOf("телефон", "bluetooth", "связь")
        AutomationSignalId.PHONE_CALL_STATE -> listOf("телефон", "звонок")
        AutomationSignalId.PHONE_CALL_DIRECTION -> listOf("телефон", "звонок")
        AutomationSignalId.PHONE_CALL_NUMBER -> listOf("телефон", "звонок", "номер")
        AutomationSignalId.GEAR_BOX_OIL_TEMPERATURE -> listOf("кпп", "масло")
        AutomationSignalId.ACTIVE_TRIP_DISTANCE_KM -> listOf("поездка", "пробег")
        AutomationSignalId.ACTIVE_TRIP_DURATION_S -> listOf("поездка", "длительность")
        AutomationSignalId.ACTIVE_TRIP_MOTOR_HOURS -> listOf("поездка", "моточасы")
        AutomationSignalId.ROAD_HIGHWAY_CLASS -> listOf("дорога", "osm", "класс")
        AutomationSignalId.MEDIA_TITLE -> listOf("трек", "музыка")
        AutomationSignalId.MEDIA_ARTIST -> listOf("исполнитель", "музыка")
        AutomationSignalId.MEDIA_PLAYING -> listOf("музыка", "пауза", "играет")
        AutomationSignalId.MEDIA_POSITION_MS -> listOf("музыка", "позиция")
        AutomationSignalId.MEDIA_DURATION_MS -> listOf("музыка", "длительность")
        AutomationSignalId.HU_SCREEN_BRIGHTNESS -> listOf("яркость", "подсветка", "экран")
        AutomationSignalId.HU_SCREEN_AUTO_BRIGHTNESS -> listOf("яркость", "авто", "экран")
        AutomationSignalId.HU_MEDIA_VOLUME -> listOf("громкость", "звук", "музыка")
        AutomationSignalId.HU_PHONE_VOLUME -> listOf("громкость", "звук", "звонок")
        AutomationSignalId.HU_NAVI_VOLUME -> listOf("громкость", "звук", "навигатор")
        AutomationSignalId.HU_VOICE_VOLUME -> listOf("громкость", "звук", "голос")
        AutomationSignalId.HU_HEADREST_SPEAKER -> listOf("динамик", "подголовник")
        AutomationSignalId.APP_THEME_MODE -> listOf("тема", "день", "ночь")
        AutomationSignalId.APP_THEME -> listOf("тема", "день", "ночь")
        AutomationSignalId.FOREGROUND_APP -> listOf("приложение", "запущено", "экран")
        AutomationSignalId.TBOX_CONNECTED -> listOf("связь", "udp", "модем")
        AutomationSignalId.MODEM_SIGNAL_LEVEL -> listOf("модем", "уровень", "палочки")
        AutomationSignalId.MODEM_RSSI_DBM -> listOf("модем", "rssi")
        AutomationSignalId.MODEM_REGISTERED -> listOf("модем", "сеть", "роуминг")
        AutomationSignalId.MODEM_OPERATOR -> listOf("модем", "оператор", "связь")
        AutomationSignalId.MODEM_SOURCE -> listOf("модем", "источник")
        AutomationSignalId.MODEM_MOBILE_DATA -> listOf("модем", "данные", "интернет")
        AutomationSignalId.MODEM_THROUGHPUT_DOWN_KBPS -> listOf("модем", "скорость", "загрузка")
        AutomationSignalId.MODEM_THROUGHPUT_UP_KBPS -> listOf("модем", "скорость", "отдача")
        AutomationSignalId.WIFI_ENABLED -> listOf("вайфай", "wi-fi", "сеть", "радио")
        AutomationSignalId.WIFI_ASSOCIATED -> listOf("вайфай", "wi-fi", "сеть", "подключение")
        AutomationSignalId.WIFI_SSID -> listOf("вайфай", "wi-fi", "сеть", "ssid")
        AutomationSignalId.WIFI_MODEM_LINK_STATUS ->
            listOf("модем", "вайфай", "админка", "связь")
        AutomationSignalId.HU_INTERNET_STATUS -> listOf("интернет", "сеть", "проверка")
        AutomationSignalId.GEO_POSITION -> listOf("gps", "координаты", "место")
        AutomationSignalId.GEO_ALTITUDE_M -> listOf("gps", "высота")
        AutomationSignalId.GEO_SPEED_KMH -> listOf("gps", "скорость")
        AutomationSignalId.GEO_BEARING_DEG -> listOf("gps", "курс", "компас")
        AutomationSignalId.GEO_SATELLITES_VISIBLE -> listOf("gps", "спутники")
        AutomationSignalId.GEO_SATELLITES_USED -> listOf("gps", "спутники")
        AutomationSignalId.GEO_FIX_VALID -> listOf("gps", "правдивость", "фикс")
        AutomationSignalId.LOCATE_STATUS -> listOf("gps", "фиксация")
        AutomationSignalId.FUEL_LEVEL_PERCENT -> listOf("топливо", "бак")
        AutomationSignalId.FUEL_LEVEL_PERCENT_FILTERED -> listOf("топливо", "фильтр", "бак")
        AutomationSignalId.FUEL_LEVEL_LITERS -> listOf("топливо", "литры", "калибровка")
        AutomationSignalId.CURRENT_FUEL_CONSUMPTION -> listOf("расход", "топливо")
        AutomationSignalId.ACTIVE_TRIP_AVG_FUEL_L100KM -> listOf("поездка", "расход")
        AutomationSignalId.MOTOR_HOURS -> listOf("моточасы", "пробег")
        AutomationSignalId.ROAD_SPEED_LIMIT_KMH -> listOf("лимит", "ограничение", "osm")
        AutomationSignalId.ROAD_TUNNEL -> listOf("тоннель", "туннель")
        AutomationSignalId.ROAD_SETTLEMENT -> listOf("нп", "город", "населённый")
        AutomationSignalId.CAMD_RECORDER_ENABLED -> listOf("камеры", "регистратор", "запись")
        AutomationSignalId.CAMD_RECORDING_ACTIVE -> listOf("камеры", "запись")
        else -> emptyList()
    }

    /** UI label for a state option; prefers catalog [AutomationSignalDescriptor.namedValues]. */
    fun stateOptionLabel(signal: AutomationSignalId, raw: String): String {
        val named = get(signal).namedValues.firstOrNull {
            it.value.equals(raw.trim(), ignoreCase = true)
        }?.label
        return named?.takeIf { it.isNotBlank() } ?: stateOptionLabel(raw)
    }

    /** UI label for a state trigger option; uses transition wording for tunnel/settlement enter/exit. */
    fun triggerStateOptionLabel(signal: AutomationSignalId, raw: String): String {
        if (signal == AutomationSignalId.ROAD_TUNNEL) {
            return when (raw.trim().lowercase()) {
                "on" -> ruEn("Въезд в тоннель", "Enter tunnel")
                "off" -> ruEn("Выезд из тоннеля", "Exit tunnel")
                else -> stateOptionLabel(signal, raw)
            }
        }
        if (signal == AutomationSignalId.ROAD_SETTLEMENT) {
            return when (raw.trim().lowercase()) {
                "on" -> ruEn("Въезд в НП", "Enter settlement")
                "off" -> ruEn("Выезд из НП", "Exit settlement")
                else -> stateOptionLabel(signal, raw)
            }
        }
        return stateOptionLabel(signal, raw)
    }

    /** Default state option when a signal is selected in a trigger editor. */
    fun defaultTriggerStateOption(signal: AutomationSignalId): String? = when (signal) {
        AutomationSignalId.ROAD_TUNNEL,
        AutomationSignalId.ROAD_SETTLEMENT,
        -> "on"
        else -> get(signal).stateOptions.firstOrNull()
    }
}

private fun formatNamedValues(values: List<AutomationSignalNamedValue>): String =
    values.joinToString { named ->
        val label = named.label.trim()
        val value = named.value.trim()
        when {
            // Case-sensitive: English "Off" must stay next to storage key "off".
            label.isEmpty() || label == value -> value
            else -> "$label ($value)"
        }
    }

private val automationLabelCollator: Collator =
    Collator.getInstance(
        if (BuildConfig.FLAVOR.trim().lowercase() == "en") {
            Locale.ENGLISH
        } else {
            Locale.forLanguageTag("ru-RU")
        },
    ).apply {
        strength = Collator.PRIMARY
    }

internal fun <T> List<T>.sortedByAutomationLabel(labelOf: (T) -> String): List<T> =
    sortedWith(compareBy(automationLabelCollator, labelOf))
