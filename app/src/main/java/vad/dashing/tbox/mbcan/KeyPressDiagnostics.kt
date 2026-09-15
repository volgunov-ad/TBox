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
    /** Verified on Jetour Dashing (A9 mbCAN). Код верхней левой кнопки руля зависит от модификации. */
    fun mbCanKeyName(keyCode: Int): String = when (keyCode) {
        29 -> "WHEEL_LEFT_JOY_UP"
        30 -> "WHEEL_LEFT_JOY_DOWN"
        31 -> "WHEEL_LEFT_JOY_LEFT"
        32 -> "WHEEL_LEFT_JOY_RIGHT"
        316 -> "WHEEL_LEFT_BTN_BOTTOM"
        115 -> "WHEEL_RIGHT_JOY_UP"
        114 -> "WHEEL_RIGHT_JOY_DOWN"
        163 -> "WHEEL_RIGHT_JOY_LEFT"
        165 -> "WHEEL_RIGHT_JOY_RIGHT"
        158 -> "WHEEL_RIGHT_BTN_TOP"
        582 -> "WHEEL_RIGHT_BTN_BOTTOM"
        210 -> "DOOR_FRONT_PASSENGER"
        211 -> "DOOR_REAR_RIGHT"
        212 -> "DOOR_REAR_LEFT"
        28 -> "OK"
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

    fun vhal(event: VhalKeyDiagnosticEvent): String {
        val timestamp = event.timestampNanos?.toString() ?: "-"
        val status = event.status?.toString() ?: "-"
        return "A10 VHAL propertyId=${event.propertyId} areaId=${event.areaId} " +
            "value=${rawValue(event.value)} type=${event.valueType} timestamp=$timestamp status=$status"
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
