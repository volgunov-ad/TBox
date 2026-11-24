package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

import vad.dashing.tbox.DRIVE_MODE_WIDGET_OPTIONS
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.CarSettingsAdasDomain
import vad.dashing.tbox.mbcan.CarSettingsAudioDomain
import vad.dashing.tbox.mbcan.CarSettingsHudDomain
import vad.dashing.tbox.mbcan.CarSettingsLocksLightsDomain
import vad.dashing.tbox.mbcan.AccTimeGap
import vad.dashing.tbox.mbcan.FcwSensitivity
import vad.dashing.tbox.mbcan.FollowMeHomeMode
import vad.dashing.tbox.mbcan.HvacBlowMode
import vad.dashing.tbox.mbcan.HvacClimateDomain
import vad.dashing.tbox.mbcan.HvacCustomMode
import vad.dashing.tbox.mbcan.LdwSensitivity
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.TrunkDoorDisplayState
import vad.dashing.tbox.mbcan.TrunkMovement

/** Stable automation state strings and migration from legacy numeric raw values. */
object AutomationSignalStateEncoding {
    val driveModeOptions: List<String> = DRIVE_MODE_WIDGET_OPTIONS
        .filter { it.propertyId == MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE }
        .map { it.widgetLabel }

    val driveMode6dctOptions: List<String> = DRIVE_MODE_WIDGET_OPTIONS
        .filter { it.propertyId == MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET }
        .map { it.widgetLabel }

    val headlightOptions: List<String> = HeadlightMode.entries.map { it.widgetLabel }

    val followMeHomeOptions = listOf("30s", "60s", "off")
    val driverUnlockOptions = listOf("driver", "all")
    val remoteLockFeedbackOptions = listOf("light", "horn", "light_horn")
    val lasModeOptions = listOf("ldw", "lka", "off")
    val fcwSensitivityOptions = listOf("far", "standard", "near")
    val accTimeGapOptions = listOf("near", "medium", "far")
    val ldwSensitivityOptions = listOf("high", "low")
    val hvacCustomOptions = listOf("eco", "comfort", "strong")
    val fragranceSmellOptions = listOf("meteor", "boss", "tea")
    val fragranceConcentrationOptions = listOf("low", "medium", "high")
    val hvacFanDirectionOptions = listOf("face", "foot", "face_foot", "defrost", "defrost_foot")
    val hudDisplayModeOptions = listOf("standard", "snow")
    val icmBrightnessModeOptions = listOf("auto", "manual")
    val steeringFeelOptions = listOf("nor", "eco", "spt")
    val trunkDoorOptions = listOf("closed", "open", "opening", "closing")
    /** Cabin door ajar (FL/FR/RL/RR): level states for triggers and conditions. */
    val doorAjarOptions = listOf("closed", "open")
    val audioVolumeSpeedOptions = listOf("off", "low", "medium", "high")
    val audioKeyToneOptions = listOf("off", "low", "medium", "high")
    val audioRadarVolumeOptions = listOf("low", "medium", "high")
    val audioEqModeOptions = listOf("pop", "rock", "jazz", "classic", "voice", "custom")

    fun driveModeFromRaw(raw: Int): String? =
        DRIVE_MODE_WIDGET_OPTIONS.firstOrNull {
            it.propertyId == MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE &&
                it.propertyValue == raw
        }?.widgetLabel

    fun driveMode6dctFromRaw(raw: Int): String? =
        DRIVE_MODE_WIDGET_OPTIONS.firstOrNull {
            it.propertyId == MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET &&
                it.propertyValue == raw
        }?.widgetLabel

    fun headlightFromRaw(raw: Int): String? = HeadlightMode.fromRaw(raw)?.widgetLabel

    private val windowSignals = setOf(
        AutomationSignalId.WINDOW_FRONT_LEFT,
        AutomationSignalId.WINDOW_FRONT_RIGHT,
        AutomationSignalId.WINDOW_REAR_LEFT,
        AutomationSignalId.WINDOW_REAR_RIGHT,
    )

    fun isWindowSignal(signal: AutomationSignalId): Boolean = signal in windowSignals

    /** STATE trigger/condition match. Windows: `open` holds for every open position. */
    fun stateMatches(signal: AutomationSignalId, actual: String, expected: String): Boolean =
        if (signal in windowSignals) {
            BodyComfortDomain.windowStateMatches(actual, expected)
        } else {
            actual.equals(expected.trim(), ignoreCase = true)
        }

    fun migrateLegacyStateValue(signal: AutomationSignalId, raw: String): String {
        val trimmed = raw.trim()
        trimmed.toIntOrNull()?.let { numeric ->
            legacyStateFromNumeric(signal, numeric)?.let { return it }
        }
        return trimmed
    }

    fun migrateLegacyNumericCondition(
        signal: AutomationSignalId,
        source: AutomationSignalSource,
        expectedValue: Double,
    ): AutomationCondition? {
        val migrated = legacyStateFromNumeric(signal, expectedValue) ?: return null
        return AutomationCondition.State(
            signal = signal,
            source = source,
            expectedState = migrated,
        )
    }

    fun migrateLegacyNumericTrigger(
        signal: AutomationSignalId,
        source: AutomationSignalSource,
        id: String,
        threshold: Double,
        holdMillis: Long,
        startupBehavior: AutomationStartupBehavior,
    ): AutomationTrigger.StateEquals? {
        val migrated = legacyStateFromNumeric(signal, threshold) ?: return null
        return AutomationTrigger.StateEquals(
            id = id,
            signal = signal,
            source = source,
            expectedState = migrated,
            holdMillis = holdMillis,
            startupBehavior = startupBehavior,
        )
    }

    fun followMeHomeFromRaw(raw: Int): String? = when (FollowMeHomeMode.fromMbCanRaw(raw)) {
        FollowMeHomeMode.Sec30 -> "30s"
        FollowMeHomeMode.Sec60 -> "60s"
        FollowMeHomeMode.Off -> "off"
        null -> FollowMeHomeMode.fromVhalRaw(raw)?.let { mode ->
            when (mode) {
                FollowMeHomeMode.Sec30 -> "30s"
                FollowMeHomeMode.Sec60 -> "60s"
                FollowMeHomeMode.Off -> "off"
            }
        }
    }

    fun driverUnlockFromRaw(raw: Int): String? = when (raw) {
        1 -> "driver"
        2 -> "all"
        else -> null
    }

    fun remoteLockFeedbackFromRaw(raw: Int): String? = when (raw) {
        CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_LIGHT -> "light"
        CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_HORN -> "horn"
        CarSettingsLocksLightsDomain.REMOTE_LOCK_FEEDBACK_LIGHT_HORN -> "light_horn"
        else -> null
    }

    fun lasModeFromRaw(raw: Int): String? = when (raw) {
        MbCanKnownVehiclePropertyId.LAS_MODE_LDW -> "ldw"
        MbCanKnownVehiclePropertyId.LAS_MODE_LKA -> "lka"
        MbCanKnownVehiclePropertyId.LAS_MODE_OFF -> "off"
        else -> null
    }

    fun fcwSensitivityFromRaw(raw: Int): String? = when (CarSettingsAdasDomain.decodeFcwSensitivityMbCan(raw)) {
        FcwSensitivity.Far -> "far"
        FcwSensitivity.Standard -> "standard"
        FcwSensitivity.Near -> "near"
        null -> null
    }

    fun accTimeGapStateKey(value: AccTimeGap): String = when (value) {
        AccTimeGap.Near -> "near"
        AccTimeGap.Medium -> "medium"
        AccTimeGap.Far -> "far"
    }

    fun accTimeGapFromRequestRaw(raw: Int): String? =
        CarSettingsAdasDomain.decodeAccTimeGapRequest(raw)?.let(::accTimeGapStateKey)

    fun ldwSensitivityFromRaw(raw: Int): String? = when (CarSettingsAdasDomain.decodeLdwSensitivityMbCan(raw)) {
        LdwSensitivity.High -> "high"
        LdwSensitivity.Low -> "low"
        null -> null
    }

    fun hvacCustomFromRaw(raw: Int): String? = when (HvacCustomMode.fromMbCanRaw(raw)) {
        HvacCustomMode.Eco -> "eco"
        HvacCustomMode.Comfort -> "comfort"
        HvacCustomMode.Strong -> "strong"
        null -> null
    }

    fun fragranceSmellFromRaw(raw: Int): String? = when (raw) {
        1 -> "meteor"
        2 -> "boss"
        3 -> "tea"
        else -> null
    }

    fun fragranceConcentrationFromRaw(raw: Int): String? = when (raw) {
        1 -> "low"
        2 -> "medium"
        3 -> "high"
        else -> null
    }

    fun hvacFanDirectionFromRaw(raw: Int): String? = when (HvacBlowMode.fromMbCanRaw(raw)) {
        HvacBlowMode.Face -> "face"
        HvacBlowMode.Foot -> "foot"
        HvacBlowMode.FaceFoot -> "face_foot"
        HvacBlowMode.Defrost -> "defrost"
        HvacBlowMode.DefrostFoot -> "defrost_foot"
        null -> null
    }

    fun hudDisplayModeFromRaw(raw: Int): String? = when (raw) {
        CarSettingsHudDomain.HUD_MODE_STANDARD -> "standard"
        CarSettingsHudDomain.HUD_MODE_SNOW -> "snow"
        else -> null
    }

    fun icmBrightnessModeFromRaw(raw: Int): String? = when (raw) {
        0 -> "auto"
        1 -> "manual"
        else -> null
    }

    fun steeringFeelFromRaw(raw: Int): String? = when (raw) {
        1 -> "nor"
        2 -> "eco"
        3 -> "spt"
        else -> null
    }

    fun tsrSwitchFromRaw(on: Boolean): String = if (on) "on" else "off"

    fun trunkDoorFromDisplay(state: TrunkDoorDisplayState): String? = when {
        state.movement == TrunkMovement.Opening -> "opening"
        state.movement == TrunkMovement.Closing -> "closing"
        state.isOpen == true -> "open"
        state.isOpen == false -> "closed"
        else -> null
    }

    fun doorAjarFromOpen(isOpen: Boolean?): String? = when (isOpen) {
        true -> "open"
        false -> "closed"
        null -> null
    }

    fun audioVolumeSpeedFromRaw(raw: Int): String? = when (raw) {
        1 -> "off"
        2 -> "low"
        3 -> "medium"
        4 -> "high"
        else -> null
    }

    fun audioKeyToneFromRaw(raw: Int): String? = when (raw) {
        0 -> "off"
        1 -> "low"
        2 -> "medium"
        3 -> "high"
        else -> null
    }

    fun audioRadarVolumeFromRaw(raw: Int): String? = when (raw) {
        1 -> "low"
        2 -> "medium"
        3 -> "high"
        else -> null
    }

    fun audioEqModeFromRaw(raw: Int): String? = when (raw) {
        CarSettingsAudioDomain.EQ_MODE_POP -> "pop"
        CarSettingsAudioDomain.EQ_MODE_ROCK -> "rock"
        CarSettingsAudioDomain.EQ_MODE_JAZZ -> "jazz"
        CarSettingsAudioDomain.EQ_MODE_CLASSIC -> "classic"
        CarSettingsAudioDomain.EQ_MODE_VOICE -> "voice"
        CarSettingsAudioDomain.EQ_MODE_CUSTOM -> "custom"
        else -> null
    }

    fun stateOptionLabel(raw: String): String = when (raw.trim().lowercase()) {
        "on" -> ruEn("Включено", "On")
        "off" -> ruEn("Выключено", "Off")
        "standby" -> ruEn("Ожидание", "Standby")
        "active" -> ruEn("Активен", "Active")
        "override" -> ruEn("Перебивка газом", "Throttle override")
        "fault" -> ruEn("Ошибка", "Error")
        "none" -> ruEn("Нет сети", "No network")
        "unknown" -> ruEn("Неизвестно", "Unknown")
        "checking" -> ruEn("Проверка", "Checking")
        "online" -> ruEn("Есть", "Online")
        "offline" -> ruEn("Нет", "No")
        "idle" -> ruEn("Нет опроса", "Not polling")
        "ok" -> ruEn("Связь OK", "Link OK")
        "auth_failed" -> ruEn("Ошибка входа", "Sign-in error")
        "unreachable" -> ruEn("Недоступен", "Unavailable")
        "error" -> ruEn("Ошибка", "Error")
        "2g" -> "2G"
        "3g" -> "3G"
        "4g" -> "4G"
        "ready" -> ruEn("Готова", "Ready")
        "pin" -> ruEn("Нужен PIN", "PIN required")
        "acc" -> "ACC ON"
        "ign" -> "ON"
        "crank" -> ruEn("Стартер", "Starter")
        "hold_15m" -> ruEn("15 минут работы ГУ", "15 minutes of HU runtime")
        "sleep" -> ruEn("Сон", "Sleep")
        "int" -> "INT"
        "low" -> "Low"
        "high" -> "High"
        "closed" -> ruEn("Закрыто", "Closed")
        "open" -> ruEn("Открыто", "Open")
        "tilt" -> ruEn("Откинут", "Tilted")
        "vent" -> ruEn("Щель", "Vent")
        "only" -> ruEn("Только подголовник", "Headrest only")
        "assist" -> ruEn("Подголовник + основные", "Headrest + main")
        "heat_1" -> ruEn("Подогрев 1", "Heat 1")
        "heat_2" -> ruEn("Подогрев 2", "Heat 2")
        "heat_3" -> ruEn("Подогрев 3", "Heat 3")
        "vent_1" -> ruEn("Вентиляция 1", "Ventilation 1")
        "vent_2" -> ruEn("Вентиляция 2", "Ventilation 2")
        "vent_3" -> ruEn("Вентиляция 3", "Ventilation 3")
        "30s" -> ruEn("30 с", "30 s")
        "60s" -> ruEn("60 с", "60 s")
        "driver" -> ruEn("Водитель", "Driver")
        "all" -> ruEn("Все", "All")
        "light" -> ruEn("Свет", "Light")
        "horn" -> ruEn("Сигнал", "Horn")
        "light_horn" -> ruEn("Свет и сигнал", "Light and horn")
        "ldw" -> "LDW"
        "lka" -> "LKA"
        "far" -> ruEn("Дальняя", "Far")
        "standard" -> ruEn("Стандарт", "Standard")
        "near" -> ruEn("Ближняя", "Near")
        "eco" -> "ECO"
        "comfort" -> ruEn("Комфорт", "Comfort")
        "strong" -> ruEn("Сильный", "Strong")
        "meteor" -> "Meteor"
        "boss" -> "Boss"
        "tea" -> "Tea"
        "medium" -> ruEn("Средняя", "Medium")
        "face" -> ruEn("Лицо", "Face")
        "foot" -> ruEn("Ноги", "Feet")
        "face_foot" -> ruEn("Лицо и ноги", "Face and feet")
        "defrost" -> ruEn("Лобовое", "Windshield")
        "defrost_foot" -> ruEn("Лобовое и ноги", "Windshield and feet")
        "snow" -> ruEn("Снег", "Snow")
        "day" -> ruEn("День", "Day")
        "night" -> ruEn("Ночь", "Night")
        "manual_day" -> ruEn("День (вручную)", "Day (manual)")
        "manual_night" -> ruEn("Ночь (вручную)", "Night (manual)")
        "auto_day" -> ruEn("День (авто)", "Day (auto)")
        "auto_night" -> ruEn("Ночь (авто)", "Night (auto)")
        "auto" -> ruEn("Авто", "Auto")
        "manual" -> ruEn("Вручную", "Manual")
        "sport" -> ruEn("Спорт", "Sport")
        "opening" -> ruEn("Открывается", "Opening")
        "closing" -> ruEn("Закрывается", "Closing")
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
        "retention" -> ruEn("Удержание", "Hold")
        "tbox" -> "TBox"
        "companion" -> ruEn("Компаньон", "Companion")
        "android" -> "Android"
        "usb" -> "USB"
        "phone" -> ruEn("Телефон", "Phone")
        "wifi_http" -> "Wi‑Fi HTTP"
        else -> AutomationRoadMatchSignals.highwayClassOptionLabel(raw) ?: raw
    }

    private fun legacyStateFromNumeric(signal: AutomationSignalId, raw: Double): String? {
        if (signal != AutomationSignalId.DRIVE_MODE && signal != AutomationSignalId.HEADLIGHT_MODE) {
            return null
        }
        if (!raw.isFinite() || raw != raw.toLong().toDouble()) {
            return null
        }
        return legacyStateFromNumeric(signal, raw.toInt())
    }

    private fun legacyStateFromNumeric(signal: AutomationSignalId, numeric: Int): String? = when (signal) {
        AutomationSignalId.DRIVE_MODE -> driveModeFromRaw(numeric)
        AutomationSignalId.HEADLIGHT_MODE -> headlightFromRaw(numeric)
        else -> null
    }
}
