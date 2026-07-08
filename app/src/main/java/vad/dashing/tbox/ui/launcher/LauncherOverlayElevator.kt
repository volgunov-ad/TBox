package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import vad.dashing.tbox.LauncherForegroundHandoff
import vad.dashing.tbox.LauncherHomeActivityHolder

/** Brings launcher activity above embedded freeform windows while overlays are open. */
internal object LauncherOverlayElevator {
    private const val TAG = "LauncherOverlay"
    private const val HOLD_POLL_MS = 350L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var holdPollRunnable: Runnable? = null
    private val holdSources = mutableSetOf<String>()

    @Volatile
    private var _overlayHoldActive: Boolean = false
    val overlayHoldActive: Boolean get() = _overlayHoldActive

    fun setHoldSource(source: String, active: Boolean) {
        if (source.isBlank()) return
        synchronized(holdSources) {
            if (active) holdSources.add(source) else holdSources.remove(source)
            updateHoldStateLocked()
        }
    }

    @Deprecated("Use setHoldSource")
    fun applyOverlayHold(active: Boolean) {
        setHoldSource("legacy", active)
    }

    private fun updateHoldStateLocked() {
        val shouldHold = holdSources.isNotEmpty()
        if (shouldHold == _overlayHoldActive) {
            if (shouldHold) {
                LauncherHomeActivityHolder.instance?.let { bringLauncherToFront(it) }
            }
            return
        }
        _overlayHoldActive = shouldHold
        val home = LauncherHomeActivityHolder.instance
        if (shouldHold && home != null) {
            startHoldPolling(home)
        } else {
            stopHoldPolling()
            releaseOverlayElevation()
        }
    }

    fun reset() {
        synchronized(holdSources) {
            holdSources.clear()
            _overlayHoldActive = false
        }
        stopHoldPolling()
        releaseOverlayElevation()
    }

    fun bringLauncherToFront(context: Context) {
        val home = LauncherHomeActivityHolder.instance ?: return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            home.runOnUiThread { bringLauncherToFront(home) }
            return
        }
        bringLauncherToFront(home)
    }

    private fun bringLauncherToFront(home: vad.dashing.tbox.LauncherHomeActivity) {
        LauncherForegroundHandoff.restoreLauncherWindow()
        home.window.decorView.visibility = View.VISIBLE
        runCatching {
            val params = home.window.attributes
            params.alpha = 1f
            home.window.attributes = params
            home.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            home.window.decorView.elevation = 64f
            home.window.decorView.translationZ = 64f
            val am = home.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.moveTaskToFront(home.taskId, ActivityManager.MOVE_TASK_WITH_HOME)
            home.window.decorView.bringToFront()
            Log.d(TAG, "moveTaskToFront task=${home.taskId} sources=$holdSources")
        }.onFailure {
            Log.w(TAG, "bringLauncherToFront failed", it)
        }
    }

    private fun startHoldPolling(home: vad.dashing.tbox.LauncherHomeActivity) {
        stopHoldPolling()
        bringLauncherToFront(home)
        val runnable = object : Runnable {
            override fun run() {
                if (!_overlayHoldActive) return
                bringLauncherToFront(home)
                mainHandler.postDelayed(this, HOLD_POLL_MS)
            }
        }
        holdPollRunnable = runnable
        mainHandler.postDelayed(runnable, HOLD_POLL_MS)
    }

    private fun stopHoldPolling() {
        holdPollRunnable?.let { mainHandler.removeCallbacks(it) }
        holdPollRunnable = null
    }

    fun releaseOverlayElevation() {
        val home = LauncherHomeActivityHolder.instance ?: return
        runCatching {
            home.window.decorView.elevation = 0f
            home.window.decorView.translationZ = 0f
        }
    }
}
