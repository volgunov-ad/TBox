package vad.dashing.tbox.freeform

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager

/**
 * Resolves the HU **app / virtual display** used for freeform launches vs the
 * [WindowManager] that should host the MainScreen window overlay.
 *
 * On multi-display head units, [TYPE_APPLICATION_OVERLAY][android.view.WindowManager.LayoutParams]
 * added via the service default WM often uses the full physical panel, while apps run on an
 * inset virtual display. Prefer a [Context.createDisplayContext] WM for that app display so
 * overlay x/y match freeform bounds (origin = top-start of the app display).
 */
object FreeformDisplaySpaces {
    private const val TAG = "FreeformDisplay"

    data class ActivityDisplay(
        val displayId: Int,
        val widthPx: Int,
        val heightPx: Int,
    )

    fun resolveActivityDisplay(context: Context): ActivityDisplay {
        val display = currentDisplay(context)
        val (w, h) = sizePx(context, display)
        return ActivityDisplay(
            displayId = display?.displayId ?: Display.DEFAULT_DISPLAY,
            widthPx = w,
            heightPx = h,
        )
    }

    /**
     * WindowManager bound to [displayId] when possible so overlay coordinates match that display.
     * Falls back to the context's default WM; returns null only if no WM is available at all.
     */
    fun windowManagerForDisplay(context: Context, displayId: Int): WindowManager? {
        val displayWm = windowManagerForDisplayId(context, displayId)
        if (displayWm != null) return displayWm
        return defaultWindowManager(context)
    }

    private fun defaultWindowManager(context: Context): WindowManager? {
        return context.getSystemService(WindowManager::class.java)
            ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: context.applicationContext.getSystemService(WindowManager::class.java)
            ?: context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    fun sizePxForWindowManager(wm: WindowManager): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(metrics)
            metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
        }
    }

    /** Compact one-line list of displays for rare WindowMode debug logs. */
    fun summarizeDisplays(context: Context): String {
        return try {
            val dm = context.getSystemService(DisplayManager::class.java) ?: return "no-dm"
            dm.displays.joinToString(",") { d ->
                @Suppress("DEPRECATION")
                val metrics = DisplayMetrics()
                d.getMetrics(metrics)
                "${d.displayId}:${metrics.widthPixels}x${metrics.heightPixels}"
            }.ifEmpty { "none" }
        } catch (e: Exception) {
            "err:${e.message}"
        }
    }

    /**
     * One-shot diagnostic: default WM size vs display-bound WM (same instance? size mismatch?).
     */
    fun describeOverlayWm(context: Context, displayId: Int): String {
        return try {
            val defaultWm = defaultWindowManager(context)
            val (defW, defH) = if (defaultWm != null) {
                sizePxForWindowManager(defaultWm)
            } else {
                -1 to -1
            }
            val overlayWm = windowManagerForDisplay(context, displayId)
            if (overlayWm == null) {
                return "displayId=$displayId defaultWm=${defW}x${defH} overlayWm=null"
            }
            val (ovW, ovH) = sizePxForWindowManager(overlayWm)
            val sameInstance = defaultWm != null && overlayWm === defaultWm
            "displayId=$displayId defaultWm=${defW}x${defH} overlayWm=${ovW}x${ovH} sameWm=$sameInstance"
        } catch (e: Exception) {
            "displayId=$displayId err=${e.message}"
        }
    }

    private fun windowManagerForDisplayId(context: Context, displayId: Int): WindowManager? {
        return try {
            val dm = context.getSystemService(DisplayManager::class.java) ?: return null
            val display = dm.getDisplay(displayId) ?: return null
            // API 30+: window context typed for overlays is the correct multi-display host.
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    val windowContext = context.createWindowContext(
                        display,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        null,
                    )
                    windowContext.getSystemService(WindowManager::class.java)?.let { return it }
                } catch (e: Exception) {
                    Log.w(TAG, "createWindowContext($displayId) failed, using display context", e)
                }
            }
            val displayContext = context.createDisplayContext(display)
            displayContext.getSystemService(WindowManager::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "createDisplayContext($displayId) failed", e)
            null
        }
    }

    private fun currentDisplay(context: Context): Display? {
        return if (Build.VERSION.SDK_INT >= 30) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }
    }

    @Suppress("DEPRECATION")
    private fun sizePx(context: Context, display: Display?): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) {
            val wm = context.getSystemService(WindowManager::class.java)
            if (wm != null) {
                val bounds = wm.currentWindowMetrics.bounds
                return bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
            }
        }
        val metrics = DisplayMetrics()
        if (display != null) {
            display.getMetrics(metrics)
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?.defaultDisplay?.getMetrics(metrics)
        }
        return metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
    }
}
