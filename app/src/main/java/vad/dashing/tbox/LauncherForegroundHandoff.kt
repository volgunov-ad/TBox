package vad.dashing.tbox

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View

/**
 * Weak link to the active [LauncherHomeActivity] so [BackgroundService] can hide HOME after
 * starting another app (some head units keep HOME visible even when startActivity succeeds).
 */
object LauncherHomeActivityHolder {
    @Volatile
    var instance: LauncherHomeActivity? = null
}

object LauncherWindowState {
    @Volatile
    var hiddenForExternalApp: Boolean = false
}

object LauncherForegroundHandoff {
    private const val TAG = "LauncherAppLaunch"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingHandoff: Runnable? = null

    fun requestLauncherHandoff(delayMs: Long = 150L) {
        cancelPendingHandoff()
        val runnable = Runnable {
            pendingHandoff = null
            val home = LauncherHomeActivityHolder.instance
            if (home == null) {
                Log.w(TAG, "handoff skipped: LauncherHomeActivity not alive")
                return@Runnable
            }
            home.window.decorView.visibility = View.INVISIBLE
            LauncherWindowState.hiddenForExternalApp = true
            home.moveTaskToBack(true)
            Log.w(TAG, "LauncherHomeActivity hidden + moveTaskToBack")
        }
        pendingHandoff = runnable
        mainHandler.postDelayed(runnable, delayMs.coerceAtLeast(0L))
    }

    fun cancelPendingHandoff() {
        pendingHandoff?.let { mainHandler.removeCallbacks(it) }
        pendingHandoff = null
    }

    fun restoreLauncherWindow() {
        cancelPendingHandoff()
        val home = LauncherHomeActivityHolder.instance ?: return
        if (!LauncherWindowState.hiddenForExternalApp) return
        home.window.decorView.visibility = View.VISIBLE
        LauncherWindowState.hiddenForExternalApp = false
        Log.w(TAG, "LauncherHomeActivity window restored")
    }
}
