package vad.dashing.tbox.location

import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Size-based geo-debug file rotation. Recording stays on; only the file changes.
 */
object GeoDebugLogRotate {
    const val FILE_PREFIX = "tbox_geo_debug_"

    fun utf8Bytes(text: CharSequence): Int =
        text.toString().toByteArray(StandardCharsets.UTF_8).size

    /**
     * Rotate when the next chunk would push the current file over [maxBytes].
     * An empty file (header not flushed yet) is not rotated — avoids a loop
     * if a single chunk is larger than the limit.
     */
    fun shouldRotate(
        flushedBytes: Long,
        pendingUtf8Bytes: Int,
        nextUtf8Bytes: Int,
        maxBytes: Long,
    ): Boolean {
        if (nextUtf8Bytes <= 0 || maxBytes <= 0L) return false
        val used = flushedBytes + pendingUtf8Bytes.toLong()
        if (used <= 0L) return false
        return used + nextUtf8Bytes.toLong() > maxBytes
    }

    fun uniqueFile(
        dir: File,
        wallMs: Long,
        prefix: String = FILE_PREFIX,
        exists: (File) -> Boolean = { it.exists() },
    ): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(wallMs))
        var file = File(dir, "$prefix$stamp.txt")
        var n = 2
        while (exists(file)) {
            file = File(dir, "${prefix}${stamp}_$n.txt")
            n++
        }
        return file
    }
}
