package vad.dashing.camd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts the service on the START broadcast. The manifest requires the signature
 * permission, so only TBox Monitor can wake the camera windows.
 */
class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val service = Intent(context, CamdService::class.java)
        try {
            context.startForegroundService(service)
            Log.i(TAG, "служба запущена по запросу")
        } catch (error: Exception) {
            Log.e(TAG, "startForegroundService", error)
            try {
                context.startService(service)
            } catch (fallback: Exception) {
                Log.e(TAG, "startService", fallback)
            }
        }
    }

    private companion object {
        const val TAG = "CamdBoot"
    }
}
