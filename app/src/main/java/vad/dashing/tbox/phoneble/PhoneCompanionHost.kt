package vad.dashing.tbox.phoneble

import org.json.JSONObject
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.AutomationCanBus
import vad.dashing.tbox.automation.AutomationCanOperation
import vad.dashing.tbox.automation.AutomationCanValueCodec
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
    )

    fun toAction(op: Int, seat: Int, arg: Int): AutomationAction? = when (op) {
        PhoneBleCodec.OP_TEMP_LEFT -> canSet(LEFT_TEMP, arg)
        PhoneBleCodec.OP_TEMP_RIGHT -> canSet(RIGHT_TEMP, arg)
        PhoneBleCodec.OP_FAN -> canSet(FAN, arg)
        PhoneBleCodec.OP_AUTO -> binarySet(AUTO, arg)
        PhoneBleCodec.OP_BLOW -> canSet(BLOW, arg)
        PhoneBleCodec.OP_MODE -> canSet(HVAC_MODE, arg)
        PhoneBleCodec.OP_SYNC -> binarySet(SYNC, arg)
        PhoneBleCodec.OP_SEAT -> {
            val propertyId = when (seat) {
                0 -> SEAT_DRIVER
                1 -> SEAT_PASSENGER
                2 -> SEAT_REAR_LEFT
                3 -> SEAT_REAR_RIGHT
                else -> return null
            }
            canSet(propertyId, arg)
        }
        PhoneBleCodec.OP_VOLUME -> AutomationAction.Builtin(
            type = AutomationBuiltinActionType.SET_MEDIA_VOLUME,
            intValue = arg,
        )
        PhoneBleCodec.OP_MEDIA_PREV -> builtin(AutomationBuiltinActionType.MEDIA_PREVIOUS)
        PhoneBleCodec.OP_MEDIA_PLAY_PAUSE -> builtin(AutomationBuiltinActionType.MEDIA_PLAY_PAUSE)
        PhoneBleCodec.OP_MEDIA_NEXT -> builtin(AutomationBuiltinActionType.MEDIA_NEXT)
        else -> null
    }

    fun snapshotFromSignals(headUnit: JSONObject, app: JSONObject): PhoneBleCodec.Snapshot {
        val hu = indexSignals(headUnit)
        val audio = indexSignals(app)
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
        )
    }

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
