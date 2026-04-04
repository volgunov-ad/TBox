package vad.dashing.tbox

/** Dashboard widget: shows an app icon and launches the app on tap. */
const val APP_LAUNCHER_WIDGET_DATA_KEY = "appLauncherWidget"

const val ACTIVE_TRIP_WIDGET_DATA_KEY = "activeTripWidget"
const val ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY = "activeTripWidgetSimple"

fun isActiveTripWidgetDataKey(dataKey: String): Boolean =
    dataKey == ACTIVE_TRIP_WIDGET_DATA_KEY || dataKey == ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY
