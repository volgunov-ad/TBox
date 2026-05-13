package vad.dashing.tbox

/** Dashboard widget: shows an app icon and launches the app on tap. */
const val APP_LAUNCHER_WIDGET_DATA_KEY = "appLauncherWidget"

/** Several launchable apps in one tile (icon grid); each tap starts that app full-screen. */
const val APP_GRID_LAUNCHER_WIDGET_DATA_KEY = "appGridLauncherWidget"

const val APP_GRID_LAUNCHER_MAX_SLOTS = 12

const val ACTIVE_TRIP_WIDGET_DATA_KEY = "activeTripWidget"
const val ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY = "activeTripWidgetSimple"
const val ACTIVE_TRIP_WIDGET_MINI_DATA_KEY = "activeTripWidgetMini"

fun isActiveTripWidgetDataKey(dataKey: String): Boolean =
    dataKey == ACTIVE_TRIP_WIDGET_DATA_KEY ||
        dataKey == ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY ||
        dataKey == ACTIVE_TRIP_WIDGET_MINI_DATA_KEY

fun normalizeAppGridPackages(packages: Collection<String>): List<String> =
    packages.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(APP_GRID_LAUNCHER_MAX_SLOTS)
        .toList()
