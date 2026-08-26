package vad.dashing.tbox.automation

import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.FirmwareVehicleJsonMapper
import vad.dashing.tbox.mbcan.MbCanAudioCommandRegistry
import vad.dashing.tbox.mbcan.MbCanCommandPolicy
import vad.dashing.tbox.mbcan.MbCanCommandRegistry
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

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
            is MbCanCommandPolicy.SetAnyInt -> emptyList()
        }

    val defaultValue: Int
        get() = when (policy) {
            is MbCanCommandPolicy.ToggleBinary -> policy.onValue
            else -> allowedValues.firstOrNull() ?: 0
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
