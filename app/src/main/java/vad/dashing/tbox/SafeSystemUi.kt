package vad.dashing.tbox

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import android.view.WindowManager

private const val TAG = "SafeSystemUi"

fun Context.findActivityOrNull(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

/** Skips the dialog when its window token is gone (activity finishing/destroyed) instead of crashing. */
fun AlertDialog.Builder.showSafely(): AlertDialog? {
    val activity = context.findActivityOrNull()
    if (activity != null && (activity.isFinishing || activity.isDestroyed)) return null
    return try {
        show()
    } catch (e: WindowManager.BadTokenException) {
        Log.w(TAG, "Dialog skipped: window token is no longer valid", e)
        null
    }
}

fun Context.startActivitySafely(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Cannot start ${intent.action ?: intent.component}", e)
        false
    }
