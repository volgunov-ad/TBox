package vad.dashing.tbox.mbcan

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
    fun mbCan(keyCode: Int, keyStatus: Int, keyType: Int): String {
        val name = when (keyCode) {
            28 -> "OK"
            103 -> "UP"
            108 -> "DOWN"
            105 -> "LEFT"
            106 -> "RIGHT"
            114 -> "VOLUME_DOWN"
            115 -> "VOLUME_UP"
            116 -> "POWER"
            158 -> "RETURN"
            587 -> "HOME"
            else -> "UNKNOWN"
        }
        return "A9 mbCAN keyCode=$keyCode name=$name keyStatus=$keyStatus keyType=$keyType"
    }

    fun vhal(event: VhalKeyDiagnosticEvent): String {
        val timestamp = event.timestampNanos?.toString() ?: "-"
        val status = event.status?.toString() ?: "-"
        return "A10 VHAL propertyId=${event.propertyId} areaId=${event.areaId} " +
            "value=${rawValue(event.value)} type=${event.valueType} timestamp=$timestamp status=$status"
    }

    fun android(action: String, keyCode: Long): String {
        return "Android KeyEvent action=$action keyCode=$keyCode"
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
