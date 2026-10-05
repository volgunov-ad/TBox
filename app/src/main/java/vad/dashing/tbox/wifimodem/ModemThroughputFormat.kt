package vad.dashing.tbox.wifimodem

import java.util.Locale

/**
 * Formats modem throughput (bytes/s) for the Modem tab.
 * Uses binary KB/MB (1024) with one decimal where helpful.
 */
object ModemThroughputFormat {
    fun formatBps(bps: Long?): String {
        if (bps == null) return "-"
        if (bps < 0L) return "-"
        if (bps < 1024L) return "$bps B/s"
        val kb = bps / 1024.0
        if (kb < 1024.0) {
            return if (kb >= 100.0) "${kb.toInt()} KB/s" else String.format(Locale.ROOT, "%.1f KB/s", kb)
        }
        val mb = kb / 1024.0
        return if (mb >= 100.0) "${mb.toInt()} MB/s" else String.format(Locale.ROOT, "%.1f MB/s", mb)
    }

    fun parseBps(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().takeWhile { it.isDigit() || it == '-' || it == '+' }
        return cleaned.toLongOrNull()?.takeIf { it >= 0L }
    }
}
