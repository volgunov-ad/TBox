package vad.dashing.tbox.automation

import java.text.Collator
import java.util.Locale
import vad.dashing.tbox.DRIVE_MODE_WIDGET_OPTIONS
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.mbcan.AccStatusDomain
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
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
    private val bothSources = setOf(
        AutomationSignalSource.TBOX,
        AutomationSignalSource.HEAD_UNIT,
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
    private val driveModeNamedValues = DRIVE_MODE_WIDGET_OPTIONS
        .filter { it.propertyId == MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE }
        .map { AutomationSignalNamedValue(it.propertyValue.toString(), it.label) }
    private val headlightNamedValues = HeadlightMode.entries.map {
        AutomationSignalNamedValue(it.rawValue.toString(), it.widgetLabel)
    }
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
        state(AutomationSignalId.HVAC_AUTO, "Автоматический климат", headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_RECIRCULATION, "Рециркуляция", headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_SYNC, "Синхронизация климата", headUnitOnly, binaryStates),
        number(
            AutomationSignalId.DRIVE_MODE,
            "Режим движения (raw)",
            "",
            headUnitOnly,
            namedValues = driveModeNamedValues,
            typicalRange = "Это raw стандартного режима ГУ, не значения 6DCT виджета (100…102)",
        ),
        number(
            AutomationSignalId.HEADLIGHT_MODE,
            "Режим фар (raw)",
            "",
            headUnitOnly,
            namedValues = headlightNamedValues,
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

    fun supports(id: AutomationSignalId, source: AutomationSignalSource): Boolean =
        source in get(id).sources

    fun signalsOfType(valueType: AutomationSignalValueType): List<AutomationSignalId> =
        entries.filter { it.id.valueType == valueType }
            .sortedByAutomationLabel { it.label }
            .map { it.id }

    fun stateOptionLabel(raw: String): String = when (raw.trim().lowercase()) {
        "on" -> "Включено"
        "off" -> "Выключено"
        "acc" -> "ACC ON"
        "ign" -> "ON"
        "int" -> "INT"
        "low" -> "Low"
        "high" -> "High"
        "closed" -> "Закрыто"
        "open" -> "Открыто"
        "tilt" -> "Откинут"
        "vent" -> "Щель"
        "heat_1" -> "Подогрев 1"
        "heat_2" -> "Подогрев 2"
        "heat_3" -> "Подогрев 3"
        "vent_1" -> "Вентиляция 1"
        "vent_2" -> "Вентиляция 2"
        "vent_3" -> "Вентиляция 3"
        else -> raw
    }

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
