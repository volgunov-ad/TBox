package vad.dashing.rootsvc

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import vad.dashing.rootsvc.client.RootSvcClient

/**
 * Early-start watchdog. Does not read events. onServiceConnected → ensure();
 * then PING every 60 s (two misses → ensure again).
 */
class RootsvcAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var misses = 0

    private val health = object : Runnable {
        override fun run() {
            Thread({
                RootSvcClient.clearCache()
                if (RootSvcClient.isUp()) {
                    misses = 0
                } else {
                    misses++
                    if (misses >= 2) {
                        misses = 0
                        EnsureDaemon.ensure(this@RootsvcAccessibilityService, "health")
                    }
                }
                handler.postDelayed(this, 60_000L)
            }, "rootsvc-health").start()
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "a11y connected — ensure")
        Thread({ EnsureDaemon.ensure(this, "a11y") }, "rootsvc-a11y").start()
        handler.removeCallbacks(health)
        handler.postDelayed(health, 60_000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(health)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "RootsvcA11y"
    }
}
