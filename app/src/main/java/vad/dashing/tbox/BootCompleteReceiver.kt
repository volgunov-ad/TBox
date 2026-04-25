package vad.dashing.tbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompleteReceiver : BroadcastReceiver() {
    companion object {
        private const val QUICKBOOT_POWERON_ACTION = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                startService(context, Intent.ACTION_BOOT_COMPLETED)
            }
            /*Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                startService(context, Intent.ACTION_LOCKED_BOOT_COMPLETED)
            }
            QUICKBOOT_POWERON_ACTION -> {
                startService(context, QUICKBOOT_POWERON_ACTION)
            }*/
        }
    }

    private fun startService(context: Context, bootAction: String) {
        val intent = Intent(context, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_START
            putExtra(BackgroundService.EXTRA_START_FROM_BOOT, true)
            putExtra(BackgroundService.EXTRA_START_SOURCE_ACTION, bootAction)
        }

        // Для Android 8+ используем startForegroundService
        context.startForegroundService(intent)
    }
}