package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent

private const val TAG = "LauncherNav"

internal fun goLauncherHome(context: Context) {
    android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_HOME)
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
    }.let { runCatching { context.startActivity(it) } }
}

/**
 * Back on HOME launcher: close overlays first, then send system Back to the focused embedded app.
 */
internal fun goLauncherBack(
    context: Context,
    vehicleSettingsOpen: Boolean,
    appDrawerOpen: Boolean,
    onCloseVehicleSettings: () -> Unit,
    onCloseAppDrawer: () -> Unit,
) {
    when {
        vehicleSettingsOpen -> onCloseVehicleSettings()
        appDrawerOpen -> onCloseAppDrawer()
        else -> dispatchBackToForegroundApp(context)
    }
}

@Suppress("DEPRECATION")
internal fun dispatchBackToForegroundApp(context: Context): Boolean {
    val launcherPkg = context.packageName
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val topPkg = runCatching {
        am.getRunningTasks(3)
            ?.firstOrNull { task ->
                val pkg = task.topActivity?.packageName
                pkg != null && pkg != launcherPkg
            }
            ?.topActivity
            ?.packageName
    }.getOrNull()

    if (topPkg == null) {
        Log.d(TAG, "dispatchBack: no embedded task")
        return false
    }

    Log.d(TAG, "dispatchBack to pkg=$topPkg")
    return injectBackKeyEvent()
}

private fun injectBackKeyEvent(): Boolean {
    runCatching {
        android.app.Instrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        return true
    }.onFailure {
        Log.w(TAG, "Instrumentation back failed", it)
    }
    return runCatching {
        Runtime.getRuntime().exec(arrayOf("input", "keyevent", KeyEvent.KEYCODE_BACK.toString())).waitFor()
        true
    }.onFailure {
        Log.w(TAG, "shell input back failed", it)
    }.getOrDefault(false)
}
