package vad.dashing.tbox.freeform

import android.graphics.Rect
import vad.dashing.tbox.MainScreenWindowModeGeometry

object FreeformLaunchBounds {
    const val MIN_PERCENT = 30
    const val MAX_PERCENT = 70
    const val DEFAULT_PERCENT = 50

    fun normalizePercent(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    /**
     * @return Pair(appBounds, tboxBounds) in display pixels for the given [side] and
     * companion [percent] (share of width for left/right, height for top/bottom).
     */
    fun computeAppAndTboxBounds(
        displayWidth: Int,
        displayHeight: Int,
        side: FreeformLaunchSide,
        percent: Int,
    ): Pair<Rect, Rect> {
        val w = displayWidth.coerceAtLeast(1)
        val h = displayHeight.coerceAtLeast(1)
        val p = normalizePercent(percent)
        return when (side) {
            FreeformLaunchSide.LEFT -> {
                val split = (w * p) / 100
                Rect(0, 0, split, h) to Rect(split, 0, w, h)
            }
            FreeformLaunchSide.RIGHT -> {
                val split = (w * (100 - p)) / 100
                Rect(split, 0, w, h) to Rect(0, 0, split, h)
            }
            FreeformLaunchSide.TOP -> {
                val split = (h * p) / 100
                Rect(0, 0, w, split) to Rect(0, split, w, h)
            }
            FreeformLaunchSide.BOTTOM -> {
                val split = (h * (100 - p)) / 100
                Rect(0, split, w, h) to Rect(0, 0, w, split)
            }
        }
    }

    fun fullscreenBounds(displayWidth: Int, displayHeight: Int): Rect =
        Rect(0, 0, displayWidth.coerceAtLeast(1), displayHeight.coerceAtLeast(1))

    /**
     * Complementary TBox area beside the freeform companion in the **same** coordinate space
     * as freeform launch bounds (app / virtual display). Prefer attaching the overlay WM to
     * that display so no physical-panel offset is required.
     *
     * [activityOriginInOverlayX]/[activityOriginInOverlayY] are used only when the overlay
     * WM still uses a larger parent space (legacy fallback).
     */
    fun computeComplementOverlayGeometry(
        activityDisplayWidth: Int,
        activityDisplayHeight: Int,
        overlayDisplayWidth: Int,
        overlayDisplayHeight: Int,
        side: FreeformLaunchSide,
        percent: Int,
        activityOriginInOverlayX: Int = 0,
        activityOriginInOverlayY: Int = 0,
    ): MainScreenWindowModeGeometry {
        val actW = activityDisplayWidth.coerceAtLeast(1)
        val actH = activityDisplayHeight.coerceAtLeast(1)
        val (_, tbox) = computeAppAndTboxBounds(actW, actH, side, percent)
        return mapActivityRectToOverlay(
            activityRect = tbox,
            overlayDisplayWidth = overlayDisplayWidth,
            overlayDisplayHeight = overlayDisplayHeight,
            activityOriginX = activityOriginInOverlayX,
            activityOriginY = activityOriginInOverlayY,
        )
    }

    /**
     * Maps a rect in activity/virtual-display coordinates into overlay WM space.
     */
    fun mapActivityRectToOverlay(
        activityRect: Rect,
        overlayDisplayWidth: Int,
        overlayDisplayHeight: Int,
        activityOriginX: Int = 0,
        activityOriginY: Int = 0,
    ): MainScreenWindowModeGeometry {
        val ovW = overlayDisplayWidth.coerceAtLeast(1)
        val ovH = overlayDisplayHeight.coerceAtLeast(1)
        val left = (activityOriginX + activityRect.left).coerceIn(0, ovW)
        val top = (activityOriginY + activityRect.top).coerceIn(0, ovH)
        val right = (activityOriginX + activityRect.right).coerceIn(left, ovW)
        val bottom = (activityOriginY + activityRect.bottom).coerceIn(top, ovH)
        return MainScreenWindowModeGeometry(
            startX = left,
            startY = top,
            width = (right - left).coerceAtLeast(MainScreenWindowModeGeometry.MIN_SIZE),
            height = (bottom - top).coerceAtLeast(MainScreenWindowModeGeometry.MIN_SIZE),
        ).normalized()
    }
}
