package vad.dashing.tbox.ui.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import vad.dashing.tbox.LastAppTracker

private const val TAG = "LauncherFreeform"
private const val WINDOWING_MODE_FREEFORM = 5

internal fun isFreeformEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
    return runCatching {
        Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
    }.getOrDefault(false)
}

/**
 * Launch [packageName] in a freeform window at [bounds] (screen pixels).
 * Returns true when startActivity succeeded with bounded options.
 */
internal fun tryLaunchInFreeformBounds(
    context: Context,
    packageName: String,
    bounds: Rect,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
    val intent = resolveLaunchIntent(context, packageName) ?: return false
    return tryLaunchIntentInBounds(context, packageName, intent, bounds)
}

internal fun tryLaunchIntentInBounds(
    context: Context,
    packageName: String,
    intent: Intent,
    bounds: Rect,
    launchAdjacent: Boolean = false,
): Boolean {
    val launchIntent = Intent(intent).apply {
        if (action.isNullOrBlank()) action = Intent.ACTION_MAIN
        if (categories?.isEmpty() != false) addCategory(Intent.CATEGORY_LAUNCHER)
        flags = 0
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        if (launchAdjacent) {
            addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        }
    }
    val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, WINDOWING_MODE_FREEFORM)
        }.onFailure {
            Log.w(TAG, "setLaunchWindowingMode unavailable: ${it.message}")
        }
    }
    val activity = context.findLauncherActivity()
    return runCatching {
        (activity ?: context).startActivity(launchIntent, options.toBundle())
    }.onSuccess {
        LastAppTracker.recordLaunch(context, packageName)
        Log.w("LauncherAppLaunch", "freeform startActivity OK pkg=$packageName bounds=$bounds")
    }.onFailure {
        Log.w("LauncherAppLaunch", "freeform startActivity failed pkg=$packageName bounds=$bounds", it)
    }.isSuccess
}

private fun Context.findLauncherActivity(): android.app.Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return this as? android.app.Activity
}
