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
        AutomationSignalId.ROAD_HIGHWAY_CLASS to listOf(
            "класс дороги",
            "тип дороги",
            "какая дорога",
        ),
        AutomationSignalId.ROAD_SPEED_LIMIT_KMH to listOf(
            "лимит по карте",
            "ограничение скорости по карте",
            "осм лимит",
        ),
        AutomationSignalId.ROAD_TUNNEL to listOf(
            "тоннель",
            "в тоннеле",
            "туннель",
        ),
        AutomationSignalId.ROAD_SETTLEMENT to listOf(
            "населённый пункт",
            "в населённом пункте",
            "населенный пункт",
            "въезд в населённый пункт",
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
            "какой расход",
            "мгновенный расход",
        ),
        AutomationSignalId.ODOMETER_KM to listOf(
            "пробег",
            "одометр",
            "общий пробег",
        ),
        AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM to listOf(
            "до то",
            "до обслуживания",
            "осталось до то",
            "пробег до то",
        ),
        AutomationSignalId.VOLTAGE to listOf(
            "напряжение",
            "аккумулятор",
            "заряд акб",
            "напряжение бортовой сети",
        ),
        AutomationSignalId.GEAR_MODE to listOf(
            "передача",
            "селектор",
            "prnd",
            "какая передача",
        ),
        AutomationSignalId.CRUISE_SET_SPEED to listOf(
            "уставка круиза",
            "скорость круиза",
            "круиз скорость",
        ),
        AutomationSignalId.ACC_STATUS to listOf(
            "статус acc",
            "состояние acc",
            "адаптивный круиз",
        ),
        AutomationSignalId.ACC_CRUISE_STATE to listOf(
            "состояние круиза acc",
            "круиз acc",
        ),
        AutomationSignalId.CCS_CRUISE_STATE to listOf(
            "состояние круиза ccs",
            "обычный круиз",
        ),
        AutomationSignalId.DRIVE_MODE to listOf(
            "режим езды",
            "драйв мод",
            "какой режим",
        ),
        AutomationSignalId.HIGH_BEAM to listOf(
            "дальний свет",
            "дальний",
        ),
        AutomationSignalId.HEADLIGHT_MODE to listOf(
            "режим фар",
            "свет фар",
            "фары",
        ),
        AutomationSignalId.STEERING_WHEEL_HEAT to listOf(
            "подогрев руля",
            "обогрев руля",
            "руль греется",
        ),
        AutomationSignalId.FRONT_LEFT_SEAT_MODE to listOf(
            "сиденье водителя",
            "подогрев водителя",
            "вентиляция водителя",
        ),
        AutomationSignalId.FRONT_RIGHT_SEAT_MODE to listOf(
            "сиденье пассажира",
            "подогрев пассажира",
            "вентиляция пассажира",
        ),
        AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE to listOf(
            "давление левое переднее",
            "давление переднее левое",
        ),
        AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE to listOf(
            "давление правое переднее",
            "давление переднее правое",
        ),
        AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE to listOf(
            "давление левое заднее",
            "давление заднее левое",
        ),
        AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE to listOf(
            "давление правое заднее",
            "давление заднее правое",
        ),
        AutomationSignalId.DOOR_FRONT_LEFT to listOf(
            "дверь водителя",
            "передняя левая дверь",
        ),
        AutomationSignalId.DOOR_FRONT_RIGHT to listOf(
            "дверь пассажира",
            "передняя правая дверь",
        ),
        AutomationSignalId.DOOR_REAR_LEFT to listOf(
            "задняя левая дверь",
        ),
        AutomationSignalId.DOOR_REAR_RIGHT to listOf(
            "задняя правая дверь",
        ),
        AutomationSignalId.TRUNK_DOOR to listOf(
            "багажник открыт",
            "дверь багажника",
            "состояние багажника",
        ),
        AutomationSignalId.SUNROOF to listOf(
            "люк",
            "состояние люка",
            "люк открыт",
        ),
        AutomationSignalId.WINDOW_FRONT_LEFT to listOf(
            "окно водителя",
            "стекло водителя",
        ),
        AutomationSignalId.WINDOW_FRONT_RIGHT to listOf(
            "окно пассажира",
            "стекло пассажира",
        ),
        AutomationSignalId.WINDOW_REAR_LEFT to listOf(
            "заднее левое окно",
        ),
        AutomationSignalId.WINDOW_REAR_RIGHT to listOf(
            "заднее правое окно",
        ),
        AutomationSignalId.FRONT_WINDSCREEN_HEAT to listOf(
            "обогрев лобового",
            "подогрев лобового",
        ),
        AutomationSignalId.HVAC_REAR_DEFROSTER to listOf(
            "обогрев заднего стекла",
            "обогрев зеркал",
        ),
        AutomationSignalId.HVAC_AUTO to listOf(
            "авто климат",
            "климат авто",
            "режим авто климата",
        ),
        AutomationSignalId.HVAC_RECIRCULATION to listOf(
            "рециркуляция",
            "воздух из салона",
        ),
        AutomationSignalId.RAIN_DETECTED to listOf(
            "дождь",
            "идёт дождь",
            "датчик дождя",
        ),
        AutomationSignalId.PARKING_RADAR to listOf(
            "парктроник",
            "радар парковки",
        ),
        AutomationSignalId.WIFI_ENABLED to listOf(
            "вайфай",
            "wifi включен",
            "состояние wifi",
        ),
        AutomationSignalId.WIFI_SSID to listOf(
            "имя wifi",
            "ssid",
            "к какой сети подключены",
        ),
        AutomationSignalId.HU_MEDIA_VOLUME to listOf(
            "громкость медиа",
            "громкость музыки",
            "какая громкость",
        ),
        AutomationSignalId.HU_SCREEN_BRIGHTNESS to listOf(
            "яркость экрана",
            "какая яркость",
        ),
        AutomationSignalId.DCL_ADAS_PANEL_VISIBLE to listOf(
            "панель ADAS DCL",
            "ADAS DCL",
        ),
        AutomationSignalId.DCL_TOP_BAR_VISIBLE to listOf(
            "верхняя панель DCL",
            "верхний бар DCL",
        ),
        AutomationSignalId.DCL_BOTTOM_BAR_VISIBLE to listOf(
            "нижняя панель DCL",
            "нижний бар DCL",
        ),
        AutomationSignalId.DCL_NOTIFICATION_SHADE_EXPANDED to listOf(
            "шторка уведомлений DCL",
            "шторка DCL",
            "control center DCL",
        ),
        AutomationSignalId.DCL_SCREEN_MODE to listOf(
            "режим экрана DCL",
            "экран DCL",
        ),
        AutomationSignalId.DCL_SMART_ACC_ACTIVE to listOf(
            "умный acc",
            "умный круиз",
            "smart acc",
        ),
        AutomationSignalId.DCL_SMART_ACC_CONFIG_ENABLED to listOf(
            "acc по ограничению",
            "скорость acc по знакам",
        ),
        AutomationSignalId.DCL_SMART_ACC_CRUISE_STATE to listOf(
            "состояние круиза dcl",
            "статус smart acc",
        ),
        AutomationSignalId.DCL_EFFECTIVE_SPEED_LIMIT to listOf(
            "эффективный лимит",
            "лимит со знака",
            "ограничение скорости dcl",
        ),
        AutomationSignalId.DCL_SMART_ACC_SPEED_OFFSET to listOf(
            "оффсет acc",
            "смещение acc",
        ),
        AutomationSignalId.DCL_SMART_ACC_TARGET_SPEED to listOf(
            "целевая скорость acc",
            "уставка smart acc",
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
        AutomationSignalId.GEO_ALTITUDE_M to listOf(
            "высота",
            "высота gps",
            "над уровнем моря",
        ),
        AutomationSignalId.GEO_SPEED_KMH to listOf(
            "скорость gps",
            "гео скорость",
        ),
        AutomationSignalId.GEO_SPEED_SOURCE to listOf(
            "источник скорости",
            "откуда скорость",
        ),
        AutomationSignalId.GEO_BEARING_DEG to listOf(
            "курс",
            "азимут",
            "направление движения",
        ),
        AutomationSignalId.GEO_SATELLITES_VISIBLE to listOf(
            "спутники видимые",
            "сколько спутников видно",
        ),
        AutomationSignalId.GEO_SATELLITES_USED to listOf(
            "спутники в решении",
            "сколько спутников используется",
        ),
        AutomationSignalId.GEO_FIX_VALID to listOf(
            "правдивость gps",
            "валидный gps",
            "достоверный gps",
        ),
        AutomationSignalId.GEO_SOURCE to listOf(
            "источник геопозиции",
            "откуда gps",
        ),
        AutomationSignalId.MODEM_RSSI_DBM to listOf(
            "rssi",
            "сигнал в децибелах",
            "сила сигнала модема",
        ),
        AutomationSignalId.MODEM_REGISTERED to listOf(
            "регистрация в сети",
            "модем в сети",
            "есть ли сеть",
        ),
        AutomationSignalId.MODEM_OPERATOR to listOf(
            "оператор",
            "сотовый оператор",
            "какой оператор",
        ),
        AutomationSignalId.MODEM_SOURCE to listOf(
            "источник модема",
            "откуда модем",
        ),
        AutomationSignalId.MODEM_THROUGHPUT_DOWN_KBPS to listOf(
            "скорость загрузки модема",
            "download модема",
        ),
        AutomationSignalId.MODEM_THROUGHPUT_UP_KBPS to listOf(
            "скорость отдачи модема",
            "upload модема",
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
        AutomationSignalId.MEDIA_PLAYING to listOf(
            "играет ли музыка",
            "воспроизведение",
        ),
        AutomationSignalId.MEDIA_POSITION_MS to listOf(
            "позиция трека",
            "где в песне",
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
            "климат включен",
            "работает ли климат",
            "состояние климата",
        ),
        AutomationSignalId.HAZARD to listOf(
            "аварийная сигнализация",
            "аварийные огни",
            "аварийка",
        ),
        AutomationSignalId.TURN_SIGNAL_LEFT to listOf(
            "левый поворотник",
            "поворотник слева",
        ),
        AutomationSignalId.TURN_SIGNAL_RIGHT to listOf(
            "правый поворотник",
            "поворотник справа",
        ),
        AutomationSignalId.TURN_STABILIZED to listOf(
            "поворотник",
            "указатель поворота",
        ),
        AutomationSignalId.DRL to listOf(
            "дневные ходовые огни",
            "ходовые огни",
        ),
        AutomationSignalId.PHONE_HFP_CONNECTED to listOf(
            "телефон по блютуз",
            "hfp",
            "телефон подключён",
        ),
        AutomationSignalId.PHONE_CALL_STATE to listOf(
            "статус звонка",
            "состояние звонка",
            "идёт ли разговор",
        ),
        AutomationSignalId.PHONE_CALL_DIRECTION to listOf(
            "направление звонка",
            "входящий или исходящий",
        ),
        AutomationSignalId.PHONE_CALL_NUMBER to listOf(
            "номер звонка",
            "кто звонит",
            "набранный номер",
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
        AutomationBuiltinActionType.ESP_BLE_RELAY_SET to "BLE реле замкнуть или разомкнуть",
        AutomationBuiltinActionType.ESP_BLE_RELAY_TOGGLE to "BLE реле переключить",
        AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE to "BLE реле импульс",
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
        AutomationBuiltinActionType.START_VAD_VOICE to "Голосовой помощник",
        AutomationBuiltinActionType.PLACE_CALL to "Позвонить",
        AutomationBuiltinActionType.ANSWER_CALL to "Ответить на звонок",
        AutomationBuiltinActionType.HANGUP_CALL to "Сбросить звонок",
        AutomationBuiltinActionType.CAMD_WINDOW to "Окно камеры",
        AutomationBuiltinActionType.CAMD_KEEP_AVM to "Держать штатный AVM отключенным",
        AutomationBuiltinActionType.CAMD_RECORDER to "Регистратор камер ГУ",
        AutomationBuiltinActionType.DCL_SET_SMART_ACC to "Умный ACC DCL",
        AutomationBuiltinActionType.DCL_TOGGLE_SMART_ACC to "Переключить Умный ACC DCL",
    )

    private val BUILTIN_EXTRAS: Map<AutomationBuiltinActionType, List<String>> = mapOf(
        AutomationBuiltinActionType.OPEN_MENU to listOf("открой меню", "меню тбокс"),
        AutomationBuiltinActionType.MEDIA_PLAY to listOf("играй", "плей", "продолжи", "включи музыку"),
        AutomationBuiltinActionType.MEDIA_PLAY_PAUSE to listOf("пауза", "стоп", "плей пауза", "останови музыку"),
        AutomationBuiltinActionType.MEDIA_NEXT to listOf("следующий", "дальше", "некст", "следующий трек"),
        AutomationBuiltinActionType.MEDIA_PREVIOUS to listOf("предыдущий", "назад", "предыдущий трек"),
        AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE to listOf("лайк", "лайкни", "мне нравится"),
        // camd_* / place_call — ещё без слотов; volume/brightness/wifi/cruise — хвосты F.
        AutomationBuiltinActionType.SET_MEDIA_VOLUME to listOf(
            "тише",
            "громче",
            "потише",
            "погромче",
            "громкость",
        ),
        AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS to listOf(
            "ярче",
            "темнее",
            "посветлее",
            "потемнее",
            "яркость",
            "яркость экрана",
        ),
        AutomationBuiltinActionType.WIFI_SET_ENABLED to listOf(
            "вайфай",
            "wifi",
            "включи вайфай",
            "выключи вайфай",
            "включи wifi",
            "выключи wifi",
        ),
        AutomationBuiltinActionType.CRUISE_NUDGE to listOf(
            "круиз плюс",
            "круиз минус",
            "плюс круиз",
            "минус круиз",
            "быстрее круиз",
            "медленнее круиз",
            "увеличь круиз",
            "убавь круиз",
        ),
        AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET to listOf(
            "уставка круиза",
            "круиз на",
        ),
        AutomationBuiltinActionType.CRUISE_PAUSE to listOf("пауза круиза", "приостанови круиз"),
        AutomationBuiltinActionType.CRUISE_RESUME to listOf("возобнови круиз", "продолжи круиз"),
        AutomationBuiltinActionType.CRUISE_FULL_OFF to listOf("выключи круиз", "отключи круиз"),
        AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED to listOf(
            "включи круиз",
            "круиз на текущей",
            "круиз по скорости",
        ),
        AutomationBuiltinActionType.SHOW_TOAST to listOf("тост", "уведомление"),
        AutomationBuiltinActionType.SHOW_ALERT to listOf("алерт", "сообщение на экран"),
        AutomationBuiltinActionType.RESTART_TBOX to listOf("рестарт тбокс", "перезагрузка тбокс"),
        AutomationBuiltinActionType.FINISH_AND_START_TRIP to listOf("новая поездка", "начать поездку"),
        AutomationBuiltinActionType.TOGGLE_APP_DAY_NIGHT_THEME to listOf(
            "переключить тему",
            "смени тему",
            "тема приложения",
        ),
        AutomationBuiltinActionType.DCL_SET_SMART_ACC to listOf(
            "умный acc",
            "smart acc",
            "acc по знакам",
        ),
        AutomationBuiltinActionType.DCL_TOGGLE_SMART_ACC to listOf(
            "переключить умный acc",
            "toggle smart acc",
        ),
        AutomationBuiltinActionType.START_VAD_VOICE to listOf(
            "слушай",
            "вад войс",
            "голосовой помощник",
            "включи голос",
        ),
        AutomationBuiltinActionType.ANSWER_CALL to listOf(
            "ответь",
            "принять звонок",
            "ответить",
        ),
        AutomationBuiltinActionType.HANGUP_CALL to listOf(
            "сбрось",
            "положить трубку",
            "завершить звонок",
            "сбросить",
        ),
        // place_call нужен номер — без слотов не матчим разговорными фразами.
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
        "open_main_screen" to listOf("открой экран", "главный экран", "открой вкладку", "открой монитор"),
        "http_request" to listOf("http запрос", "веб запрос"),
        // Без «пауза» — конфликтует с media_play_pause.
        "delay" to listOf("задержка", "подожди секунд"),
    )
}
