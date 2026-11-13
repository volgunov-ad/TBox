package vad.dashing.camd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            QUICKBOOT_POWERON,
            -> start(context, action)
        }
    }

    private fun start(context: Context, action: String) {
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED && !isUnlocked(context)) return
        val service = Intent(context, CamdService::class.java)
        try {
            context.startForegroundService(service)
            Log.i(TAG, "служба запущена: $action")
        } catch (error: Exception) {
            Log.e(TAG, "startForegroundService $action", error)
            try {
                context.startService(service)
            } catch (fallback: Exception) {
                Log.e(TAG, "startService $action", fallback)
            }
        }
    }

    private fun isUnlocked(context: Context): Boolean {
        val user = context.getSystemService(UserManager::class.java) ?: return true
        return user.isUserUnlocked
    }

    private companion object {
        const val TAG = "CamdBoot"
        const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
