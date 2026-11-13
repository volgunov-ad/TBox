package vad.dashing.tbox

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * System-bound when the user grants Notification Listener access.
 * On connect, kicks [BackgroundService] as a foreground service so the monitoring stack
 * can start before [android.content.Intent.ACTION_BOOT_COMPLETED] (same ACTION_START path
 * as MainActivity / widgets). Does **not** open MainActivity or mark boot-open pending.
 *
 * Also best-effort wakes «Камеры ГУ» when installed (same gate as [BootCompleteReceiver]),
 * once per process — NLS often binds before BOOT_COMPLETED and can reconnect.
 */
class MediaControlNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        startBackgroundServiceFromListener()
        wakeCamdFromListenerIfNeeded()
        SharedMediaControlService.startActiveSessionMonitor(applicationContext)
    }

    private fun startBackgroundServiceFromListener() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_START
            putExtra(
                BackgroundService.EXTRA_START_SOURCE_ACTION,
                START_SOURCE_LISTENER_CONNECTED,
            )
        }
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BackgroundService from NLS", e)
        }
    }

    private fun wakeCamdFromListenerIfNeeded() {
        // Reconnects must not spam START when camd was already woken from this process.
        if (camdWakeAttempted) return
        if (!isCamdPackageInstalled(packageManager)) return
        camdWakeAttempted = true
        startCamdService(applicationContext)
    }

    companion object {
        private const val TAG = "MediaControlNLS"

        /** Value of [BackgroundService.EXTRA_START_SOURCE_ACTION] when started from NLS. */
        const val START_SOURCE_LISTENER_CONNECTED = "notification_listener_connected"

        @Volatile
        private var camdWakeAttempted = false

        /** Test-only: clear the once-per-process camd wake latch. */
        internal fun resetCamdWakeAttemptedForTests() {
            camdWakeAttempted = false
        }
    }
}
