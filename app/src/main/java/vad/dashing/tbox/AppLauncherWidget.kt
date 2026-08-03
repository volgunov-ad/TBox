package vad.dashing.tbox

/** Dashboard widget: shows an app icon and launches the app on tap. */
const val APP_LAUNCHER_WIDGET_DATA_KEY = "appLauncherWidget"

/** Dashboard widget: shows a custom icon and sends a configured HTTP request on tap. */
const val HTTP_REQUEST_WIDGET_DATA_KEY = "httpRequestWidget"

const val ACTIVE_TRIP_WIDGET_DATA_KEY = "activeTripWidget"
const val ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY = "activeTripWidgetSimple"
const val ACTIVE_TRIP_WIDGET_MINI_DATA_KEY = "activeTripWidgetMini"
const val ACTIVE_TRIP_WIDGET_CUSTOM_DATA_KEY = "activeTripWidgetCustom"
const val GEOPOSITION_DATA_WIDGET_DATA_KEY = "geopositionDataWidget"

fun isActiveTripWidgetDataKey(dataKey: String): Boolean =
    dataKey == ACTIVE_TRIP_WIDGET_DATA_KEY ||
        dataKey == ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY ||
        dataKey == ACTIVE_TRIP_WIDGET_MINI_DATA_KEY ||
        dataKey == ACTIVE_TRIP_WIDGET_CUSTOM_DATA_KEY

/** [FloatingDashboardWidgetConfig.tripWidgetSource]: show current (or last finished) trip. */
const val TRIP_WIDGET_SOURCE_CURRENT = 0

/** [FloatingDashboardWidgetConfig.tripWidgetSource]: show live persistent (daily) trip. */
const val TRIP_WIDGET_SOURCE_PERSISTENT = 1

fun normalizeTripWidgetSource(raw: Int): Int =
    if (raw == TRIP_WIDGET_SOURCE_PERSISTENT) TRIP_WIDGET_SOURCE_PERSISTENT
    else TRIP_WIDGET_SOURCE_CURRENT
