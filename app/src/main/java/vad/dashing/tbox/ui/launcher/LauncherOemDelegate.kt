package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.util.Log
import android.widget.Toast
import vad.dashing.tbox.R

private const val TAG = "LauncherAppLaunch"

/**
 * Non-whitelist apps cannot start fullscreen from third-party HOME (OEM error 102).
 * Delegating to [com.wt.launcher3] was tested on HU: HOME intent freezes the OEM launcher.
 */
object LauncherOemDelegate {
    fun notifyFullscreenBlocked(context: Context, packageName: String) {
        val label = packageName.substringAfterLast('.')
        Log.w(
            TAG,
            "fullscreen blocked by OEM whitelist pkg=$packageName (HU returns ActivityManager error 102)",
        )
        Toast.makeText(
            context,
            context.getString(R.string.launcher_oem_fullscreen_blocked, label),
            Toast.LENGTH_LONG,
        ).show()
    }
}
