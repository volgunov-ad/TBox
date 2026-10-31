package vad.dashing.tbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompleteReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DEBUG_BOOT_COMPLETED = "vad.dashing.tbox.DEBUG_BOOT_COMPLETED"
        private const val QUICKBOOT_POWERON_ACTION = "android.intent.action.QUICKBOOT_POWERON"
        private const val TAG = "BootCompleteReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                startService(context, Intent.ACTION_BOOT_COMPLETED)
            }
            ACTION_DEBUG_BOOT_COMPLETED -> {
                startService(context, ACTION_DEBUG_BOOT_COMPLETED)
            }
            /*Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                startService(context, Intent.ACTION_LOCKED_BOOT_COMPLETED)
            }*/
            QUICKBOOT_POWERON_ACTION -> {
                startService(context, QUICKBOOT_POWERON_ACTION)
            }
        }
    }

    private fun startService(context: Context, bootAction: String) {
        TboxRepository.addLog("INFO", "Boot receiver", "Received: $bootAction")
        val intent = Intent(context, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_START
            putExtra(BackgroundService.EXTRA_START_FROM_BOOT, true)
            putExtra(BackgroundService.EXTRA_START_SOURCE_ACTION, bootAction)
        }

        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BackgroundService ($bootAction)", e)
            TboxRepository.addLog("ERROR", "Boot receiver", "Start failed: ${e.message}")
        }
    }
}