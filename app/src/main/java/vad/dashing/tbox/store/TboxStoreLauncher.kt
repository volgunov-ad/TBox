package vad.dashing.tbox.store

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Opens the companion [vad.dashing.store] APK (TBox Store).
 * Returns false when the store is not installed.
 */
object TboxStoreLauncher {
    const val PACKAGE_NAME = "vad.dashing.store"
    const val ACTION_OPEN = "vad.dashing.store.action.OPEN"

    fun isInstalled(packageManager: PackageManager): Boolean =
        packageManager.getLaunchIntentForPackage(PACKAGE_NAME) != null

    fun open(context: Context): Boolean {
        val pm = context.packageManager
        val explicit = Intent(ACTION_OPEN).setPackage(PACKAGE_NAME)
        val launch = if (explicit.resolveActivity(pm) != null) {
            explicit
        } else {
            pm.getLaunchIntentForPackage(PACKAGE_NAME)
        } ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }
}
