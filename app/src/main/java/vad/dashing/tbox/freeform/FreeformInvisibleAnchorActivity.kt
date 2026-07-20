package vad.dashing.tbox.freeform

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.ContextCompat

/**
 * Taskbar-style freeform workspace anchor: tiny freeform activity that keeps
 * multi-window/freeform active. Touches pass through to windows behind it.
 */
class FreeformInvisibleAnchorActivity : Activity() {

    companion object {
        const val ACTION_FINISH =
            "vad.dashing.tbox.freeform.FINISH_INVISIBLE_ANCHOR"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reallyFinish()
        }
    }

    private var initialLaunch = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isRunning) {
            super.finish()
            overridePendingTransition(0, 0)
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        ContextCompat.registerReceiver(
            this,
            finishReceiver,
            IntentFilter(ACTION_FINISH),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isRunning = true
    }

    override fun onResume() {
        super.onResume()
        if (!isInMultiWindowMode && !initialLaunch) {
            reallyFinish()
        }
        initialLaunch = false
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(finishReceiver)
        } catch (_: Exception) {
        }
        isRunning = false
        super.onDestroy()
    }

    /** Keep the anchor alive under normal back / finish requests. */
    override fun finish() {
        // no-op
    }

    private fun reallyFinish() {
        isRunning = false
        super.finish()
        overridePendingTransition(0, 0)
    }
}
