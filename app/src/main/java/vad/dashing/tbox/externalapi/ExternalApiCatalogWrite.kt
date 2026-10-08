package vad.dashing.tbox.externalapi

import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationCanBus
import vad.dashing.tbox.automation.AutomationCanCatalog
import vad.dashing.tbox.automation.AutomationCanCatalogEntry
import vad.dashing.tbox.automation.AutomationCanOperation
import vad.dashing.tbox.automation.AutomationCanValueCodec
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalDescriptor
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalStateEncoding
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.BodyComfortWrite
import vad.dashing.tbox.mbcan.CarSettingsHudDomain
import vad.dashing.tbox.mbcan.HvacClimateDomain
import vad.dashing.tbox.mbcan.MbCanCommandPolicy
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.SlaSpeedLimitDomain
import java.util.Locale
import kotlin.math.abs

/**
 * Catalog v4 write schema: signal state → invoke body, built from the same tables Monitor
 * already uses. A command whose pairs cannot be checked on every supported head-unit mode
 * is published with `write = null` and stays a sensor.
 */
object ExternalApiCatalogWrite {
    const val KIND_BINARY = "binary"
    const val KIND_OPTIONS = "options"
    const val KIND_NUMBER = "number"
    const val KIND_PULSE = "pulse"

    data class WriteOption(
        val state: String,
        val value: Any,
    )

    data class WriteSchema(
        val kind: String,
        val options: List<WriteOption>,
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
        val unit: String? = null,
    )

    data class CommandSchema(
        val operations: List<String>,
        val signalId: String?,
        val write: WriteSchema?,
    )

    fun forEntry(entry: AutomationCanCatalogEntry): CommandSchema {
        val operations = entry.allowedOperations
            .sortedBy { it.ordinal }
            .map { it.storageKey }
        if (AutomationCanOperation.TRUNK_PULSE in entry.allowedOperations) {
            val pulse = pulseSchema(entry)
            return CommandSchema(operations, signalId = null, write = pulse)
        }
        val signal = linkedSignal(entry)
        val write = signal?.let { buildWrite(entry, it) }
        return CommandSchema(operations, signal?.id?.storageKey, write)
    }

    fun writeJson(schema: WriteSchema?): Any {
        if (schema == null) return JSONObject.NULL
        val options = JSONArray()
        schema.options.forEach { option ->
            options.put(
                JSONObject()
                    .put("state", option.state)
                    .put("value", option.value),
            )
        }
        val json = JSONObject()
            .put("kind", schema.kind)
            .put("options", options)
        if (schema.kind == KIND_NUMBER) {
            json.put("min", schema.min)
            json.put("max", schema.max)
            json.put("step", schema.step)
            json.put("unit", schema.unit.orEmpty())
        }
        return json
    }

    private fun linkedSignal(entry: AutomationCanCatalogEntry): AutomationSignalDescriptor? {
        val id = SIGNAL_BY_COMMAND[entry.bus to entry.propertyId] ?: return null
        return AutomationSignalCatalog.entries.firstOrNull { it.id == id }
    }

    private fun buildWrite(
        entry: AutomationCanCatalogEntry,
        signal: AutomationSignalDescriptor,
    ): WriteSchema? {
        if (signal.id.valueType == AutomationSignalValueType.NUMBER) {
            return numberSchema(entry, signal)
        }
        if (entry.policy is MbCanCommandPolicy.SetWindowPosition) {
            return windowSchema(entry, signal)
        }
        if (entry.policy is MbCanCommandPolicy.ToggleBinary &&
            signal.stateOptions == listOf("off", "on")
        ) {
            val options = listOf(
                WriteOption("off", AutomationCanValueCodec.KEY_OFF),
                WriteOption("on", AutomationCanValueCodec.KEY_ON),
            )
            if (!options.all { accepted(entry, AutomationCanOperation.SET, it.value) }) return null
            return WriteSchema(KIND_BINARY, options)
        }
        val options = signal.stateOptions.map { state ->
            val raw = rawForState(entry, state) ?: return null
            WriteOption(state, raw)
        }
        if (options.isEmpty()) return null
        if (!options.all { accepted(entry, AutomationCanOperation.SET, it.value) }) return null
        val binaryStates = options.map { it.state }.toSet() == setOf("off", "on")
        val kind = if (binaryStates) KIND_BINARY else KIND_OPTIONS
        return WriteSchema(kind, options)
    }

    private fun windowSchema(
        entry: AutomationCanCatalogEntry,
        signal: AutomationSignalDescriptor,
    ): WriteSchema? {
        // `open` is the live between-stops reading, not a write target.
        val options = signal.stateOptions.mapNotNull { state ->
            val key = WINDOW_STATE_TO_KEY[state] ?: return@mapNotNull null
            WriteOption(state, key)
        }
        if (options.isEmpty()) return null
        if (!options.all { accepted(entry, AutomationCanOperation.SET, it.value) }) return null
        return WriteSchema(KIND_OPTIONS, options)
    }

    private fun numberSchema(
        entry: AutomationCanCatalogEntry,
        signal: AutomationSignalDescriptor,
    ): WriteSchema? {
        val options = numberOptions(entry) ?: return null
        if (options.isEmpty()) return null
        if (!options.all { accepted(entry, AutomationCanOperation.SET, it.value) }) return null
        val numbers = options.map { it.state.toDouble() }
        val step = when (entry.propertyId) {
            MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
            MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT,
            -> 0.5
            MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET ->
                CarSettingsHudDomain.OVERSPEED_STEP_KMH.toDouble()
            else -> 1.0
        }
        return WriteSchema(
            kind = KIND_NUMBER,
            options = options,
            min = numbers.minOrNull(),
            max = numbers.maxOrNull(),
            step = step,
            unit = signal.unit,
        )
    }

    private fun numberOptions(entry: AutomationCanCatalogEntry): List<WriteOption>? {
        val raws = entry.allowedValues.sorted()
        if (raws.isEmpty()) return null
        return when (entry.propertyId) {
            MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
            MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT,
            -> raws.map { raw ->
                val celsius = HvacClimateDomain.mbCanTempRawToCelsius(raw) ?: return null
                WriteOption(formatNumber(celsius.toDouble()), raw)
            }
            MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET -> raws.map { raw ->
                val kmh = CarSettingsHudDomain.decodeOverspeedKmh(raw) ?: return null
                WriteOption(kmh.toString(), raw)
            }
            else -> raws.map { WriteOption(it.toString(), it) }
        }
    }

    private fun pulseSchema(entry: AutomationCanCatalogEntry): WriteSchema? {
        val options = listOf(
            WriteOption(AutomationCanValueCodec.KEY_OPEN, AutomationCanValueCodec.KEY_OPEN),
            WriteOption(AutomationCanValueCodec.KEY_CLOSE, AutomationCanValueCodec.KEY_CLOSE),
        )
        if (!options.all { accepted(entry, AutomationCanOperation.TRUNK_PULSE, it.value) }) return null
        return WriteSchema(KIND_PULSE, options)
    }

    private fun rawForState(entry: AutomationCanCatalogEntry, state: String): Int? =
        entry.allowedValues.firstOrNull { raw -> stateFromRaw(entry, raw) == state }

    private fun stateFromRaw(entry: AutomationCanCatalogEntry, raw: Int): String? {
        if (entry.bus == AutomationCanBus.AUDIO) {
            return when (entry.propertyId) {
                MbCanKnownAudioPropertyId.VOLUME_SPEED ->
                    AutomationSignalStateEncoding.audioVolumeSpeedFromRaw(raw)
                MbCanKnownAudioPropertyId.VOLUME_RADAR ->
                    AutomationSignalStateEncoding.audioRadarVolumeFromRaw(raw)
                MbCanKnownAudioPropertyId.EQ_MODE ->
                    AutomationSignalStateEncoding.audioEqModeFromRaw(raw)
                else -> null
            }
        }
        val id = MbCanKnownVehiclePropertyId
        return when (entry.propertyId) {
            id.LIGHTCONTROL -> HeadlightMode.fromRaw(raw)?.widgetLabel
            id.HEADLIGHTS_HOMELIGHT_DELAY -> AutomationSignalStateEncoding.followMeHomeFromRaw(raw)
            id.DRIVER_UNLOCK_MODE -> AutomationSignalStateEncoding.driverUnlockFromRaw(raw)
            id.DEFENCES_PROMPT -> AutomationSignalStateEncoding.remoteLockFeedbackFromRaw(raw)
            id.LAS_MODE_SELECTION -> AutomationSignalStateEncoding.lasModeFromRaw(raw)
            id.FCW_SENSITIVITY -> AutomationSignalStateEncoding.fcwSensitivityFromRaw(raw)
            id.ACC_TIME_GAP_SET -> AutomationSignalStateEncoding.accTimeGapFromRequestRaw(raw)
            id.LAS_SENSITIVITY_LEVEL -> AutomationSignalStateEncoding.ldwSensitivityFromRaw(raw)
            id.HVAC_CUSTOM -> AutomationSignalStateEncoding.hvacCustomFromRaw(raw)
            id.FRAGRANCE_SMELL -> AutomationSignalStateEncoding.fragranceSmellFromRaw(raw)
            id.FRAGRANCE_CONCENTRATION ->
                AutomationSignalStateEncoding.fragranceConcentrationFromRaw(raw)
            id.HVAC_FAN_DIRECTION -> AutomationSignalStateEncoding.hvacFanDirectionFromRaw(raw)
            id.HUD_DISPLAY_MODE -> AutomationSignalStateEncoding.hudDisplayModeFromRaw(raw)
            id.ICM_BRIGHTNESS_MODE -> AutomationSignalStateEncoding.icmBrightnessModeFromRaw(raw)
            id.VEHICLE_PROPERTY_STEERING_MODE,
            id.VEHICLE_PROPERTY_EPS_MODE,
            -> AutomationSignalStateEncoding.steeringFeelFromRaw(raw)
            id.VEHICLE_DRIVEMODE -> AutomationSignalStateEncoding.driveModeFromRaw(raw)
            id.VEHICLE_DRIVEMODE_6DCT_WET -> AutomationSignalStateEncoding.driveMode6dctFromRaw(raw)
            id.VEHICLE_TSR_SWITCH -> when (raw) {
                SlaSpeedLimitDomain.SLA_SWITCH_ON -> "on"
                SlaSpeedLimitDomain.SLA_SWITCH_OFF -> "off"
                else -> null
            }
            id.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
            id.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
            -> seatState(raw, front = true)
            id.REAR_LEFT_SEAT_HEAT_SWITCH,
            id.REAR_RIGHT_SEAT_HEAT_SWITCH,
            -> seatState(raw, front = false)
            id.SUNSHADE_POS -> shadeState(raw, allowTilt = false)
            id.SUNROOF_CONTROL -> shadeState(raw, allowTilt = true)
            else -> null
        }
    }

    private fun shadeState(raw: Int, allowTilt: Boolean): String? {
        if (allowTilt && raw == MbCanKnownVehiclePropertyId.SUNROOF_TILT) {
            return BodyComfortDomain.STATE_TILT
        }
        if (raw !in BodyComfortWrite.SHADE_VALUES) return null
        val percent = (raw - 1) * 10
        return "$percent%"
    }

    private fun seatState(raw: Int, front: Boolean): String? = when (raw) {
        1 -> "off"
        2 -> "heat_1"
        3 -> "heat_2"
        4 -> "heat_3"
        5 -> if (front) "vent_1" else null
        6 -> if (front) "vent_2" else null
        7 -> if (front) "vent_3" else null
        else -> null
    }

    private fun accepted(
        entry: AutomationCanCatalogEntry,
        operation: AutomationCanOperation,
        value: Any,
    ): Boolean {
        val action = when (value) {
            is String -> AutomationAction.CanCommand(
                bus = entry.bus,
                propertyId = entry.propertyId,
                operation = operation,
                valueKey = value,
            )
            is Int -> AutomationAction.CanCommand(
                bus = entry.bus,
                propertyId = entry.propertyId,
                operation = operation,
                value = value,
            )
            else -> return false
        }
        val canonical = AutomationCanValueCodec.canonicalize(action)
        if (!AutomationCanCatalog.isAllowed(canonical)) return false
        return entry.supportedModes.all { mode ->
            AutomationCanValueCodec.isResolvable(canonical, mode)
        }
    }

    fun formatNumber(value: Double): String {
        val nearest = kotlin.math.round(value * 10.0) / 10.0
        return if (abs(nearest - nearest.toLong()) < 0.001) {
            nearest.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", nearest)
        }
    }

    private val WINDOW_STATE_TO_KEY = mapOf(
        "0%" to AutomationCanValueCodec.KEY_CLOSE,
        "${BodyComfortDomain.WINDOW_A9_VENT_PERCENT}%" to AutomationCanValueCodec.KEY_VENT,
        "${BodyComfortDomain.WINDOW_A9_COMFORT_OPEN_PERCENT}%" to AutomationCanValueCodec.KEY_COMFORT_OPEN,
        "100%" to AutomationCanValueCodec.KEY_OPEN,
    )

    /**
     * One confirmation signal per command. Trunk pulse is intentionally absent:
     * the door sensor is a different id.
     */
    private val SIGNAL_BY_COMMAND: Map<Pair<AutomationCanBus, Int>, AutomationSignalId> = buildMap {
        fun vehicle(propertyId: Int, signal: AutomationSignalId) {
            put(AutomationCanBus.VEHICLE to propertyId, signal)
        }
        fun audio(propertyId: Int, signal: AutomationSignalId) {
            put(AutomationCanBus.AUDIO to propertyId, signal)
        }
        val id = MbCanKnownVehiclePropertyId
        vehicle(id.STEERING_WHEEL_HEAT_SWITCH, AutomationSignalId.STEERING_WHEEL_HEAT)
        vehicle(id.WIPER_MAINTENANCE_SWITCH, AutomationSignalId.WIPER_MAINTENANCE)
        vehicle(id.PARKING_RADAR_SWITCH, AutomationSignalId.PARKING_RADAR)
        vehicle(id.AVH_SWITCH, AutomationSignalId.AVH)
        vehicle(id.HDC_SWITCH, AutomationSignalId.HDC)
        vehicle(id.ESP_OFF_SWITCH, AutomationSignalId.ESP_OFF)
        vehicle(id.LIGHTCONTROL, AutomationSignalId.HEADLIGHT_MODE)
        vehicle(id.REAR_FOG_LIGHT, AutomationSignalId.REAR_FOG)
        vehicle(id.DOOR_AUTO_LOCK, AutomationSignalId.DOOR_AUTO_LOCK)
        vehicle(id.DOOR_IGNOFF_UNLOCK, AutomationSignalId.DOOR_IGNOFF_UNLOCK)
        vehicle(id.HEADLIGHTS_HOMELIGHT_DELAY, AutomationSignalId.HEADLIGHTS_FOLLOW_ME_HOME)
        vehicle(id.DRIVER_UNLOCK_MODE, AutomationSignalId.DRIVER_UNLOCK_MODE)
        vehicle(id.DEFENCES_PROMPT, AutomationSignalId.REMOTE_LOCK_FEEDBACK)
        vehicle(id.WIPER_SENSITIVITY, AutomationSignalId.WIPER_SENSITIVITY)
        vehicle(id.REAR_WIPER, AutomationSignalId.REAR_WIPER)
        vehicle(id.MIRROR_AUTOFOLD_SW, AutomationSignalId.MIRROR_AUTO_FOLD)
        vehicle(id.HIGHBEAM_ADJUST, AutomationSignalId.LOW_BEAM_HEIGHT)
        vehicle(id.TURN_FLASH_COUNT, AutomationSignalId.TURN_FLASH_COUNT)
        vehicle(id.LAS_MODE_SELECTION, AutomationSignalId.LAS_MODE)
        vehicle(id.TJA_ICA_SWITCH, AutomationSignalId.TJA_ICA)
        vehicle(id.BLIND_AREA_DETECTION, AutomationSignalId.BLIND_SPOT_DETECTION)
        vehicle(id.DOOR_OPEN_WARNING, AutomationSignalId.DOOR_OPEN_WARNING)
        vehicle(id.FCW_SWITCH, AutomationSignalId.FCW)
        vehicle(id.FCW_SENSITIVITY, AutomationSignalId.FCW_SENSITIVITY)
        vehicle(id.ACC_TIME_GAP_SET, AutomationSignalId.ACC_TIME_GAP)
        vehicle(id.LAS_SENSITIVITY_LEVEL, AutomationSignalId.LDW_SENSITIVITY)
        vehicle(id.HMA_SWITCH, AutomationSignalId.HMA)
        vehicle(id.HVAC_CUSTOM, AutomationSignalId.HVAC_CUSTOM_MODE)
        vehicle(id.HVAC_AC_MAX, AutomationSignalId.HVAC_AC_MAX)
        vehicle(id.FRONT_WINDSCREEN_HEAT_SWITCH, AutomationSignalId.FRONT_WINDSCREEN_HEAT)
        vehicle(id.HVAC_DEFROSTER_SWITCH, AutomationSignalId.HVAC_REAR_DEFROSTER)
        vehicle(id.HVAC_AIR_RECIRCULATION, AutomationSignalId.HVAC_RECIRCULATION)
        vehicle(id.HVAC_POWER, AutomationSignalId.HVAC_POWER)
        vehicle(id.HVAC_BLOWER_DELAY, AutomationSignalId.HVAC_AC_CLEAN_WHEN_LOCKED)
        vehicle(id.HVAC_AUTO_STATE, AutomationSignalId.HVAC_AUTO)
        vehicle(id.HVAC_AQS, AutomationSignalId.HVAC_ANION_PURIFY)
        vehicle(id.FRAGRANCE_SWITCH, AutomationSignalId.FRAGRANCE)
        vehicle(id.FRAGRANCE_SMELL, AutomationSignalId.FRAGRANCE_SMELL)
        vehicle(id.FRAGRANCE_CONCENTRATION, AutomationSignalId.FRAGRANCE_CONCENTRATION)
        vehicle(id.POWER_FIRST_BREATH, AutomationSignalId.HVAC_FIRST_BLOWING)
        vehicle(id.BT_REDUCED_WIND_SPEED, AutomationSignalId.BT_REDUCE_FAN)
        vehicle(id.HVAC_VENTILATION_AUTO_SWITCH, AutomationSignalId.HVAC_AUTO_VENTILATION)
        vehicle(id.HUD_SWITCH, AutomationSignalId.HUD)
        vehicle(id.HUD_HEIGHT, AutomationSignalId.HUD_HEIGHT)
        vehicle(id.HUD_BRIGHTNESS, AutomationSignalId.HUD_BRIGHTNESS)
        vehicle(id.HUD_DISPLAY_MODE, AutomationSignalId.HUD_DISPLAY_MODE)
        vehicle(id.HUD_AUTO_BRIGHTNESS, AutomationSignalId.HUD_AUTO_BRIGHTNESS)
        vehicle(id.ICM_BRIGHTNESS_MODE, AutomationSignalId.ICM_BRIGHTNESS_MODE)
        vehicle(id.ICM_BRIGHTNESS_MANUAL, AutomationSignalId.ICM_BRIGHTNESS)
        vehicle(id.OVERSPEED_ALARM_SET, AutomationSignalId.OVERSPEED_ALARM)
        vehicle(id.HVAC_FAN_DIRECTION, AutomationSignalId.HVAC_FAN_DIRECTION)
        vehicle(id.VEHICLE_PROPERTY_STEERING_MODE, AutomationSignalId.STEERING_MODE)
        vehicle(id.VEHICLE_PROPERTY_EPS_MODE, AutomationSignalId.EPS_MODE)
        vehicle(id.VEHICLE_DRIVEMODE, AutomationSignalId.DRIVE_MODE)
        vehicle(id.VEHICLE_DRIVEMODE_6DCT_WET, AutomationSignalId.DRIVE_MODE_6DCT)
        vehicle(id.VEHICLE_TSR_SWITCH, AutomationSignalId.TSR_SWITCH)
        vehicle(id.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH, AutomationSignalId.FRONT_LEFT_SEAT_MODE)
        vehicle(id.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH, AutomationSignalId.FRONT_RIGHT_SEAT_MODE)
        vehicle(id.REAR_LEFT_SEAT_HEAT_SWITCH, AutomationSignalId.REAR_LEFT_SEAT_MODE)
        vehicle(id.REAR_RIGHT_SEAT_HEAT_SWITCH, AutomationSignalId.REAR_RIGHT_SEAT_MODE)
        vehicle(id.HVAC_TEMPERATURE_LEFT, AutomationSignalId.HVAC_TEMPERATURE_LEFT)
        vehicle(id.HVAC_TEMPERATURE_RIGHT, AutomationSignalId.HVAC_TEMPERATURE_RIGHT)
        vehicle(id.HVAC_FAN_SPEED, AutomationSignalId.HVAC_FAN_SPEED)
        vehicle(id.HVAC_FRONT_OFF, AutomationSignalId.HVAC_FRONT_OFF)
        vehicle(id.HVAC_SYNC_SWITCH, AutomationSignalId.HVAC_SYNC)
        vehicle(id.SUNSHADE_POS, AutomationSignalId.SUNSHADE)
        vehicle(id.SUNROOF_CONTROL, AutomationSignalId.SUNROOF)
        vehicle(id.WINDOW_FL_POS, AutomationSignalId.WINDOW_FRONT_LEFT)
        vehicle(id.WINDOW_FR_POS, AutomationSignalId.WINDOW_FRONT_RIGHT)
        vehicle(id.WINDOW_RL_POS, AutomationSignalId.WINDOW_REAR_LEFT)
        vehicle(id.WINDOW_RR_POS, AutomationSignalId.WINDOW_REAR_RIGHT)
        audio(MbCanKnownAudioPropertyId.VOLUME_SPEED, AutomationSignalId.AUDIO_VOLUME_SPEED_MODE)
        audio(MbCanKnownAudioPropertyId.VOLUME_KEY, AutomationSignalId.AUDIO_KEY_TONE_VOLUME)
        audio(MbCanKnownAudioPropertyId.VOLUME_RADAR, AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME)
        audio(MbCanKnownAudioPropertyId.EQ_MODE, AutomationSignalId.AUDIO_EQ_MODE)
    }
}
