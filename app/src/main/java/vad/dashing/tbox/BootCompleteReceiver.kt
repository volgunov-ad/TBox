package vad.dashing.tbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                startService(context)
            }
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                startService(context)
            }
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_START
            putExtra(BackgroundService.EXTRA_START_FROM_BOOT, true)
        }

        // Для Android 8+ используем startForegroundService
        context.startForegroundService(intent)
    }
}