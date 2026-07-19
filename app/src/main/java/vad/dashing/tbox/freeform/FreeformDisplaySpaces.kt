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
 * inset virtual display (e.g. display 5 @ 1320×856 while default is 1920×981). Prefer that
 * inset VD for freeform bounds and overlay WM so x/y match.
 */
object FreeformDisplaySpaces {
    private const val TAG = "FreeformDisplay"

    /** Ignore status/cluster strips and tiny secondary panels. */
    private const val MIN_APP_SHORT_SIDE_PX = 400
    private const val MIN_APP_LONG_SIDE_PX = 800

    data class ActivityDisplay(
        val displayId: Int,
        val widthPx: Int,
        val heightPx: Int,
    )

    data class DisplaySize(
        val displayId: Int,
        val widthPx: Int,
        val heightPx: Int,
    ) {
        val area: Long get() = widthPx.toLong() * heightPx
        val shortSide: Int get() = minOf(widthPx, heightPx)
        val longSide: Int get() = maxOf(widthPx, heightPx)
    }

    fun resolveActivityDisplay(context: Context): ActivityDisplay {
        val catalog = listDisplaySizes(context)
        val fromContext = currentDisplay(context)?.let { display ->
            val (w, h) = sizePxForDisplay(display)
            DisplaySize(display.displayId, w, h)
        }
        return resolveFromCatalog(catalog, contextDisplay = fromContext)
    }

    /**
     * Pure resolve used by [resolveActivityDisplay] and unit tests.
     * Prefers [pickAppVirtualDisplay] so applicationContext / overlay Context on display 0
     * still resolve to the HU inset app VD.
     */
    internal fun resolveFromCatalog(
        catalog: List<DisplaySize>,
        contextDisplay: DisplaySize?,
    ): ActivityDisplay {
        pickAppVirtualDisplay(catalog)?.let { picked ->
            return ActivityDisplay(picked.displayId, picked.widthPx, picked.heightPx)
        }
        val fallback = contextDisplay
            ?: catalog.firstOrNull { it.displayId == Display.DEFAULT_DISPLAY }
            ?: DisplaySize(Display.DEFAULT_DISPLAY, 1280, 720)
        return ActivityDisplay(fallback.displayId, fallback.widthPx, fallback.heightPx)
    }

    /**
     * Context bound to [displayId] for [Context.startActivity] so freeform companions land on
     * the same virtual display as launch bounds / overlay (not the default panel).
     */
    fun contextForDisplay(context: Context, displayId: Int): Context {
        if (displayId == Display.DEFAULT_DISPLAY) return context.applicationContext
        return try {
            val dm = context.getSystemService(DisplayManager::class.java) ?: return context.applicationContext
            val display = dm.getDisplay(displayId) ?: return context.applicationContext
            context.applicationContext.createDisplayContext(display)
        } catch (e: Exception) {
            Log.w(TAG, "contextForDisplay($displayId) failed", e)
            context.applicationContext
        }
    }

    /**
     * Among HU displays, prefer the largest non-default surface that looks like the app /
     * freeform canvas (not a status strip): shorter side ≥ [MIN_APP_SHORT_SIDE_PX], longer
     * side ≥ [MIN_APP_LONG_SIDE_PX], and area strictly smaller than the default display when
     * a default exists (inset VD).
     *
     * Jetour sample: `0:1920x981` (default) + `5:1320x856` (app) → picks 5.
     */
    internal fun pickAppVirtualDisplay(displays: List<DisplaySize>): DisplaySize? {
        if (displays.isEmpty()) return null
        val defaultArea = displays
            .firstOrNull { it.displayId == Display.DEFAULT_DISPLAY }
            ?.area
            ?: 0L
        val candidates = displays.filter { d ->
            d.displayId != Display.DEFAULT_DISPLAY &&
                d.shortSide >= MIN_APP_SHORT_SIDE_PX &&
                d.longSide >= MIN_APP_LONG_SIDE_PX &&
                (defaultArea <= 0L || d.area < defaultArea)
        }
        return candidates.maxByOrNull { it.area }
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
            listDisplaySizes(context).joinToString(",") { d ->
                "${d.displayId}:${d.widthPx}x${d.heightPx}"
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

    private fun listDisplaySizes(context: Context): List<DisplaySize> {
        val dm = context.getSystemService(DisplayManager::class.java) ?: return emptyList()
        return dm.displays.map { display ->
            val (w, h) = sizePxForDisplay(display)
            DisplaySize(display.displayId, w, h)
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
    private fun sizePxForDisplay(display: Display): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)
        return metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
    }
}
