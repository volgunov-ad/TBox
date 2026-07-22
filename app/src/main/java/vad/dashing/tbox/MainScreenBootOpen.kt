package vad.dashing.tbox

import android.content.Context
import android.os.SystemClock

/**
 * Pure policy + lightweight prefs for «open main screen after boot».
 * Keeps boot-open independent of whether [BackgroundService] was already running.
 */
internal object MainScreenBootOpenPolicy {

    /** Gaps between startActivity attempts after the user-configured initial delay. */
    val RETRY_GAPS_MS: LongArray = longArrayOf(0L, 2_000L, 5_000L, 15_000L, 30_000L)

    /** Wall-clock budget from first pending mark (elapsedRealtime). */
    const val MAX_EPISODE_MS: Long = 30_000L

    /** Delay before checking [MainActivityForegroundTracker] after each startActivity. */
    const val VERIFY_AFTER_LAUNCH_MS: Long = 2_000L

    /**
     * @param attemptIndex 0-based attempt after the initial settings delay.
     * @return delay before this attempt, or null when no more retries in the schedule.
     */
    fun delayBeforeAttemptMs(attemptIndex: Int): Long? {
        if (attemptIndex < 0 || attemptIndex >= RETRY_GAPS_MS.size) return null
        return RETRY_GAPS_MS[attemptIndex]
    }

    fun isEpisodeExpired(nowElapsedRealtimeMs: Long, deadlineElapsedRealtimeMs: Long): Boolean =
        nowElapsedRealtimeMs >= deadlineElapsedRealtimeMs

    fun newDeadlineElapsedRealtimeMs(
        nowElapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        maxEpisodeMs: Long = MAX_EPISODE_MS,
    ): Long = nowElapsedRealtimeMs + maxEpisodeMs
}

/**
 * Process-surviving pending flag for boot-open (not part of settings backup JSON).
 */
internal object MainScreenBootOpenStore {
    private const val PREFS = "main_screen_boot_open"
    private const val KEY_PENDING = "pending"
    private const val KEY_DEADLINE_ELAPSED = "deadline_elapsed"
    private const val KEY_SOURCE = "source"

    fun markPending(context: Context, sourceAction: String?) {
        val deadline = MainScreenBootOpenPolicy.newDeadlineElapsedRealtimeMs()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PENDING, true)
            .putLong(KEY_DEADLINE_ELAPSED, deadline)
            .putString(KEY_SOURCE, sourceAction.orEmpty())
            .apply()
    }

    fun clearPending(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PENDING, false)
            .remove(KEY_DEADLINE_ELAPSED)
            .remove(KEY_SOURCE)
            .apply()
    }

    fun isPending(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PENDING, false)

    fun deadlineElapsedRealtimeMs(context: Context): Long =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_DEADLINE_ELAPSED, 0L)

    fun sourceAction(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SOURCE, "").orEmpty()
}
