package vad.dashing.tbox

import android.os.SystemClock

/**
 * Coordinates long cold-start delays for **embedded** third-party [android.appwidget.AppWidgetHost]
 * widgets only (not [AppWidgetManager] itself).
 *
 * - **Floating overlay**: one coordinated ~10s window per [BackgroundService] session — the first
 *   time any floating panel mounts, all external tiles wait until that window elapses; later
 *   mounts in the same session get no wait.
 * - **Main screen**: a fixed delay is supplied when [MainActivity] was opened from the boot path
 *   ([MainActivityIntentHelper.EXTRA_OPENED_AFTER_BOOT_SERVICE]); consumed once per activity launch.
 */
object ExternalWidgetColdStartGate {
    const val COLD_EXTERNAL_WIDGET_INIT_MS = 10_000L

    private val sessionLock = Any()
    private var floatingColdEndElapsed = 0L

    /**
     * Call when [BackgroundService] begins a fresh run ([kickoffStart]) so a new floating session
     * gets the cold window again.
     */
    fun resetFloatingSessionForNewServiceRun() {
        synchronized(sessionLock) {
            floatingColdEndElapsed = 0L
        }
    }

    /**
     * Remaining time until the floating-session cold window ends. The first caller in a session
     * starts the window; concurrent callers share the same deadline.
     */
    fun floatingColdRemainingMs(): Long {
        synchronized(sessionLock) {
            val now = SystemClock.elapsedRealtime()
            if (floatingColdEndElapsed == 0L) {
                floatingColdEndElapsed = now + COLD_EXTERNAL_WIDGET_INIT_MS
            }
            return (floatingColdEndElapsed - now).coerceAtLeast(0L)
        }
    }
}
