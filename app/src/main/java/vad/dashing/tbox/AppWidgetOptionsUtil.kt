package vad.dashing.tbox

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
