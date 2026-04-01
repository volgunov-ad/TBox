package vad.dashing.tbox

import android.content.Context
import android.content.Intent

/**
 * When the user leaves [MainActivity] for an external media player from a music dashboard widget,
 * [BackgroundService] can bring [MainActivity] back after a delay.
 */
object DeferredMainActivityRequest {

    const val AFTER_MUSIC_WIDGET_PLAYER_LAUNCH_MS = 5000L

    /**
     * If [MainActivity] is currently in the foreground, asks [BackgroundService] to open it again
     * after [AFTER_MUSIC_WIDGET_PLAYER_LAUNCH_MS]. Repeated calls replace the previous scheduled job.
     */
    fun scheduleReturnAfterExternalPlayerLaunchIfMainWasVisible(context: Context) {
        if (!MainActivityForegroundTracker.isMainActivityInForeground.value) return
        val app = context.applicationContext
        val intent = Intent(app, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_OPEN_MAIN_ACTIVITY
            putExtra(BackgroundService.EXTRA_OPEN_MAIN_DELAY_MS, AFTER_MUSIC_WIDGET_PLAYER_LAUNCH_MS)
        }
        try {
            app.startService(intent)
        } catch (_: Exception) {
            // Service may be unavailable in rare states; launching the player still proceeds.
        }
    }
}
