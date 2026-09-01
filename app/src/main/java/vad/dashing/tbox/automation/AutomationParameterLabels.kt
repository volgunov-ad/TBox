package vad.dashing.tbox.automation

import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

/**
 * Shared Russian labels for automation signal and CAN action catalogs.
 *
 * [AutomationSignalCatalog] and [AutomationCanCatalog] must stay aligned on wording.
 */
object AutomationParameterLabels {
    fun vehicleLabel(propertyId: Int): String = when (propertyId) {
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

    fun audioLabel(propertyId: Int): String = when (propertyId) {
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

    fun signalLabel(signal: AutomationSignalId): String = when (signal) {
        AutomationSignalId.ENGINE_RPM -> "Обороты двигателя"
        AutomationSignalId.CAR_SPEED -> "Скорость автомобиля"
        AutomationSignalId.ENGINE_TEMPERATURE -> "Температура двигателя"
        AutomationSignalId.OUTSIDE_TEMPERATURE -> "Температура снаружи"
        AutomationSignalId.INSIDE_TEMPERATURE -> "Температура в салоне"
        AutomationSignalId.FUEL_LEVEL_PERCENT -> "Уровень топлива"
        AutomationSignalId.ODOMETER_KM -> "Одометр"
        AutomationSignalId.CURRENT_FUEL_CONSUMPTION -> "Текущий расход топлива"
        AutomationSignalId.DISTANCE_TO_EMPTY_KM -> "Запас хода"
        AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM -> "До обслуживания"
        AutomationSignalId.VOLTAGE -> "Напряжение"
        AutomationSignalId.STEERING_ANGLE -> "Угол руля"
        AutomationSignalId.STEERING_SPEED -> "Скорость вращения руля"
        AutomationSignalId.CRUISE_SET_SPEED -> "Уставка круиза"
        AutomationSignalId.GEAR_MODE -> "Режим КПП"
        AutomationSignalId.ACC_STATUS -> "Статус ACC (ключ)"
        AutomationSignalId.GAS_PEDAL -> "Педаль газа"
        AutomationSignalId.BRAKE_PEDAL -> "Педаль тормоза"
        AutomationSignalId.CURRENT_GEAR -> "Текущая передача"
        AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE -> "Давление переднего левого колеса"
        AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE -> "Давление переднего правого колеса"
        AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE -> "Давление заднего левого колеса"
        AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE -> "Давление заднего правого колеса"
        AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE -> "Температура переднего левого колеса"
        AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE -> "Температура переднего правого колеса"
        AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE -> "Температура заднего левого колеса"
        AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE -> "Температура заднего правого колеса"
        AutomationSignalId.INSIDE_AIR_QUALITY -> "Качество воздуха в салоне"
        AutomationSignalId.OUTSIDE_AIR_QUALITY -> "Качество наружного воздуха"
        AutomationSignalId.STEERING_WHEEL_HEAT -> vehicleLabel(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH)
        AutomationSignalId.WIPER_MAINTENANCE -> vehicleLabel(MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH)
        AutomationSignalId.WIPER_STS -> "Режим дворников"
        AutomationSignalId.RAIN_DETECTED -> "Детектор дождя"
        AutomationSignalId.SUNSHADE -> vehicleLabel(MbCanKnownVehiclePropertyId.SUNSHADE_POS)
        AutomationSignalId.SUNROOF -> vehicleLabel(MbCanKnownVehiclePropertyId.SUNROOF_CONTROL)
        AutomationSignalId.WINDOW_FRONT_LEFT -> vehicleLabel(MbCanKnownVehiclePropertyId.WINDOW_FL_POS)
        AutomationSignalId.WINDOW_FRONT_RIGHT -> vehicleLabel(MbCanKnownVehiclePropertyId.WINDOW_FR_POS)
        AutomationSignalId.WINDOW_REAR_LEFT -> vehicleLabel(MbCanKnownVehiclePropertyId.WINDOW_RL_POS)
        AutomationSignalId.WINDOW_REAR_RIGHT -> vehicleLabel(MbCanKnownVehiclePropertyId.WINDOW_RR_POS)
        AutomationSignalId.PARKING_RADAR -> vehicleLabel(MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH)
        AutomationSignalId.REAR_FOG -> vehicleLabel(MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT)
        AutomationSignalId.AVH -> vehicleLabel(MbCanKnownVehiclePropertyId.AVH_SWITCH)
        AutomationSignalId.HDC -> vehicleLabel(MbCanKnownVehiclePropertyId.HDC_SWITCH)
        AutomationSignalId.ESP_OFF -> vehicleLabel(MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH)
        AutomationSignalId.TJA_ICA -> vehicleLabel(MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH)
        AutomationSignalId.HMA -> vehicleLabel(MbCanKnownVehiclePropertyId.HMA_SWITCH)
        AutomationSignalId.HVAC_AC_MAX -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_AC_MAX)
        AutomationSignalId.HVAC_POWER -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_POWER)
        AutomationSignalId.HVAC_AUTO -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE)
        AutomationSignalId.HVAC_RECIRCULATION -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION)
        AutomationSignalId.HVAC_SYNC -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH)
        AutomationSignalId.DRIVE_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE)
        AutomationSignalId.HEADLIGHT_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.LIGHTCONTROL)
        AutomationSignalId.REVERSE_GEAR -> "Задняя передача"
        AutomationSignalId.FRONT_LEFT_SEAT_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH)
        AutomationSignalId.FRONT_RIGHT_SEAT_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH)
        AutomationSignalId.REAR_LEFT_SEAT_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH)
        AutomationSignalId.REAR_RIGHT_SEAT_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH)
        AutomationSignalId.GEO_POSITION -> "Геопозиция"
        AutomationSignalId.DOOR_AUTO_LOCK -> vehicleLabel(MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK)
        AutomationSignalId.DOOR_IGNOFF_UNLOCK -> vehicleLabel(MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK)
        AutomationSignalId.HEADLIGHTS_FOLLOW_ME_HOME -> vehicleLabel(MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY)
        AutomationSignalId.DRIVER_UNLOCK_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE)
        AutomationSignalId.REMOTE_LOCK_FEEDBACK -> vehicleLabel(MbCanKnownVehiclePropertyId.DEFENCES_PROMPT)
        AutomationSignalId.WIPER_SENSITIVITY -> vehicleLabel(MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY)
        AutomationSignalId.REAR_WIPER -> vehicleLabel(MbCanKnownVehiclePropertyId.REAR_WIPER)
        AutomationSignalId.MIRROR_AUTO_FOLD -> vehicleLabel(MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW)
        AutomationSignalId.LOW_BEAM_HEIGHT -> vehicleLabel(MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST)
        AutomationSignalId.TURN_FLASH_COUNT -> vehicleLabel(MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT)
        AutomationSignalId.LAS_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION)
        AutomationSignalId.BLIND_SPOT_DETECTION -> vehicleLabel(MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION)
        AutomationSignalId.DOOR_OPEN_WARNING -> vehicleLabel(MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING)
        AutomationSignalId.FCW -> vehicleLabel(MbCanKnownVehiclePropertyId.FCW_SWITCH)
        AutomationSignalId.FCW_SENSITIVITY -> vehicleLabel(MbCanKnownVehiclePropertyId.FCW_SENSITIVITY)
        AutomationSignalId.LDW_SENSITIVITY -> vehicleLabel(MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL)
        AutomationSignalId.HVAC_CUSTOM_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_CUSTOM)
        AutomationSignalId.FRONT_WINDSCREEN_HEAT -> vehicleLabel(MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH)
        AutomationSignalId.HVAC_REAR_DEFROSTER -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH)
        AutomationSignalId.HVAC_AC_CLEAN_WHEN_LOCKED -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY)
        AutomationSignalId.HVAC_ANION_PURIFY -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_AQS)
        AutomationSignalId.FRAGRANCE -> vehicleLabel(MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH)
        AutomationSignalId.FRAGRANCE_SMELL -> vehicleLabel(MbCanKnownVehiclePropertyId.FRAGRANCE_SMELL)
        AutomationSignalId.FRAGRANCE_CONCENTRATION -> vehicleLabel(MbCanKnownVehiclePropertyId.FRAGRANCE_CONCENTRATION)
        AutomationSignalId.HVAC_FIRST_BLOWING -> vehicleLabel(MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH)
        AutomationSignalId.BT_REDUCE_FAN -> vehicleLabel(MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED)
        AutomationSignalId.HVAC_AUTO_VENTILATION -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH)
        AutomationSignalId.HVAC_FAN_DIRECTION -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION)
        AutomationSignalId.HVAC_TEMPERATURE_LEFT -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT)
        AutomationSignalId.HVAC_TEMPERATURE_RIGHT -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT)
        AutomationSignalId.HVAC_FAN_SPEED -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED)
        AutomationSignalId.HVAC_FRONT_OFF -> vehicleLabel(MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF)
        AutomationSignalId.HUD -> vehicleLabel(MbCanKnownVehiclePropertyId.HUD_SWITCH)
        AutomationSignalId.HUD_HEIGHT -> vehicleLabel(MbCanKnownVehiclePropertyId.HUD_HEIGHT)
        AutomationSignalId.HUD_BRIGHTNESS -> vehicleLabel(MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS)
        AutomationSignalId.HUD_DISPLAY_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE)
        AutomationSignalId.HUD_AUTO_BRIGHTNESS -> vehicleLabel(MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS)
        AutomationSignalId.ICM_BRIGHTNESS_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE)
        AutomationSignalId.ICM_BRIGHTNESS -> vehicleLabel(MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL)
        AutomationSignalId.OVERSPEED_ALARM -> vehicleLabel(MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET)
        AutomationSignalId.STEERING_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_STEERING_MODE)
        AutomationSignalId.EPS_MODE -> vehicleLabel(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE)
        AutomationSignalId.DRIVE_MODE_6DCT -> vehicleLabel(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET)
        AutomationSignalId.TSR_SWITCH -> vehicleLabel(MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH)
        AutomationSignalId.TRUNK_DOOR -> vehicleLabel(MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL)
        AutomationSignalId.AUDIO_VOLUME_SPEED_MODE -> audioLabel(MbCanKnownAudioPropertyId.VOLUME_SPEED)
        AutomationSignalId.AUDIO_KEY_TONE_VOLUME -> audioLabel(MbCanKnownAudioPropertyId.VOLUME_KEY)
        AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME -> audioLabel(MbCanKnownAudioPropertyId.VOLUME_RADAR)
        AutomationSignalId.AUDIO_EQ_MODE -> audioLabel(MbCanKnownAudioPropertyId.EQ_MODE)
        AutomationSignalId.AUDIO_EQ_BASS -> audioLabel(MbCanKnownAudioPropertyId.EQ_BAND_BASS)
        AutomationSignalId.AUDIO_EQ_MIDDLE -> audioLabel(MbCanKnownAudioPropertyId.EQ_BAND_MIDDLE)
        AutomationSignalId.AUDIO_EQ_TREBLE -> audioLabel(MbCanKnownAudioPropertyId.EQ_BAND_TREBLE)
        AutomationSignalId.AUDIO_BALANCE -> audioLabel(MbCanKnownAudioPropertyId.BALANCE)
        AutomationSignalId.AUDIO_FADER -> audioLabel(MbCanKnownAudioPropertyId.FADER)
        AutomationSignalId.ESP_GPIO_IN_0 -> "ESP-вход 0"
        AutomationSignalId.ESP_GPIO_IN_1 -> "ESP-вход 1"
        AutomationSignalId.ESP_GPIO_IN_2 -> "ESP-вход 2"
        AutomationSignalId.ESP_GPIO_IN_3 -> "ESP-вход 3"
        AutomationSignalId.ESP_RELAY_0 -> "ESP-реле 0"
        AutomationSignalId.ESP_RELAY_1 -> "ESP-реле 1"
        AutomationSignalId.FOREGROUND_APP -> "Приложение на экране"
    }
}
