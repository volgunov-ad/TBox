package vad.dashing.tbox

import android.content.Context
import vad.dashing.tbox.R

/** Rounds duration to the nearest non-negative minute (used by [formatTripDurationHuman]). */
fun tripDurationRoundedMinutes(ms: Long): Int =
    ((ms + 30_000L).coerceAtLeast(0L) / 60_000L).toInt()

/**
 * Formats trip duration for UI: "1 ч 34 мин", or "35 мин" when hours are zero; "1 ч" when minutes are zero.
 * Rounds to the nearest minute.
 */
fun formatTripDurationHuman(context: Context, ms: Long): String {
    val totalMin = tripDurationRoundedMinutes(ms)
    if (totalMin == 0) {
        return context.getString(R.string.trips_format_minutes_only, 0)
    }
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0 -> context.getString(R.string.trips_format_minutes_only, m)
        m == 0 -> context.getString(R.string.trips_format_hours_only, h)
        else -> context.getString(R.string.trips_format_hours_minutes, h, m)
    }
}
