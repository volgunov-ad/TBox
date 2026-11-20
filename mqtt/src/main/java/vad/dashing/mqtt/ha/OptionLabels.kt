package vad.dashing.mqtt.ha


/**
 * Alice-visible labels for SELECT option keys / pulse states.
 *
 * Kept in sync with Monitor [vad.dashing.tbox.automation.AutomationSignalStateEncoding.stateOptionLabel]
 * (mqtt APK cannot depend on `:app`). Payload on the set topic stays the raw key.
 */
object OptionLabels {

    /**
     * Display name for a writable option button.
     * Prefers a human [WritePair.textValue] when the API already sent Russian text;
     * otherwise maps known keys; unknown snake_case is spaced for speech.
     */
    fun actionLabel(state: String, pair: WritePair? = null): String {
        val humanFromPair = pair?.textValue?.trim().orEmpty()
        if (looksHumanLabel(humanFromPair) && !isCommandKey(humanFromPair)) {
            return humanFromPair
        }
        val key = state.trim()
        if (key.isEmpty()) return state
        // Imperatives for window/cover commands (better for «Алиса, …» than state adjectives).
        when (key.lowercase()) {
            "opened", "open" -> return "открыть"
            "closed", "close" -> return "закрыть"
            "vent" -> return "щель"
            "comfort_open" -> return "комфортное открытие"
        }
        return stateOptionLabel(key)
    }

    /** Mirror of AutomationSignalStateEncoding.stateOptionLabel (RU Monitor wording). */
    fun stateOptionLabel(raw: String): String = when (raw.trim().lowercase()) {
        "on" -> "Включено"
        "off" -> "Выключено"
        "standby" -> "Ожидание"
        "active" -> "Активен"
        "override" -> "Перебивка газом"
        "fault" -> "Ошибка"
        "none" -> "Нет сети"
        "unknown" -> "Неизвестно"
        "checking" -> "Проверка"
        "online" -> "Есть"
        "offline" -> "Нет"
        "idle" -> "Нет опроса"
        "ok" -> "Связь OK"
        "auth_failed" -> "Ошибка входа"
        "unreachable" -> "Недоступен"
        "error" -> "Ошибка"
        "2g" -> "2G"
        "3g" -> "3G"
        "4g" -> "4G"
        "ready" -> "Готова"
        "pin" -> "Нужен PIN"
        "acc" -> "ACC ON"
        "ign" -> "ON"
        "crank" -> "Стартер"
        "hold_15m" -> "15 минут работы ГУ"
        "sleep" -> "Сон"
        "int" -> "INT"
        "low" -> "Low"
        "high" -> "High"
        "closed" -> "Закрыто"
        "open" -> "Открыто"
        "opened" -> "Открыто"
        "tilt" -> "Откинут"
        "vent" -> "Щель"
        "comfort_open" -> "Комфортное открытие"
        "only" -> "Только подголовник"
        "assist" -> "Подголовник + основные"
        "heat_1" -> "Подогрев 1"
        "heat_2" -> "Подогрев 2"
        "heat_3" -> "Подогрев 3"
        "vent_1" -> "Вентиляция 1"
        "vent_2" -> "Вентиляция 2"
        "vent_3" -> "Вентиляция 3"
        "30s" -> "30 с"
        "60s" -> "60 с"
        "driver" -> "Водитель"
        "all" -> "Все"
        "light" -> "Свет"
        "horn" -> "Сигнал"
        "light_horn" -> "Свет и сигнал"
        "ldw" -> "LDW"
        "lka" -> "LKA"
        "far" -> "Дальняя"
        "standard" -> "Стандарт"
        "near" -> "Ближняя"
        "eco" -> "ECO"
        "comfort" -> "Комфорт"
        "strong" -> "Сильный"
        "meteor" -> "Meteor"
        "boss" -> "Boss"
        "tea" -> "Tea"
        "medium" -> "Средняя"
        "face" -> "Лицо"
        "foot" -> "Ноги"
        "face_foot" -> "Лицо и ноги"
        "defrost" -> "Лобовое"
        "defrost_foot" -> "Лобовое и ноги"
        "snow" -> "Снег"
        "day" -> "День"
        "night" -> "Ночь"
        "manual_day" -> "День (вручную)"
        "manual_night" -> "Ночь (вручную)"
        "auto_day" -> "День (авто)"
        "auto_night" -> "Ночь (авто)"
        "auto" -> "Авто"
        "manual" -> "Вручную"
        "sport" -> "Спорт"
        "opening" -> "Открывается"
        "closing" -> "Закрывается"
        "pop" -> "Pop"
        "rock" -> "Rock"
        "jazz" -> "Jazz"
        "classic" -> "Classic"
        "voice" -> "Voice"
        "custom" -> "Custom"
        "nor" -> "NOR"
        "spt" -> "SPT"
        "sand" -> "SAND"
        "mud" -> "MUD"
        "snow_mode" -> "SNOW"
        "gnss" -> "GNSS"
        "can" -> "CAN"
        "retention" -> "Удержание"
        "tbox" -> "TBox"
        "companion" -> "Компаньон"
        "android" -> "Android"
        "usb" -> "USB"
        "phone" -> "Телефон"
        "wifi_http" -> "Wi‑Fi HTTP"
        else -> humanizeUnknown(raw)
    }

    /** Cyrillic / spaced text from API — not a raw invoke key. */
    internal fun looksHumanLabel(value: String): Boolean {
        if (value.isBlank()) return false
        if (value.any { it in '\u0400'..'\u04FF' }) return true
        return value.contains(' ') && value.length > 2
    }

    private fun isCommandKey(value: String): Boolean =
        value.lowercase() in setOf(
            "open", "close", "opened", "closed", "vent", "comfort_open",
            "on", "off", "press",
        )

    private fun humanizeUnknown(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw
        // Short ALLCAPS codes (ECO, NOR, SPT) stay as-is for speech.
        if (trimmed.length <= 6 && trimmed.all { it.isLetterOrDigit() } &&
            trimmed.any { it.isLetter() } && trimmed == trimmed.uppercase()
        ) {
            return trimmed
        }
        if ('_' in trimmed) {
            return trimmed.replace('_', ' ').trim()
        }
        return trimmed
    }
}
