package vad.dashing.camd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            QUICKBOOT_POWERON,
            START_ACTION,
            -> context.startForegroundService(Intent(context, CamdService::class.java))
        }
    }

    private companion object {
        const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val START_ACTION = "vad.dashing.camd.START"
    }
}
