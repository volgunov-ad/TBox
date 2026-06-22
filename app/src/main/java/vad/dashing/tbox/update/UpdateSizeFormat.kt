package vad.dashing.tbox.update

import java.util.Locale
import kotlin.math.roundToInt

fun formatApkSizeMegabytes(bytes: Long): String {
    if (bytes <= 0L) return "0"
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 10.0) {
        megabytes.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", megabytes)
    }
}
