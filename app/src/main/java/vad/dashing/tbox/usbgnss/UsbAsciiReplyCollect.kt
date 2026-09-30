package vad.dashing.tbox.usbgnss

import java.util.Locale

/**
 * When to stop waiting for a UM980 ASCII command reply.
 * Streaming NMEA ($GGA/$RMC/…) keeps flowing and must not hold the wait open,
 * but a CONFIG / UNILOGLIST dump has to finish before the quiet gap.
 */
internal object UsbAsciiReplyCollect {
    const val QUIET_AFTER_REPLY_MS = 700L

    fun isStreamingNmea(line: String): Boolean {
        val t = line.trim()
        if (t.length < 2 || t[0] != '$') return false
        val head = t.substring(1).take(8).uppercase(Locale.US)
        return !head.startsWith("COMMAND") &&
            !head.startsWith("CONFIG") &&
            !head.startsWith("MODE") &&
            !head.startsWith("MASK")
    }

    /** True once a command payload has arrived and no further reply line for [quietMs]. */
    fun ready(lines: List<String>, quietMs: Long): Boolean {
        if (quietMs < QUIET_AFTER_REPLY_MS) return false
        return lines.any { line ->
            if (isStreamingNmea(line)) return@any false
            val t = line.trim()
            t.contains("response:", ignoreCase = true) ||
                t.startsWith("#") ||
                t.startsWith("<") ||
                t.startsWith("\$CONFIG", ignoreCase = true) ||
                t.startsWith("\$MODE", ignoreCase = true) ||
                t.startsWith("\$MASK", ignoreCase = true)
        }
    }
}
