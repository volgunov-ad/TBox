package vad.dashing.tbox.automation

import vad.dashing.tbox.DRIVE_MODE_WIDGET_OPTIONS
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.mbcan.CarSettingsAdasDomain
import vad.dashing.tbox.mbcan.CarSettingsAudioDomain
import vad.dashing.tbox.mbcan.CarSettingsHudDomain
import vad.dashing.tbox.mbcan.CarSettingsLocksLightsDomain
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
    val ldwSensitivityOptions = listOf("high", "low")
    val hvacCustomOptions = listOf("eco", "comfort", "strong")
    val fragranceSmellOptions = listOf("meteor", "boss", "tea")
    val fragranceConcentrationOptions = listOf("low", "medium", "high")
    val hvacFanDirectionOptions = listOf("face", "foot", "face_foot", "defrost", "defrost_foot")
    val hudDisplayModeOptions = listOf("standard", "snow")
    val icmBrightnessModeOptions = listOf("auto", "manual")
    val steeringFeelOptions = listOf("eco", "comfort", "sport")
    val trunkDoorOptions = listOf("closed", "open", "opening", "closing")
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
        if (signal != AutomationSignalId.DRIVE_MODE && signal != AutomationSignalId.HEADLIGHT_MODE) {
            return null
        }
        val migrated = legacyStateFromNumeric(signal, expectedValue.toInt())
            ?: return null
        return AutomationCondition.State(
            signal = signal,
            source = source,
            expectedState = migrated,
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
        1 -> "eco"
        2 -> "comfort"
        3 -> "sport"
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
        else -> raw
    }

    private fun legacyStateFromNumeric(signal: AutomationSignalId, numeric: Int): String? = when (signal) {
        AutomationSignalId.DRIVE_MODE -> driveModeFromRaw(numeric)
        AutomationSignalId.HEADLIGHT_MODE -> headlightFromRaw(numeric)
        else -> null
    }
}
