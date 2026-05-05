package vad.dashing.tbox.trip

import android.content.Context
import vad.dashing.tbox.R

/** Floor division to whole seconds (non-negative). */
internal fun tripDurationTotalSeconds(ms: Long): Int =
    (ms.coerceAtLeast(0L) / 1000L).toInt()

/**
 * Hours, minutes, seconds from [ms] using truncated whole seconds (same as [tripDurationTotalSeconds]).
 */
fun tripDurationHms(ms: Long): Triple<Int, Int, Int> {
    val sec = tripDurationTotalSeconds(ms)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return Triple(h, m, s)
}

/**
 * Formats trip duration for UI with seconds, e.g. "1 ч 12 мин 5 с" / "1 h 12 min 5 s".
 */
fun formatTripDurationHuman(context: Context, ms: Long): String {
    val (h, m, s) = tripDurationHms(ms)
    return when {
        h > 0 -> context.getString(R.string.trips_format_hours_minutes_seconds, h, m, s)
        m > 0 -> context.getString(R.string.trips_format_minutes_seconds, m, s)
        else -> context.getString(R.string.trips_format_seconds_only, s)
    }
}
