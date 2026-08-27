package vad.dashing.tbox.automation

import vad.dashing.tbox.DRIVE_MODE_WIDGET_OPTIONS
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

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
    )

    private val byId = entries.associateBy { it.id }

    fun get(id: AutomationSignalId): AutomationSignalDescriptor = requireNotNull(byId[id])

    fun supports(id: AutomationSignalId, source: AutomationSignalSource): Boolean =
        source in get(id).sources

    fun stateOptionLabel(raw: String): String = when (raw.trim().lowercase()) {
        "on" -> "Включено"
        "off" -> "Выключено"
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
    ) = AutomationSignalDescriptor(
        id = id,
        label = label,
        sources = sources,
        stateOptions = options,
        namedValues = options.map { AutomationSignalNamedValue(it, stateOptionLabel(it)) },
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
