package vad.dashing.tbox.mbcan

import android.view.KeyEvent
import java.lang.reflect.Array as ReflectArray
import java.util.Locale
import kotlin.math.absoluteValue

internal const val KEY_PRESS_DIAGNOSTIC_LOG_LIMIT = 300

internal data class KeyPressDiagnosticLog(
    val lines: List<String> = emptyList(),
) {
    fun append(line: String): KeyPressDiagnosticLog {
        return copy(lines = (lines + line).takeLast(KEY_PRESS_DIAGNOSTIC_LOG_LIMIT))
    }

    fun clear(): KeyPressDiagnosticLog = copy(lines = emptyList())

    fun asText(): String = lines.joinToString("\n")
}

internal object KeyPressDiagnosticFormat {
    /** Verified on Jetour Dashing (A9 mbCAN). Upper-left wheel button confirmed as keyCode 216. */
    fun mbCanKeyName(keyCode: Int): String = when (keyCode) {
        29 -> "WHEEL_LEFT_JOY_UP"
        30 -> "WHEEL_LEFT_JOY_DOWN"
        31 -> "WHEEL_LEFT_JOY_LEFT"
        32 -> "WHEEL_LEFT_JOY_RIGHT"
        33 -> "WHEEL_LEFT_JOY_PRESS"
        216 -> "WHEEL_LEFT_BTN_TOP"
        316 -> "WHEEL_LEFT_BTN_BOTTOM"
        115 -> "WHEEL_RIGHT_JOY_UP"
        114 -> "WHEEL_RIGHT_JOY_DOWN"
        163 -> "WHEEL_RIGHT_JOY_LEFT"
        165 -> "WHEEL_RIGHT_JOY_RIGHT"
        28 -> "WHEEL_RIGHT_JOY_PRESS"
        158 -> "WHEEL_RIGHT_BTN_TOP"
        582 -> "WHEEL_RIGHT_BTN_BOTTOM"
        210 -> "DOOR_FRONT_PASSENGER"
        211 -> "DOOR_REAR_RIGHT"
        212 -> "DOOR_REAR_LEFT"
        103 -> "UP"
        105 -> "LEFT"
        106 -> "RIGHT"
        108 -> "DOWN"
        116 -> "POWER"
        587 -> "HOME"
        else -> "UNKNOWN"
    }

    fun mbCanKeyStatus(keyStatus: Int): String = when (keyStatus) {
        0 -> "PRESSED"
        1 -> "RELEASED"
        else -> "UNKNOWN"
    }

    fun mbCan(keyCode: Int, keyStatus: Int, keyType: Int): String {
        return "A9 mbCAN keyCode=$keyCode name=${mbCanKeyName(keyCode)} " +
            "keyStatus=$keyStatus state=${mbCanKeyStatus(keyStatus)} keyType=$keyType"
    }

    /** A10 door-panel button status. Value 1 is the press that starts the stock 2200 ms hold timer. */
    fun vhalDoorName(propertyId: Int): String = when (propertyId) {
        289412276 -> "DOOR_FRONT_PASSENGER"
        289412309 -> "DOOR_REAR_LEFT"
        289412275 -> "DOOR_REAR_RIGHT"
        else -> "UNKNOWN"
    }

    /**
     * Same door numbers as A9 mbCAN so a saved door binding means the same door on both head units.
     * Wheel codes stay separate: A10 InputKey values do not match A9 keyCodes.
     */
    fun vhalDoorKeyCode(propertyId: Int): Int? = when (propertyId) {
        289412276 -> 210
        289412275 -> 211
        289412309 -> 212
        else -> null
    }

    fun vhal(event: VhalKeyDiagnosticEvent): String {
        val timestamp = event.timestampNanos?.toString() ?: "-"
        val status = event.status?.toString() ?: "-"
        val door = vhalDoorName(event.propertyId).takeIf { it != "UNKNOWN" }
        val name = if (door != null) " name=$door" else ""
        return "A10 VHAL propertyId=${event.propertyId}$name areaId=${event.areaId} " +
            "value=${rawValue(event.value)} type=${event.valueType} timestamp=$timestamp status=$status"
    }

    /**
     * Verified on Jetour Dashing A10 (`android.car.input.service` extra `InputKey`).
     * Short press and hold use the same code. Left top button is absent on that trim.
     */
    fun adayoInputKeyName(inputKey: Int): String = when (inputKey) {
        19 -> "WHEEL_LEFT_JOY_UP"
        20 -> "WHEEL_LEFT_JOY_DOWN"
        21 -> "WHEEL_LEFT_JOY_LEFT"
        22 -> "WHEEL_LEFT_JOY_RIGHT"
        195 -> "WHEEL_LEFT_JOY_PRESS"
        189 -> "WHEEL_LEFT_BTN_BOTTOM"
        4 -> "WHEEL_RIGHT_BTN_TOP"
        231 -> "WHEEL_RIGHT_BTN_BOTTOM"
        24 -> "WHEEL_RIGHT_JOY_UP"
        25 -> "WHEEL_RIGHT_JOY_DOWN"
        88 -> "WHEEL_RIGHT_JOY_LEFT"
        87 -> "WHEEL_RIGHT_JOY_RIGHT"
        91 -> "WHEEL_RIGHT_JOY_PRESS"
        else -> "UNKNOWN"
    }

    /** `InputKey` is the int code from `android.car.input.service`. `hardKey` is the older string extra. */
    fun adayo(action: String, hardKey: String?, inputKey: Int?): String {
        val input = if (inputKey != null) {
            val wheel = adayoInputKeyName(inputKey).takeIf { it != "UNKNOWN" }
            val androidName = runCatching { KeyEvent.keyCodeToString(inputKey) }.getOrNull()
                ?.takeIf { it.isNotBlank() && it != inputKey.toString() }
            buildString {
                append(" InputKey=$inputKey")
                if (wheel != null) append(" name=$wheel")
                if (androidName != null) append(" $androidName")
            }
        } else {
            ""
        }
        val named = hardKey?.takeIf { it.isNotBlank() }
        val hard = when {
            named != null -> " hardKey=$named"
            inputKey == null -> " hardKey=-"
            else -> ""
        }
        return "A10 Adayo keyEvent action=$action$input$hard"
    }

    fun android(action: String, keyCode: Long, nativeEvent: KeyEvent?): String {
        val native = if (nativeEvent != null) {
            " scanCode=${nativeEvent.scanCode} androidKeyCode=${nativeEvent.keyCode} " +
                "deviceId=${nativeEvent.deviceId} source=${nativeEvent.source} " +
                "meta=${nativeEvent.metaState} repeat=${nativeEvent.repeatCount}"
        } else {
            ""
        }
        return "Android KeyEvent action=$action keyCode=$keyCode$native"
    }

    fun subscription(subscription: VhalKeyDiagnosticSubscription): String {
        val state = if (subscription.subscribed) "OK" else "ERROR"
        return "A10 VHAL subscribe propertyId=${subscription.propertyId} $state ${subscription.detail}"
    }

    fun error(source: String, detail: String): String = "$source ERROR $detail"

    private fun rawValue(value: Any?): String {
        if (value == null) return "null"
        if (!value.javaClass.isArray) return value.toString()
        val length = ReflectArray.getLength(value)
        return (0 until length).joinToString(prefix = "[", postfix = "]") { index ->
            ReflectArray.get(value, index)?.toString() ?: "null"
        }
    }
}

internal fun formatDiagnosticClock(timestampMillis: Long): String {
    val totalSeconds = timestampMillis / 1_000L
    val millis = (timestampMillis % 1_000L).absoluteValue
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = (totalSeconds / 3_600L) % 24L
    return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
}
