package vad.dashing.rootsvc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager

/**
 * BOOT_COMPLETED / QUICKBOOT / MY_PACKAGE_REPLACED → ensure().
 * Ordered broadcast is not held: goAsync + worker thread (UsbMount pattern).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            QUICKBOOT_POWERON,
            -> {
                if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED && !isUnlocked(context)) return
                val pending = goAsync()
                Thread({
                    try {
                        EnsureDaemon.ensure(context, action)
                    } finally {
                        pending.finish()
                    }
                }, "rootsvc-boot").start()
            }
        }
    }

    private fun isUnlocked(context: Context): Boolean {
        val user = context.getSystemService(UserManager::class.java) ?: return true
        return user.isUserUnlocked
    }

    private companion object {
        const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
