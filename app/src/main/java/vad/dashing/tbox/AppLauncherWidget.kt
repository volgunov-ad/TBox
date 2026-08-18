package vad.dashing.tbox

import androidx.annotation.StringRes

/** Dashboard widget: shows an app icon and launches the app on tap. */
const val APP_LAUNCHER_WIDGET_DATA_KEY = "appLauncherWidget"

/**
 * How an [APP_LAUNCHER_WIDGET_DATA_KEY] tile starts the target app.
 *
 * [STOCK_WINDOW] uses Adayo A10 launcher ActivityView (`com.adayo.launcher.LAUNCH_APP`).
 */
enum class AppLauncherLaunchMode(val storageKey: String, @StringRes val labelRes: Int) {
    FULLSCREEN("fullscreen", R.string.widget_app_launcher_mode_fullscreen),
    FREEFORM("freeform", R.string.widget_app_launcher_mode_freeform),
    STOCK_WINDOW("stock_window", R.string.widget_app_launcher_mode_stock_window);

    companion object {
        val DEFAULT: AppLauncherLaunchMode = FULLSCREEN

        fun fromStorageKey(key: String?): AppLauncherLaunchMode {
            val normalized = key?.trim().orEmpty()
            return entries.firstOrNull { it.storageKey.equals(normalized, ignoreCase = true) }
                ?: DEFAULT
        }

        /** Prefer explicit mode; fall back to legacy [launcherFreeformEnabled]. */
        fun fromStored(
            launchModeKey: String?,
            freeformEnabled: Boolean,
        ): AppLauncherLaunchMode {
            val fromKey = launchModeKey?.trim().orEmpty()
            if (fromKey.isNotEmpty()) {
                return fromStorageKey(fromKey)
            }
            return if (freeformEnabled) FREEFORM else FULLSCREEN
        }
    }
}

/** Dashboard widget: shows a custom icon and sends a configured HTTP request on tap. */
const val HTTP_REQUEST_WIDGET_DATA_KEY = "httpRequestWidget"

const val ACTIVE_TRIP_WIDGET_DATA_KEY = "activeTripWidget"
const val ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY = "activeTripWidgetSimple"
const val ACTIVE_TRIP_WIDGET_MINI_DATA_KEY = "activeTripWidgetMini"
const val ACTIVE_TRIP_WIDGET_CUSTOM_DATA_KEY = "activeTripWidgetCustom"
const val GEOPOSITION_DATA_WIDGET_DATA_KEY = "geopositionDataWidget"
/** Phase F2a: local Canvas road-match view (no MapKit / network). */
const val ROAD_MATCH_MAP_WIDGET_DATA_KEY = "roadMatchMapWidget"

fun isRoadMatchMapWidgetDataKey(dataKey: String): Boolean =
    dataKey == ROAD_MATCH_MAP_WIDGET_DATA_KEY

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
