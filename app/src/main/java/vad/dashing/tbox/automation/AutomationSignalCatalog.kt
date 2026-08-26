package vad.dashing.tbox.automation

data class AutomationSignalDescriptor(
    val id: AutomationSignalId,
    val label: String,
    val unit: String = "",
    val sources: Set<AutomationSignalSource>,
    val stateOptions: List<String> = emptyList(),
)

object AutomationSignalCatalog {
    private val bothSources = AutomationSignalSource.entries.toSet()
    private val headUnitOnly = setOf(AutomationSignalSource.HEAD_UNIT)
    private val tboxOnly = setOf(AutomationSignalSource.TBOX)
    private val binaryStates = listOf("off", "on")

    val entries: List<AutomationSignalDescriptor> = listOf(
        number(AutomationSignalId.ENGINE_RPM, "Обороты двигателя", "об/мин", bothSources),
        number(AutomationSignalId.CAR_SPEED, "Скорость автомобиля", "км/ч", bothSources),
        number(AutomationSignalId.ENGINE_TEMPERATURE, "Температура двигателя", "°C", bothSources),
        number(AutomationSignalId.OUTSIDE_TEMPERATURE, "Температура снаружи", "°C", bothSources),
        number(AutomationSignalId.INSIDE_TEMPERATURE, "Температура в салоне", "°C", tboxOnly),
        number(AutomationSignalId.FUEL_LEVEL_PERCENT, "Уровень топлива", "%", bothSources),
        number(AutomationSignalId.ODOMETER_KM, "Одометр", "км", bothSources),
        number(AutomationSignalId.CURRENT_FUEL_CONSUMPTION, "Текущий расход топлива", "л/100 км", bothSources),
        number(AutomationSignalId.DISTANCE_TO_EMPTY_KM, "Запас хода", "км", bothSources),
        number(AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM, "До обслуживания", "км", bothSources),
        number(AutomationSignalId.VOLTAGE, "Напряжение", "В", tboxOnly),
        number(AutomationSignalId.STEERING_ANGLE, "Угол руля", "°", bothSources),
        number(AutomationSignalId.STEERING_SPEED, "Скорость вращения руля", "°/с", bothSources),
        number(AutomationSignalId.CRUISE_SET_SPEED, "Уставка круиза", "км/ч", bothSources),
        state(AutomationSignalId.GEAR_MODE, "Режим КПП", bothSources, listOf("P", "R", "N", "D")),
        number(AutomationSignalId.CURRENT_GEAR, "Текущая передача", "", tboxOnly),
        number(AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE, "Давление переднего левого колеса", "бар", bothSources),
        number(AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE, "Давление переднего правого колеса", "бар", bothSources),
        number(AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE, "Давление заднего левого колеса", "бар", bothSources),
        number(AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE, "Давление заднего правого колеса", "бар", bothSources),
        number(AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE, "Температура переднего левого колеса", "°C", bothSources),
        number(AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE, "Температура переднего правого колеса", "°C", bothSources),
        number(AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE, "Температура заднего левого колеса", "°C", bothSources),
        number(AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE, "Температура заднего правого колеса", "°C", bothSources),
        number(AutomationSignalId.INSIDE_AIR_QUALITY, "Качество воздуха в салоне", "", bothSources),
        number(AutomationSignalId.OUTSIDE_AIR_QUALITY, "Качество наружного воздуха", "", bothSources),
        state(AutomationSignalId.STEERING_WHEEL_HEAT, "Обогрев руля", headUnitOnly, binaryStates),
        state(AutomationSignalId.WIPER_MAINTENANCE, "Сервисное положение дворников", headUnitOnly, binaryStates),
        state(AutomationSignalId.PARKING_RADAR, "Парковочный радар", headUnitOnly, binaryStates),
        state(AutomationSignalId.REAR_FOG, "Задний противотуманный фонарь", headUnitOnly, binaryStates),
        state(AutomationSignalId.AVH, "Auto Hold (AVH)", headUnitOnly, binaryStates),
        state(AutomationSignalId.HDC, "HDC", headUnitOnly, binaryStates),
        state(AutomationSignalId.ESP_OFF, "Отключение ESP", headUnitOnly, binaryStates),
        state(AutomationSignalId.TJA_ICA, "TJA/ICA", headUnitOnly, binaryStates),
        state(AutomationSignalId.HMA, "Автоматический дальний свет HMA", headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_AC_MAX, "AC MAX", headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_POWER, "Питание климата", headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_AUTO, "Автоматический климат", headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_RECIRCULATION, "Рециркуляция", headUnitOnly, binaryStates),
        state(AutomationSignalId.HVAC_SYNC, "Синхронизация климата", headUnitOnly, binaryStates),
        number(AutomationSignalId.DRIVE_MODE, "Режим движения (raw)", "", headUnitOnly),
        number(AutomationSignalId.HEADLIGHT_MODE, "Режим фар (raw)", "", headUnitOnly),
        state(AutomationSignalId.REVERSE_GEAR, "Задняя передача", headUnitOnly, binaryStates),
        state(AutomationSignalId.FRONT_LEFT_SEAT_MODE, "Левое переднее сиденье", headUnitOnly),
        state(AutomationSignalId.FRONT_RIGHT_SEAT_MODE, "Правое переднее сиденье", headUnitOnly),
        state(AutomationSignalId.REAR_LEFT_SEAT_MODE, "Левое заднее сиденье", headUnitOnly),
        state(AutomationSignalId.REAR_RIGHT_SEAT_MODE, "Правое заднее сиденье", headUnitOnly),
    )

    private val byId = entries.associateBy { it.id }

    fun get(id: AutomationSignalId): AutomationSignalDescriptor = requireNotNull(byId[id])

    fun supports(id: AutomationSignalId, source: AutomationSignalSource): Boolean =
        source in get(id).sources

    private fun number(
        id: AutomationSignalId,
        label: String,
        unit: String,
        sources: Set<AutomationSignalSource>,
    ) = AutomationSignalDescriptor(id, label, unit, sources)

    private fun state(
        id: AutomationSignalId,
        label: String,
        sources: Set<AutomationSignalSource>,
        options: List<String> = emptyList(),
    ) = AutomationSignalDescriptor(id, label, sources = sources, stateOptions = options)
}
