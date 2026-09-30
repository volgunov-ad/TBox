package vad.dashing.tbox.externalapi

import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.AutomationCanBus
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

/**
 * Russian voice phrases for External API `/v1/catalog` ([voiceAliasesRu]).
 *
 * Does not change [storageKey]; NLU clients match spoken text against these aliases,
 * then call signals / invoke / automations run with catalog ids.
 */
object ExternalApiVoiceAliasesRu {
    fun forSignal(id: AutomationSignalId, label: String): List<String> =
        merge(listOf(label) + (SIGNAL_EXTRAS[id] ?: emptyList()))

    fun forBuiltin(type: AutomationBuiltinActionType): List<String> {
        val label = BUILTIN_LABELS[type] ?: type.storageKey.replace('_', ' ')
        return merge(listOf(label) + (BUILTIN_EXTRAS[type] ?: emptyList()))
    }

    fun forCanCommand(bus: AutomationCanBus, propertyId: Int, label: String): List<String> {
        val extras = when (bus) {
            AutomationCanBus.VEHICLE -> VEHICLE_CAN_EXTRAS[propertyId]
            AutomationCanBus.AUDIO -> AUDIO_CAN_EXTRAS[propertyId]
        }.orEmpty()
        return merge(listOf(label) + extras)
    }

    fun forGenericAction(type: String): List<String> =
        merge(GENERIC_ACTION_ALIASES[type].orEmpty())

    private fun merge(raw: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (item in raw) {
            val normalized = normalize(item) ?: continue
            seen.add(normalized)
        }
        return seen.toList()
    }

    private fun normalize(raw: String): String? {
        val trimmed = raw.trim().lowercase().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return null
        // Drop trailing parenthetical abbreviations for a shorter spoken form.
        val withoutParen = trimmed
            .replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return withoutParen.ifEmpty { trimmed }
    }

    private val SIGNAL_EXTRAS: Map<AutomationSignalId, List<String>> = mapOf(
        AutomationSignalId.OUTSIDE_TEMPERATURE to listOf(
            "температура на улице",
            "сколько градусов на улице",
            "на улице",
            "за бортом",
        ),
        AutomationSignalId.INSIDE_TEMPERATURE to listOf(
            "температура в салоне",
            "сколько градусов в салоне",
            "в салоне",
        ),
        AutomationSignalId.ENGINE_TEMPERATURE to listOf(
            "температура двигателя",
            "температура мотора",
            "антифриз",
        ),
        AutomationSignalId.CAR_SPEED to listOf(
            "скорость",
            "какая скорость",
            "сколько едем",
        ),
        AutomationSignalId.ENGINE_RPM to listOf(
            "обороты",
            "обороты мотора",
        ),
        AutomationSignalId.FUEL_LEVEL_PERCENT to listOf(
            "уровень топлива",
            "сколько топлива",
            "сколько бензина",
            "бак",
        ),
        AutomationSignalId.FUEL_LEVEL_PERCENT_FILTERED to listOf(
            "фильтрованный уровень топлива",
            "топливо фильтр",
        ),
        AutomationSignalId.FUEL_LEVEL_LITERS to listOf(
            "литры топлива",
            "сколько литров",
        ),
        AutomationSignalId.DISTANCE_TO_EMPTY_KM to listOf(
            "запас хода",
            "сколько ещё проеду",
            "до пустого бака",
        ),
        AutomationSignalId.CURRENT_FUEL_CONSUMPTION to listOf(
            "расход",
            "текущий расход",
        ),
        AutomationSignalId.ODOMETER_KM to listOf(
            "пробег",
            "одометр",
        ),
        AutomationSignalId.VOLTAGE to listOf(
            "напряжение",
            "аккумулятор",
            "заряд акб",
        ),
        AutomationSignalId.GEAR_MODE to listOf(
            "передача",
            "селектор",
            "prnd",
        ),
        AutomationSignalId.TBOX_CONNECTED to listOf(
            "тбокс на связи",
            "тбокс подключен",
            "связь с тбокс",
        ),
        AutomationSignalId.MODEM_SIGNAL_LEVEL to listOf(
            "уровень сигнала",
            "сигнал модема",
            "сеть",
        ),
        AutomationSignalId.LOCATE_STATUS to listOf(
            "gps",
            "геолокация",
            "есть ли gps",
        ),
        AutomationSignalId.GEAR_BOX_OIL_TEMPERATURE to listOf(
            "температура масла кпп",
            "масло коробки",
        ),
        AutomationSignalId.ACTIVE_TRIP_DISTANCE_KM to listOf(
            "пробег поездки",
            "дистанция поездки",
        ),
        AutomationSignalId.ACTIVE_TRIP_AVG_FUEL_L100KM to listOf(
            "средний расход поездки",
            "расход за поездку",
        ),
        AutomationSignalId.ACTIVE_TRIP_DURATION_S to listOf(
            "длительность поездки",
            "сколько едем по времени",
        ),
        AutomationSignalId.MEDIA_TITLE to listOf(
            "что играет",
            "название трека",
            "песня",
        ),
        AutomationSignalId.MEDIA_ARTIST to listOf(
            "исполнитель",
            "артист",
            "кто играет",
        ),
        AutomationSignalId.HVAC_TEMPERATURE_LEFT to listOf(
            "температура климата",
            "климат слева",
        ),
        AutomationSignalId.HVAC_TEMPERATURE_RIGHT to listOf(
            "климат справа",
        ),
        AutomationSignalId.HVAC_FAN_SPEED to listOf(
            "вентилятор",
            "скорость обдува",
        ),
        AutomationSignalId.HVAC_POWER to listOf(
            "кондиционер",
            "климат",
        ),
    )

    private val BUILTIN_LABELS: Map<AutomationBuiltinActionType, String> = mapOf(
        AutomationBuiltinActionType.OPEN_MENU to "Открыть меню",
        AutomationBuiltinActionType.FINISH_AND_START_TRIP to "Завершить и начать поездку",
        AutomationBuiltinActionType.RESET_MOTOR_HOURS to "Сбросить моточасы",
        AutomationBuiltinActionType.RESTART_TBOX to "Перезапустить TBox",
        AutomationBuiltinActionType.TOGGLE_APP_DAY_NIGHT_THEME to "Переключить тему приложения",
        AutomationBuiltinActionType.ENABLE_HEAD_UNIT_AUTO_THEME to "Автотема ГУ",
        AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME to "Тема ГУ день/ночь",
        AutomationBuiltinActionType.TOGGLE_MIRROR_ADJUST_MODE to "Режим настройки зеркал",
        AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS to "Скрыть плавающие панели",
        AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED to "Плавающие панели",
        AutomationBuiltinActionType.ESP_RELAY_SET to "Реле ESP установить",
        AutomationBuiltinActionType.ESP_RELAY_TOGGLE to "Реле ESP переключить",
        AutomationBuiltinActionType.ESP_RELAY_PULSE to "Реле ESP импульс",
        AutomationBuiltinActionType.MEDIA_PREVIOUS to "Предыдущий трек",
        AutomationBuiltinActionType.MEDIA_PLAY_PAUSE to "Пауза или воспроизведение",
        AutomationBuiltinActionType.MEDIA_PLAY to "Воспроизведение",
        AutomationBuiltinActionType.MEDIA_NEXT to "Следующий трек",
        AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE to "Лайк трека",
        AutomationBuiltinActionType.SET_MEDIA_VOLUME to "Громкость медиа",
        AutomationBuiltinActionType.SET_PHONE_VOLUME to "Громкость телефона",
        AutomationBuiltinActionType.SET_NAVI_VOLUME to "Громкость навигации",
        AutomationBuiltinActionType.SET_VOICE_VOLUME to "Громкость голоса",
        AutomationBuiltinActionType.SET_HEADREST_SPEAKER to "Динамики подголовников",
        AutomationBuiltinActionType.CYCLE_MOCK_LOCATION_MODE to "Режим mock location",
        AutomationBuiltinActionType.GNSS_MODULE_REBOOT to "Перезапуск GNSS",
        AutomationBuiltinActionType.SET_SIMULATED_LOCATION_SOURCE_LOSS to "Симуляция потери GPS",
        AutomationBuiltinActionType.SET_GEO_DEBUG_LOG to "Гео-отладка",
        AutomationBuiltinActionType.WIFI_SET_ENABLED to "Wi‑Fi вкл/выкл",
        AutomationBuiltinActionType.WIFI_CONNECT to "Подключить Wi‑Fi",
        AutomationBuiltinActionType.WIFI_DISCONNECT to "Отключить Wi‑Fi",
        AutomationBuiltinActionType.WIFI_MODEM_SET_DATA to "Модемные данные",
        AutomationBuiltinActionType.WIFI_MODEM_REBOOT to "Перезапуск модема",
        AutomationBuiltinActionType.ADB_SET_TCP to "ADB TCP",
        AutomationBuiltinActionType.ADB_SHELL to "ADB shell",
        AutomationBuiltinActionType.ADB_FORCE_STOP to "ADB force-stop",
        AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS to "Яркость экрана",
        AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS to "Автояркость экрана",
        AutomationBuiltinActionType.SHOW_TOAST to "Показать уведомление",
        AutomationBuiltinActionType.SHOW_ALERT to "Показать сообщение",
        AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET to "Триггер-виджет автоматизации",
        AutomationBuiltinActionType.SET_AUTOMATION_ENABLED to "Включить или выключить автоматизацию",
        AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET to "Круиз до цели",
        AutomationBuiltinActionType.CRUISE_PAUSE to "Пауза круиза",
        AutomationBuiltinActionType.CRUISE_FULL_OFF to "Выключить круиз",
        AutomationBuiltinActionType.CRUISE_RESUME to "Возобновить круиз",
        AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED to "Круиз на текущей скорости",
        AutomationBuiltinActionType.CRUISE_NUDGE to "Подстроить круиз",
    )

    private val BUILTIN_EXTRAS: Map<AutomationBuiltinActionType, List<String>> = mapOf(
        AutomationBuiltinActionType.MEDIA_PLAY to listOf("играй", "плей", "продолжи"),
        AutomationBuiltinActionType.MEDIA_PLAY_PAUSE to listOf("пауза", "стоп", "плей пауза"),
        AutomationBuiltinActionType.MEDIA_NEXT to listOf("следующий", "дальше", "некст"),
        AutomationBuiltinActionType.MEDIA_PREVIOUS to listOf("предыдущий", "назад"),
        AutomationBuiltinActionType.SET_MEDIA_VOLUME to listOf("громкость", "тише", "громче"),
        AutomationBuiltinActionType.SHOW_TOAST to listOf("тост", "уведомление"),
        AutomationBuiltinActionType.SHOW_ALERT to listOf("алерт", "сообщение на экран"),
        AutomationBuiltinActionType.RESTART_TBOX to listOf("рестарт тбокс", "перезагрузка тбокс"),
        AutomationBuiltinActionType.FINISH_AND_START_TRIP to listOf("новая поездка", "начать поездку"),
        AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS to listOf("яркость", "сделай ярче", "сделай темнее"),
        AutomationBuiltinActionType.WIFI_SET_ENABLED to listOf("вайфай", "wifi"),
    )

    private val VEHICLE_CAN_EXTRAS: Map<Int, List<String>> = mapOf(
        MbCanKnownVehiclePropertyId.HVAC_POWER to listOf(
            "кондиционер",
            "включи климат",
            "выключи климат",
            "климат-контроль",
        ),
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE to listOf("авто климат", "климат авто"),
        MbCanKnownVehiclePropertyId.HVAC_AC_MAX to listOf("макс охлаждение", "ac max"),
        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED to listOf(
            "вентилятор",
            "обдув",
            "скорость вентилятора",
        ),
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION to listOf(
            "направление обдува",
            "обдув на стекло",
            "обдув в ноги",
            "обдув в лицо",
        ),
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT to listOf(
            "температура климата",
            "потеплее",
            "похолоднее",
        ),
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT to listOf("температура справа"),
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH to listOf(
            "обогрев заднего стекла",
            "обогрев зеркал",
        ),
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH to listOf(
            "обогрев лобового",
            "подогрев лобового стекла",
        ),
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION to listOf(
            "рециркуляция",
            "воздух из салона",
        ),
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH to listOf(
            "подогрев руля",
            "обогрев руля",
        ),
        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH to listOf(
            "подогрев сиденья водителя",
            "сиденье водителя",
        ),
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH to listOf(
            "подогрев сиденья пассажира",
            "сиденье пассажира",
        ),
        MbCanKnownVehiclePropertyId.SUNROOF_CONTROL to listOf("люк", "открой люк", "закрой люк"),
        MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL to listOf(
            "багажник",
            "открой багажник",
            "закрой багажник",
        ),
        MbCanKnownVehiclePropertyId.WINDOW_POS to listOf("окна", "стёкла", "все окна"),
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE to listOf(
            "режим езды",
            "драйв мод",
            "спорт режим",
            "эко режим",
        ),
        MbCanKnownVehiclePropertyId.LIGHTCONTROL to listOf("фары", "свет", "режим фар"),
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT to listOf("задний туман", "противотуманки"),
    )

    private val AUDIO_CAN_EXTRAS: Map<Int, List<String>> = mapOf(
        MbCanKnownAudioPropertyId.EQ_MODE to listOf("эквалайзер", "режим эквалайзера"),
        MbCanKnownAudioPropertyId.BALANCE to listOf("баланс"),
        MbCanKnownAudioPropertyId.FADER to listOf("фейдер"),
        MbCanKnownAudioPropertyId.VOLUME_SPEED to listOf("громкость от скорости"),
    )

    private val GENERIC_ACTION_ALIASES: Map<String, List<String>> = mapOf(
        "launch_application" to listOf("запустить приложение", "открой приложение"),
        "open_main_screen" to listOf("открой экран", "главный экран", "открой вкладку"),
        "http_request" to listOf("http запрос", "веб запрос"),
        "delay" to listOf("пауза", "задержка", "подожди"),
    )
}
