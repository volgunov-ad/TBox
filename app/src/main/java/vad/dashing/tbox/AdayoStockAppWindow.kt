package vad.dashing.tbox

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Adayo Android 10 launcher embeds apps in an ActivityView via
 * [ACTION_LAUNCH_APP] (stock “app window” ~1327×865).
 */
object AdayoStockAppWindow {
    const val LAUNCHER_PACKAGE = "com.adayo.launcher"
    const val ACTION_LAUNCH_APP = "com.adayo.launcher.LAUNCH_APP"
    private const val EXTRA_APP_PKG = "app_pkg"
    private const val EXTRA_APP_CLS = "app_cls"
    private const val EXTRA_APP_ACTION = "app_action"

    fun isAvailable(context: Context): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(LAUNCHER_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Asks the stock launcher to start [packageName] inside its ActivityView.
     * Returns false if the intent cannot be started (caller should fall back).
     */
    fun launchInAppWindow(
        context: Context,
        packageName: String,
        activityClass: String? = null,
        action: String? = null,
    ): Boolean {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return false
        val intent = Intent(ACTION_LAUNCH_APP).apply {
            setPackage(LAUNCHER_PACKAGE)
            putExtra(EXTRA_APP_PKG, pkg)
            if (!activityClass.isNullOrBlank()) {
                putExtra(EXTRA_APP_CLS, activityClass.trim())
            }
            if (!action.isNullOrBlank()) {
                putExtra(EXTRA_APP_ACTION, action.trim())
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
