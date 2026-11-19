package vad.dashing.tbox.automation

import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

/**
 * User-facing group headers for the merged «Действия» picker (builtin + CAN).
 * Pure Kotlin — no Android imports, unit-testable on JVM.
 */
enum class AutomationActionGroup(val label: String) {
    PROGRAM("Программа и поездка"),
    SCREEN("Экран и тема"),
    MEDIA_SOUND("Медиа и звук"),
    PHONE("Телефон"),
    NETWORK("Wi-Fi и сеть"),
    AUTOMATIONS("Автоматизации"),
    ESP("ESP-компаньон"),
    DCL("DCL"),
    CAMERAS("Камеры"),
    CAN_CLIMATE("Автомобиль (CAN): климат"),
    CAN_LIGHTS_LOCKS("Автомобиль (CAN): свет и замки"),
    CAN_SEATS("Автомобиль (CAN): сиденья и комфорт"),
    CAN_BODY("Автомобиль (CAN): кузов"),
    CAN_ADAS("Автомобиль (CAN): ADAS и круиз"),
    CAN_PANELS("Автомобиль (CAN): панели и приборы"),
    CAN_AUDIO("Автомобиль (CAN): аудио"),
}

/**
 * Русские синонимы поверх официальных подписей; поиск идёт и по ним.
 * Ключи канонические слова в нижнем регистре, ё → е.
 */
fun builtinActionKeywords(type: AutomationBuiltinActionType): List<String> = when (type) {
    AutomationBuiltinActionType.OPEN_MENU -> listOf("меню")
    AutomationBuiltinActionType.FINISH_AND_START_TRIP -> listOf("поездка", "заправка")
    AutomationBuiltinActionType.RESET_MOTOR_HOURS -> listOf("моточасы", "пробег")
    AutomationBuiltinActionType.RESTART_TBOX -> listOf("перезагрузка", "модем", "тбокс")
    AutomationBuiltinActionType.TOGGLE_APP_DAY_NIGHT_THEME ->
        listOf("тема", "день", "ночь", "светлая", "тёмная")
    AutomationBuiltinActionType.ENABLE_HEAD_UNIT_AUTO_THEME ->
        listOf("тема", "день", "ночь", "авто")
    AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME ->
        listOf("тема", "день", "ночь", "светлая", "тёмная", "авто")
    AutomationBuiltinActionType.TOGGLE_MIRROR_ADJUST_MODE ->
        listOf("зеркала", "регулировка")
    AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS ->
        listOf("панели", "плавающие", "виджет", "скрыть", "показать")
    AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED ->
        listOf("панели", "плавающие", "виджет", "включить", "выключить")
    AutomationBuiltinActionType.ESP_RELAY_SET -> emptyList()
    AutomationBuiltinActionType.ESP_RELAY_TOGGLE -> listOf("реле", "канал", "эсп")
    AutomationBuiltinActionType.ESP_RELAY_PULSE -> listOf("реле", "канал", "импульс", "эсп")
    AutomationBuiltinActionType.MEDIA_PREVIOUS -> listOf("трек", "музыка", "назад")
    AutomationBuiltinActionType.MEDIA_PLAY_PAUSE -> listOf("трек", "музыка", "пауза", "старт")
    AutomationBuiltinActionType.MEDIA_PLAY -> listOf("трек", "музыка", "старт", "играть")
    AutomationBuiltinActionType.MEDIA_NEXT -> listOf("трек", "музыка", "вперёд")
    AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE -> listOf("нравится", "лайк", "музыка")
    AutomationBuiltinActionType.SET_MEDIA_VOLUME -> listOf("громкость", "звук", "музыка")
    AutomationBuiltinActionType.SET_PHONE_VOLUME -> listOf("громкость", "звук", "звонок")
    AutomationBuiltinActionType.SET_NAVI_VOLUME -> listOf("громкость", "звук", "навигатор")
    AutomationBuiltinActionType.SET_VOICE_VOLUME -> listOf("громкость", "звук", "голос", "ттс")
    AutomationBuiltinActionType.SET_HEADREST_SPEAKER ->
        listOf("динамик", "подголовник", "звук", "задние")
    AutomationBuiltinActionType.CYCLE_MOCK_LOCATION_MODE ->
        listOf("геопозиция", "подмена", "локация", "gps")
    AutomationBuiltinActionType.GNSS_MODULE_REBOOT ->
        listOf("геопозиция", "gps", "антенна", "перезагрузка")
    AutomationBuiltinActionType.SET_SIMULATED_LOCATION_SOURCE_LOSS ->
        listOf("геопозиция", "потеря", "симуляция", "gps")
    AutomationBuiltinActionType.SET_GEO_DEBUG_LOG ->
        listOf("геопозиция", "журнал", "лог", "отладка")
    AutomationBuiltinActionType.WIFI_SET_ENABLED -> listOf("вайфай", "сеть", "радио")
    AutomationBuiltinActionType.WIFI_CONNECT -> listOf("вайфай", "сеть", "подключение")
    AutomationBuiltinActionType.WIFI_DISCONNECT -> listOf("вайфай", "сеть", "отключение")
    AutomationBuiltinActionType.WIFI_MODEM_SET_DATA ->
        listOf("модем", "данные", "интернет", "apn")
    AutomationBuiltinActionType.WIFI_MODEM_REBOOT ->
        listOf("модем", "перезагрузка", "роутер")
    AutomationBuiltinActionType.ADB_SET_TCP -> listOf("adb", "отладка", "порт", "5555")
    AutomationBuiltinActionType.ADB_SHELL -> listOf("adb", "команда", "shell", "консоль")
    AutomationBuiltinActionType.ADB_FORCE_STOP -> listOf("adb", "приложение", "закрыть", "убить")
    AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS ->
        listOf("яркость", "подсветка", "экран")
    AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS ->
        listOf("яркость", "авто", "подсветка", "экран")
    AutomationBuiltinActionType.SHOW_TOAST -> listOf("уведомление", "всплывашка", "тост")
    AutomationBuiltinActionType.SHOW_ALERT ->
        listOf("сообщение", "окно", "уведомление", "диалог")
    AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET ->
        listOf("виджет", "триггер", "плитка", "активировать")
    AutomationBuiltinActionType.SET_AUTOMATION_ENABLED ->
        listOf("правило", "включить", "выключить", "автоматизация")
    AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET ->
        listOf("круиз", "acc", "ccs", "уставка", "скорость", "автопилот")
    AutomationBuiltinActionType.CRUISE_PAUSE ->
        listOf("круиз", "acc", "ccs", "пауза", "cancel")
    AutomationBuiltinActionType.CRUISE_FULL_OFF ->
        listOf("круиз", "acc", "ccs", "выключить", "выкл")
    AutomationBuiltinActionType.CRUISE_RESUME ->
        listOf("круиз", "acc", "ccs", "возобновить", "res")
    AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED ->
        listOf("круиз", "acc", "ccs", "текущая", "скорость")
    AutomationBuiltinActionType.CRUISE_NUDGE ->
        listOf("круиз", "acc", "ccs", "уставка", "плюс", "минус")
    AutomationBuiltinActionType.START_VAD_VOICE -> listOf("голос", "ассистент", "слушать")
    AutomationBuiltinActionType.PLACE_CALL ->
        listOf("звонок", "позвонить", "телефон", "номер")
    AutomationBuiltinActionType.ANSWER_CALL -> listOf("звонок", "ответить", "телефон")
    AutomationBuiltinActionType.HANGUP_CALL ->
        listOf("звонок", "сбросить", "отклонить", "телефон")
    AutomationBuiltinActionType.CAMD_WINDOW ->
        listOf("камера", "видео", "окно", "обзор", "avm")
    AutomationBuiltinActionType.CAMD_KEEP_AVM ->
        listOf("камера", "avm", "круговой", "обзор", "отключить")
    AutomationBuiltinActionType.CAMD_RECORDER ->
        listOf("камера", "регистратор", "запись", "rec")
    AutomationBuiltinActionType.DCL_SET_SMART_ACC ->
        listOf("dcl", "умный", "круиз", "acc", "ограничение")
    AutomationBuiltinActionType.DCL_TOGGLE_SMART_ACC ->
        listOf("dcl", "умный", "круиз", "acc", "переключить")
}

fun canActionKeywords(bus: AutomationCanBus, propertyId: Int): List<String> = when (bus) {
    AutomationCanBus.AUDIO -> when (propertyId) {
        MbCanKnownAudioPropertyId.VOLUME_SPEED -> listOf("громкость", "скорость")
        MbCanKnownAudioPropertyId.VOLUME_KEY -> listOf("громкость", "клавиши")
        MbCanKnownAudioPropertyId.VOLUME_RADAR -> listOf("громкость", "радар", "парктроник")
        MbCanKnownAudioPropertyId.EQ_MODE -> listOf("эквалайзер", "звук", "режим")
        MbCanKnownAudioPropertyId.EQ_BAND_BASS -> listOf("эквалайзер", "бас", "низкие")
        MbCanKnownAudioPropertyId.EQ_BAND_MIDDLE -> listOf("эквалайзер", "середина", "средние")
        MbCanKnownAudioPropertyId.EQ_BAND_TREBLE -> listOf("эквалайзер", "высокие", "верхи")
        MbCanKnownAudioPropertyId.BALANCE -> listOf("баланс", "звук")
        MbCanKnownAudioPropertyId.FADER -> listOf("фейдер", "звук", "перед", "зад")
        else -> emptyList()
    }

    AutomationCanBus.VEHICLE -> when (propertyId) {
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH -> listOf("обогрев", "руль", "печка")
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH ->
            listOf("дворники", "стеклоочиститель", "сервис")
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH -> listOf("парктроник", "радар")
        MbCanKnownVehiclePropertyId.AVH_SWITCH -> listOf("auto hold", "автодержание", "тормоз")
        MbCanKnownVehiclePropertyId.HDC_SWITCH -> listOf("спуск", "помощь", "съезд")
        MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH -> listOf("esp", "стабилизация", "занос")
        MbCanKnownVehiclePropertyId.LIGHTCONTROL -> listOf("фары", "свет", "режим", "габариты")
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT -> listOf("фонарь", "противотуманный", "свет")
        MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK -> listOf("замки", "двери", "запирание")
        MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK ->
            listOf("замки", "двери", "отпирание", "зажигание")
        MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY ->
            listOf("фары", "свет", "follow me", "задержка", "коридор")
        MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE ->
            listOf("замки", "двери", "отпирание", "водитель")
        MbCanKnownVehiclePropertyId.DEFENCES_PROMPT ->
            listOf("замки", "подтверждение", "сигнал", "свет", "сирена")
        MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY ->
            listOf("дворники", "стеклоочиститель", "чувствительность", "дождь")
        MbCanKnownVehiclePropertyId.REAR_WIPER ->
            listOf("дворники", "стеклоочиститель", "задний")
        MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW ->
            listOf("зеркала", "складывание", "авто")
        MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH ->
            listOf("зеркала", "складывание")
        MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST ->
            listOf("фары", "свет", "высота", "ближний", "наклон")
        MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT ->
            listOf("поворотник", "мигание", "комфорт")
        MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION ->
            listOf("полоса", "удержание", "lka", "ldw")
        MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH -> listOf("ngp", "пилот", "полоса")
        MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION ->
            listOf("слепые", "зоны", "bsd")
        MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING ->
            listOf("дверь", "предупреждение", "dow")
        MbCanKnownVehiclePropertyId.FCW_SWITCH ->
            listOf("столкновение", "fcw", "предупреждение")
        MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH ->
            listOf("aeb", "автоторможение", "тормоз")
        MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING ->
            listOf("дистанция", "предупреждение")
        MbCanKnownVehiclePropertyId.FCW_SENSITIVITY ->
            listOf("столкновение", "fcw", "чувствительность")
        MbCanKnownVehiclePropertyId.ACC_TIME_GAP_SET ->
            listOf("круиз", "acc", "дистанция", "время")
        MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL ->
            listOf("ldw", "полоса", "чувствительность")
        MbCanKnownVehiclePropertyId.HMA_SWITCH ->
            listOf("дальний", "свет", "авто", "hma")
        MbCanKnownVehiclePropertyId.HVAC_CUSTOM ->
            listOf("климат", "печка", "кондиционер", "режим", "eco")
        MbCanKnownVehiclePropertyId.HVAC_AC_MAX ->
            listOf("климат", "кондиционер", "охлаждение", "ac")
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH ->
            listOf("обогрев", "лобовое", "стекло", "печка")
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH ->
            listOf("обогрев", "заднее", "стекло", "зеркала")
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION ->
            listOf("климат", "рециркуляция", "воздух")
        MbCanKnownVehiclePropertyId.HVAC_POWER -> listOf("климат", "кондиционер", "ac")
        MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY ->
            listOf("климат", "кондиционер", "очистка", "сушка")
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE -> listOf("климат", "авто", "печка")
        MbCanKnownVehiclePropertyId.HVAC_AQS ->
            listOf("воздух", "анионы", "очистка", "ионизация")
        MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH ->
            listOf("аромат", "парфюм", "запах")
        MbCanKnownVehiclePropertyId.FRAGRANCE_SMELL -> listOf("аромат", "запах")
        MbCanKnownVehiclePropertyId.FRAGRANCE_CONCENTRATION ->
            listOf("аромат", "интенсивность", "концентрация")
        MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH ->
            listOf("климат", "продувка", "первые минуты")
        MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED ->
            listOf("вентилятор", "bluetooth", "звонок", "шум")
        MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH ->
            listOf("климат", "вентиляция", "авто", "проветривание")
        MbCanKnownVehiclePropertyId.HUD_SWITCH -> listOf("hud", "проектор", "проекция")
        MbCanKnownVehiclePropertyId.HUD_HEIGHT -> listOf("hud", "высота", "проекция")
        MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS -> listOf("hud", "яркость", "проекция")
        MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE -> listOf("hud", "режим", "снег", "проекция")
        MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS -> listOf("hud", "яркость", "авто")
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE ->
            listOf("приборная", "панель", "яркость", "авто")
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL ->
            listOf("приборная", "панель", "яркость")
        MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET ->
            listOf("скорость", "превышение", "предупреждение", "лимит")
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION ->
            listOf("климат", "обдув", "направление", "печка", "лобовое", "ноги")
        MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH ->
            listOf("зарядка", "беспроводная", "телефон", "qi")
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_STEERING_MODE ->
            listOf("руль", "режим", "усилитель")
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE ->
            listOf("руль", "усилитель", "eps", "режим")
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE ->
            listOf("движение", "режим", "eco", "sport", "спорт", "норм")
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET ->
            listOf("движение", "режим", "кпп", "6dct")
        MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH ->
            listOf("знаки", "распознавание", "sla", "ограничения")
        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH ->
            listOf("сиденье", "подогрев", "вентиляция", "левое", "переднее")
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH ->
            listOf("сиденье", "подогрев", "вентиляция", "правое", "переднее")
        MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH ->
            listOf("сиденье", "подогрев", "левое", "заднее")
        MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH ->
            listOf("сиденье", "подогрев", "правое", "заднее")
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT ->
            listOf("климат", "температура", "печка", "левая")
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT ->
            listOf("климат", "температура", "печка", "правая")
        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED ->
            listOf("климат", "вентилятор", "скорость", "обдув")
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF -> listOf("климат", "передний", "выключить")
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH ->
            listOf("климат", "синхронизация", "зоны")
        MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL ->
            listOf("багажник", "крышка", "пятая дверь", "открыть", "закрыть")
        MbCanKnownVehiclePropertyId.SUNSHADE_POS ->
            listOf("шторка", "панорама", "тюнель", "крыша")
        MbCanKnownVehiclePropertyId.SUNROOF_CONTROL ->
            listOf("люк", "панорама", "крыша", "откинуть")
        MbCanKnownVehiclePropertyId.WINDOW_POS ->
            listOf("стёкла", "окна", "все", "открыть", "закрыть", "щель")
        MbCanKnownVehiclePropertyId.WINDOW_FL_POS ->
            listOf("стекло", "окно", "левое", "переднее", "открыть", "закрыть")
        MbCanKnownVehiclePropertyId.WINDOW_FR_POS ->
            listOf("стекло", "окно", "правое", "переднее", "открыть", "закрыть")
        MbCanKnownVehiclePropertyId.WINDOW_RL_POS ->
            listOf("стекло", "окно", "левое", "заднее", "открыть", "закрыть")
        MbCanKnownVehiclePropertyId.WINDOW_RR_POS ->
            listOf("стекло", "окно", "правое", "заднее", "открыть", "закрыть")
        else -> emptyList()
    }
}

/** Picker group of a builtin action; exhaustive `when` — new types must declare their group. */
fun builtinActionGroup(type: AutomationBuiltinActionType): AutomationActionGroup = when (type) {
    AutomationBuiltinActionType.OPEN_MENU,
    AutomationBuiltinActionType.FINISH_AND_START_TRIP,
    AutomationBuiltinActionType.RESET_MOTOR_HOURS,
    AutomationBuiltinActionType.RESTART_TBOX,
    AutomationBuiltinActionType.CYCLE_MOCK_LOCATION_MODE,
    AutomationBuiltinActionType.GNSS_MODULE_REBOOT,
    AutomationBuiltinActionType.SET_SIMULATED_LOCATION_SOURCE_LOSS,
    AutomationBuiltinActionType.SET_GEO_DEBUG_LOG,
    -> AutomationActionGroup.PROGRAM

    AutomationBuiltinActionType.TOGGLE_APP_DAY_NIGHT_THEME,
    AutomationBuiltinActionType.ENABLE_HEAD_UNIT_AUTO_THEME,
    AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME,
    AutomationBuiltinActionType.TOGGLE_MIRROR_ADJUST_MODE,
    AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS,
    AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS,
    -> AutomationActionGroup.SCREEN

    AutomationBuiltinActionType.MEDIA_PREVIOUS,
    AutomationBuiltinActionType.MEDIA_PLAY_PAUSE,
    AutomationBuiltinActionType.MEDIA_PLAY,
    AutomationBuiltinActionType.MEDIA_NEXT,
    AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE,
    AutomationBuiltinActionType.SET_MEDIA_VOLUME,
    AutomationBuiltinActionType.SET_NAVI_VOLUME,
    AutomationBuiltinActionType.SET_VOICE_VOLUME,
    AutomationBuiltinActionType.SET_HEADREST_SPEAKER,
    -> AutomationActionGroup.MEDIA_SOUND

    AutomationBuiltinActionType.SET_PHONE_VOLUME,
    AutomationBuiltinActionType.PLACE_CALL,
    AutomationBuiltinActionType.ANSWER_CALL,
    AutomationBuiltinActionType.HANGUP_CALL,
    -> AutomationActionGroup.PHONE

    AutomationBuiltinActionType.WIFI_SET_ENABLED,
    AutomationBuiltinActionType.WIFI_CONNECT,
    AutomationBuiltinActionType.WIFI_DISCONNECT,
    AutomationBuiltinActionType.WIFI_MODEM_SET_DATA,
    AutomationBuiltinActionType.WIFI_MODEM_REBOOT,
    -> AutomationActionGroup.NETWORK

    AutomationBuiltinActionType.ADB_SET_TCP,
    AutomationBuiltinActionType.ADB_SHELL,
    AutomationBuiltinActionType.ADB_FORCE_STOP,
    -> AutomationActionGroup.NETWORK

    AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET,
    AutomationBuiltinActionType.SET_AUTOMATION_ENABLED,
    -> AutomationActionGroup.AUTOMATIONS

    AutomationBuiltinActionType.ESP_RELAY_SET,
    AutomationBuiltinActionType.ESP_RELAY_TOGGLE,
    AutomationBuiltinActionType.ESP_RELAY_PULSE,
    -> AutomationActionGroup.ESP

    AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET,
    AutomationBuiltinActionType.CRUISE_PAUSE,
    AutomationBuiltinActionType.CRUISE_FULL_OFF,
    AutomationBuiltinActionType.CRUISE_RESUME,
    AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED,
    AutomationBuiltinActionType.CRUISE_NUDGE,
    -> AutomationActionGroup.CAN_ADAS

    AutomationBuiltinActionType.START_VAD_VOICE,
    -> AutomationActionGroup.PROGRAM

    AutomationBuiltinActionType.CAMD_WINDOW,
    AutomationBuiltinActionType.CAMD_KEEP_AVM,
    AutomationBuiltinActionType.CAMD_RECORDER,
    -> AutomationActionGroup.CAMERAS

    AutomationBuiltinActionType.DCL_SET_SMART_ACC,
    AutomationBuiltinActionType.DCL_TOGGLE_SMART_ACC,
    -> AutomationActionGroup.DCL

    AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS,
    AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED,
    -> AutomationActionGroup.SCREEN

    AutomationBuiltinActionType.SHOW_TOAST,
    AutomationBuiltinActionType.SHOW_ALERT,
    -> AutomationActionGroup.SCREEN
}

/** Picker group of a CAN catalog entry. */
fun canActionGroup(bus: AutomationCanBus, propertyId: Int): AutomationActionGroup = when (bus) {
    AutomationCanBus.AUDIO -> AutomationActionGroup.CAN_AUDIO
    AutomationCanBus.VEHICLE -> when (propertyId) {
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
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
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION,
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT,
        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED,
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF,
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH,
        -> AutomationActionGroup.CAN_CLIMATE

        MbCanKnownVehiclePropertyId.LIGHTCONTROL,
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT,
        MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK,
        MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK,
        MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY,
        MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE,
        MbCanKnownVehiclePropertyId.DEFENCES_PROMPT,
        MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW,
        MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH,
        MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST,
        MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT,
        MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH,
        -> AutomationActionGroup.CAN_LIGHTS_LOCKS

        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
        MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH,
        -> AutomationActionGroup.CAN_SEATS

        MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
        MbCanKnownVehiclePropertyId.SUNSHADE_POS,
        MbCanKnownVehiclePropertyId.SUNROOF_CONTROL,
        MbCanKnownVehiclePropertyId.WINDOW_POS,
        MbCanKnownVehiclePropertyId.WINDOW_FL_POS,
        MbCanKnownVehiclePropertyId.WINDOW_FR_POS,
        MbCanKnownVehiclePropertyId.WINDOW_RL_POS,
        MbCanKnownVehiclePropertyId.WINDOW_RR_POS,
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
        MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY,
        MbCanKnownVehiclePropertyId.REAR_WIPER,
        -> AutomationActionGroup.CAN_BODY

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
        MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH,
        -> AutomationActionGroup.CAN_ADAS

        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
        MbCanKnownVehiclePropertyId.AVH_SWITCH,
        MbCanKnownVehiclePropertyId.HDC_SWITCH,
        MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH,
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_STEERING_MODE,
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE,
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET,
        -> AutomationActionGroup.CAN_ADAS

        MbCanKnownVehiclePropertyId.HUD_SWITCH,
        MbCanKnownVehiclePropertyId.HUD_HEIGHT,
        MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS,
        MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE,
        MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS,
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE,
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL,
        MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET,
        -> AutomationActionGroup.CAN_PANELS

        else -> AutomationActionGroup.CAN_BODY
    }
}
