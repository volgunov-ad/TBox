package vad.dashing.tbox

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Bundle

/**
 * Merges size hints into existing widget options so provider-specific keys (e.g. from configure)
 * are not dropped when the host updates [OPTION_APPWIDGET_MIN_WIDTH] / max height.
 */
fun mergeAppWidgetSizeOptions(
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    minWidthDp: Int,
    minHeightDp: Int
): Bundle {
    val existing = appWidgetManager.getAppWidgetOptions(appWidgetId)
    return Bundle(existing).apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp)
    }
}

/** True if [existing] already has the same embedded min/max cell size hints as [merged]. */
fun embeddedWidgetSizeHintsMatch(existing: Bundle, merged: Bundle): Boolean {
    val keys = arrayOf(
        AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
        AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
        AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
        AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
    )
    for (k in keys) {
        if (existing.getInt(k, Int.MIN_VALUE) != merged.getInt(k, Int.MIN_VALUE)) {
            return false
        }
    }
    return true
}

/**
 * Temporary size used to break AppWidgetService identical-options no-ops.
 * Width is bumped by 1dp; height stays the same so the restore delta is a single axis.
 */
fun embeddedWidgetSizeNudgeDp(widthDp: Int, heightDp: Int): Pair<Int, Int> {
    val w = widthDp.coerceAtLeast(1)
    val h = heightDp.coerceAtLeast(1)
    return (w + 1) to h
}

/**
 * Writes a temporary nudged size hint, then restores [widthDp]×[heightDp].
 * Forces providers to receive [AppWidgetManager.updateAppWidgetOptions] even when the
 * target cell size already matches stored options (common after pre-createView setup).
 */
fun forceNotifyEmbeddedWidgetSizeOptions(
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    widthDp: Int,
    heightDp: Int,
) {
    val targetW = widthDp.coerceAtLeast(1)
    val targetH = heightDp.coerceAtLeast(1)
    val (nudgeW, nudgeH) = embeddedWidgetSizeNudgeDp(targetW, targetH)
    appWidgetManager.updateAppWidgetOptions(
        appWidgetId,
        mergeAppWidgetSizeOptions(appWidgetManager, appWidgetId, nudgeW, nudgeH),
    )
    appWidgetManager.updateAppWidgetOptions(
        appWidgetId,
        mergeAppWidgetSizeOptions(appWidgetManager, appWidgetId, targetW, targetH),
    )
}

/**
 * After [AppWidgetHost.createView], nudge options and ask the host view to re-measure so
 * RemoteViews stretch (default provider size laid out MATCH_PARENT) is replaced ASAP.
 */
fun refreshEmbeddedAppWidgetHostSize(
    hostView: AppWidgetHostView,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    widthDp: Int,
    heightDp: Int,
) {
    val w = widthDp.coerceAtLeast(1)
    val h = heightDp.coerceAtLeast(1)
    forceNotifyEmbeddedWidgetSizeOptions(appWidgetManager, appWidgetId, w, h)
    try {
        @Suppress("DEPRECATION")
        hostView.updateAppWidgetSize(/* newOptions = */ null, w, h, w, h)
    } catch (_: Exception) {
        // Options nudge above is the primary path; HostView size API can fail on some HU builds.
    }
    hostView.requestLayout()
    hostView.invalidate()
}
