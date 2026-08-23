package vad.dashing.tbox

/**
 * True when an embedded app-widget cell has a real measured size in dp.
 * [android.appwidget.AppWidgetHost.createView] should wait for this so RemoteViews
 * inflate to the tile instead of a default size that later stretches.
 */
fun isExternalAppWidgetCellReady(widthDp: Int, heightDp: Int): Boolean =
    widthDp > 0 && heightDp > 0
