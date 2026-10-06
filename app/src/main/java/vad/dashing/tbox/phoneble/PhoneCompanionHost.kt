package vad.dashing.tbox.phoneble

import org.json.JSONObject
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.AutomationCanBus
import vad.dashing.tbox.automation.AutomationCanOperation
import vad.dashing.tbox.automation.AutomationCanValueCodec
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import kotlin.math.roundToInt

/**
 * Maps the phone companion wire commands onto the same actions as the vehicle web page,
 * and folds head-unit signal JSON into one snapshot.
 */
object PhoneCompanionHost {
    const val LEFT_TEMP = 37
    const val RIGHT_TEMP = 111
    const val FAN = 38
    const val AUTO = 110
    const val BLOW = 40
    const val HVAC_MODE = 140
    const val SYNC = 94
    const val SEAT_DRIVER = 138
    const val SEAT_PASSENGER = 139
    const val SEAT_REAR_LEFT = 318
    const val SEAT_REAR_RIGHT = 319

    val headUnitSignalIds: List<String> = listOf(
        "hvac_temperature_left",
        "hvac_temperature_right",
        "hvac_fan_speed",
        "hvac_auto",
        "hvac_sync",
        "hvac_fan_direction",
        "hvac_custom_mode",
        "front_left_seat_mode",
        "front_right_seat_mode",
        "rear_left_seat_mode",
        "rear_right_seat_mode",
        "window_front_left",
        "window_front_right",
        "window_rear_left",
        "window_rear_right",
        "sunroof",
        "sunshade",
        "outside_temperature",
    )

    val tboxSignalIds: List<String> = listOf(
        "outside_temperature",
        "inside_temperature",
    )

    private val windowPropertyIds: List<Int> = listOf(
        MbCanKnownVehiclePropertyId.WINDOW_FL_POS,
        MbCanKnownVehiclePropertyId.WINDOW_FR_POS,
        MbCanKnownVehiclePropertyId.WINDOW_RL_POS,
        MbCanKnownVehiclePropertyId.WINDOW_RR_POS,
        MbCanKnownVehiclePropertyId.WINDOW_POS,
    )

    private val roofPercents: Set<Int> = (0..100 step 10).toSet()

    val appSignalIds: List<String> = listOf(
        "hu_media_volume",
        "media_title",
        "media_artist",
        "media_playing",
        "media_position_ms",
        "media_duration_ms",
    )

    /** Returns null for unknown ops and out-of-range arguments. */
    fun toAction(op: Int, seat: Int, arg: Int): AutomationAction? = when (op) {
        PhoneBleCodec.OP_TEMP_LEFT -> arg.takeIf(::validTemp)?.let { canSet(LEFT_TEMP, it) }
        PhoneBleCodec.OP_TEMP_RIGHT -> arg.takeIf(::validTemp)?.let { canSet(RIGHT_TEMP, it) }
        PhoneBleCodec.OP_FAN -> arg.takeIf { it in 0..7 }?.let { canSet(FAN, it) }
        PhoneBleCodec.OP_AUTO -> arg.takeIf { it in 0..1 }?.let { binarySet(AUTO, it) }
        PhoneBleCodec.OP_BLOW -> arg.takeIf { it in 1..5 }?.let { canSet(BLOW, it) }
        PhoneBleCodec.OP_MODE -> arg.takeIf { it in 1..3 }?.let { canSet(HVAC_MODE, it) }
        PhoneBleCodec.OP_SYNC -> arg.takeIf { it in 0..1 }?.let { binarySet(SYNC, it) }
        PhoneBleCodec.OP_SEAT -> {
            val (propertyId, maxMode) = when (seat) {
                0 -> SEAT_DRIVER to 7
                1 -> SEAT_PASSENGER to 7
                2 -> SEAT_REAR_LEFT to 4
                3 -> SEAT_REAR_RIGHT to 4
                else -> return null
            }
            arg.takeIf { it in 1..maxMode }?.let { canSet(propertyId, it) }
        }
        PhoneBleCodec.OP_VOLUME -> arg.takeIf { it in 0..31 }?.let {
            AutomationAction.Builtin(
                type = AutomationBuiltinActionType.SET_MEDIA_VOLUME,
                intValue = it,
            )
        }
        PhoneBleCodec.OP_MEDIA_PREV -> builtin(AutomationBuiltinActionType.MEDIA_PREVIOUS)
        PhoneBleCodec.OP_MEDIA_PLAY_PAUSE -> builtin(AutomationBuiltinActionType.MEDIA_PLAY_PAUSE)
        PhoneBleCodec.OP_MEDIA_NEXT -> builtin(AutomationBuiltinActionType.MEDIA_NEXT)
        PhoneBleCodec.OP_WINDOW -> {
            val propertyId = windowPropertyIds.getOrNull(seat) ?: return null
            val key = when (arg) {
                PhoneBleCodec.WINDOW_CMD_CLOSE -> AutomationCanValueCodec.KEY_CLOSE
                PhoneBleCodec.WINDOW_CMD_VENT -> AutomationCanValueCodec.KEY_VENT
                PhoneBleCodec.WINDOW_CMD_COMFORT -> AutomationCanValueCodec.KEY_COMFORT_OPEN
                PhoneBleCodec.WINDOW_CMD_OPEN -> AutomationCanValueCodec.KEY_OPEN
                else -> return null
            }
            keySet(propertyId, key)
        }
        PhoneBleCodec.OP_SUNROOF -> when (arg) {
            PhoneBleCodec.ROOF_TILT ->
                canSet(MbCanKnownVehiclePropertyId.SUNROOF_CONTROL, MbCanKnownVehiclePropertyId.SUNROOF_TILT)
            in roofPercents ->
                canSet(MbCanKnownVehiclePropertyId.SUNROOF_CONTROL, BodyComfortDomain.percentToWrite(arg))
            else -> null
        }
        PhoneBleCodec.OP_SUNSHADE -> arg.takeIf { it in roofPercents }?.let {
            canSet(MbCanKnownVehiclePropertyId.SUNSHADE_POS, BodyComfortDomain.percentToWrite(it))
        }
        else -> null
    }

    private fun validTemp(tenths: Int): Boolean =
        tenths in PhoneBleCodec.TEMP_MIN..PhoneBleCodec.TEMP_MAX &&
            (tenths - PhoneBleCodec.TEMP_MIN) % PhoneBleCodec.TEMP_STEP == 0

    fun snapshotFromSignals(
        headUnit: JSONObject,
        app: JSONObject,
        tbox: JSONObject = JSONObject(),
        android10: Boolean? = null,
    ): PhoneBleCodec.Snapshot {
        val hu = indexSignals(headUnit)
        val audio = indexSignals(app)
        val box = indexSignals(tbox)
        return PhoneBleCodec.Snapshot(
            leftTenths = tenths(hu["hvac_temperature_left"]),
            rightTenths = tenths(hu["hvac_temperature_right"]),
            fan = intValue(hu["hvac_fan_speed"]),
            mode = modeValue(hu["hvac_custom_mode"]),
            auto = onOff(hu["hvac_auto"]),
            blow = blowValue(hu["hvac_fan_direction"]),
            sync = onOff(hu["hvac_sync"]),
            seats = listOf(
                seatValue(hu["front_left_seat_mode"]),
                seatValue(hu["front_right_seat_mode"]),
                seatValue(hu["rear_left_seat_mode"]),
                seatValue(hu["rear_right_seat_mode"]),
            ),
            volume = intValue(audio["hu_media_volume"]),
            playing = onOff(audio["media_playing"]),
            positionMs = longValue(audio["media_position_ms"]),
            durationMs = longValue(audio["media_duration_ms"])?.takeIf { it > 0L },
            title = (audio["media_title"] as? String)?.takeIf { it.isNotBlank() },
            artist = (audio["media_artist"] as? String)?.takeIf { it.isNotBlank() },
            windows = listOf(
                windowValue(hu["window_front_left"]),
                windowValue(hu["window_front_right"]),
                windowValue(hu["window_rear_left"]),
                windowValue(hu["window_rear_right"]),
            ),
            sunroof = roofValue(hu["sunroof"], allowTilt = true),
            sunshade = roofValue(hu["sunshade"], allowTilt = false),
            android10 = android10,
            outsideTenths = tenths(hu["outside_temperature"]) ?: tenths(box["outside_temperature"]),
            insideTenths = tenths(box["inside_temperature"]),
        )
    }

    private fun windowValue(raw: Any?): Int? {
        val state = text(raw) ?: return null
        if (state == BodyComfortDomain.STATE_OPEN) return PhoneBleCodec.WINDOW_BETWEEN
        return percentValue(state)
    }

    private fun roofValue(raw: Any?, allowTilt: Boolean): Int? {
        val state = text(raw) ?: return null
        if (state == BodyComfortDomain.STATE_TILT) return if (allowTilt) PhoneBleCodec.ROOF_TILT else null
        return percentValue(state)
    }

    private fun percentValue(state: String): Int? =
        state.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }

    private fun keySet(propertyId: Int, key: String): AutomationAction =
        AutomationCanValueCodec.canonicalize(
            AutomationAction.CanCommand(
                bus = AutomationCanBus.VEHICLE,
                propertyId = propertyId,
                operation = AutomationCanOperation.SET,
                valueKey = key,
            ),
        )

    private fun canSet(propertyId: Int, value: Int): AutomationAction =
        AutomationCanValueCodec.canonicalize(
            AutomationAction.CanCommand(
                bus = AutomationCanBus.VEHICLE,
                propertyId = propertyId,
                operation = AutomationCanOperation.SET,
                value = value,
            ),
        )

    private fun binarySet(propertyId: Int, arg: Int): AutomationAction =
        AutomationCanValueCodec.canonicalize(
            AutomationAction.CanCommand(
                bus = AutomationCanBus.VEHICLE,
                propertyId = propertyId,
                operation = AutomationCanOperation.SET,
                valueKey = if (arg != 0) AutomationCanValueCodec.KEY_ON else AutomationCanValueCodec.KEY_OFF,
            ),
        )

    private fun builtin(type: AutomationBuiltinActionType): AutomationAction =
        AutomationAction.Builtin(type = type)

    private fun indexSignals(root: JSONObject): Map<String, Any?> {
        val arr = root.optJSONArray("signals") ?: return emptyMap()
        return buildMap {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                if (!item.optBoolean("available", false)) continue
                val id = item.optString("id", "")
                if (id.isEmpty() || item.isNull("value")) continue
                put(id, item.opt("value"))
            }
        }
    }

    private fun tenths(raw: Any?): Int? {
        val number = (raw as? Number)?.toDouble() ?: return null
        if (!number.isFinite()) return null
        return (number * 10.0).roundToInt()
    }

    private fun intValue(raw: Any?): Int? {
        val number = (raw as? Number)?.toDouble() ?: return null
        if (!number.isFinite()) return null
        return number.roundToInt()
    }

    private fun longValue(raw: Any?): Long? {
        val number = (raw as? Number)?.toDouble() ?: return null
        if (!number.isFinite() || number < 0.0) return null
        return number.toLong()
    }

    private fun text(raw: Any?): String? = (raw as? String)?.trim()?.lowercase()

    private fun onOff(raw: Any?): Int? = when (text(raw)) {
        "on" -> 1
        "off" -> 0
        else -> null
    }

    private fun modeValue(raw: Any?): Int? = when (text(raw)) {
        "eco" -> 1
        "comfort" -> 2
        "strong" -> 3
        else -> null
    }

    private fun blowValue(raw: Any?): Int? = when (text(raw)) {
        "face" -> 1
        "foot" -> 2
        "face_foot" -> 3
        "defrost" -> 4
        "defrost_foot" -> 5
        else -> null
    }

    private fun seatValue(raw: Any?): Int? = when (text(raw)) {
        "off" -> 1
        "heat_1" -> 2
        "heat_2" -> 3
        "heat_3" -> 4
        "vent_1" -> 5
        "vent_2" -> 6
        "vent_3" -> 7
        else -> null
    }
}
