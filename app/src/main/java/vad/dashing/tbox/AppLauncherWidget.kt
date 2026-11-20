package vad.dashing.tbox

import androidx.annotation.StringRes

/** Dashboard widget: shows an app icon and launches the app on tap. */
const val APP_LAUNCHER_WIDGET_DATA_KEY = "appLauncherWidget"

/**
 * How an app-shortcut or music-control tile starts the target app.
 *
 * [FULLSCREEN] is the picker label «Обычный»: raw `startActivity`, or DCL `app_area`
 * when DCL environment mode is on.
 * [DCL_FULL_WIDTH] is opt-in «На всю ширину (DCL)» → DCL `launchApp(mode=full_width)`
 * (bars visible, ADAS hidden, not immersive).
 * [DCL_FULLSCREEN] is opt-in «На весь экран (DCL)» → DCL `launchApp(mode=fullscreen)`.
 * [STOCK_WINDOW] uses Adayo A10 launcher ActivityView (`com.adayo.launcher.LAUNCH_APP`).
 * [VIRTUAL_DISPLAY] starts the app on a chosen HU display via localhost ADB
 * (`am start --display`).
 */
enum class AppLauncherLaunchMode(val storageKey: String, @StringRes val labelRes: Int) {
    FULLSCREEN("fullscreen", R.string.widget_app_launcher_mode_fullscreen),
    DCL_FULL_WIDTH("dcl_full_width", R.string.widget_app_launcher_mode_dcl_full_width),
    DCL_FULLSCREEN("dcl_fullscreen", R.string.widget_app_launcher_mode_dcl_fullscreen),
    FREEFORM("freeform", R.string.widget_app_launcher_mode_freeform),
    STOCK_WINDOW("stock_window", R.string.widget_app_launcher_mode_stock_window),
    VIRTUAL_DISPLAY("virtual_display", R.string.widget_app_launcher_mode_virtual_display);

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

/**
 * True when the tile persists app-shortcut launch fields ([FloatingDashboardWidgetConfig.launcherAppPackage]
 * and launch-mode / freeform / virtual-display settings).
 */
fun widgetStoresAppLauncherLaunchSettings(
    dataKey: String,
    httpLaunchAppBeforeRequest: Boolean = false,
): Boolean =
    usesAppLauncherLaunchModeFields(dataKey) ||
        (dataKey == HTTP_REQUEST_WIDGET_DATA_KEY && httpLaunchAppBeforeRequest)

const val ACTIVE_TRIP_WIDGET_DATA_KEY = "activeTripWidget"
const val ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY = "activeTripWidgetSimple"
const val ACTIVE_TRIP_WIDGET_MINI_DATA_KEY = "activeTripWidgetMini"
const val ACTIVE_TRIP_WIDGET_CUSTOM_DATA_KEY = "activeTripWidgetCustom"
/** Single trip metric (distance, avg speed, …) with current/daily source. */
const val TRIP_METRIC_WIDGET_DATA_KEY = "tripMetricWidget"
/** Single OBD-II Mode 01 (or ATRV) value from a bonded ELM327 adapter. */
const val OBD_METRIC_WIDGET_DATA_KEY = "obdMetricWidget"
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

fun isTripMetricWidgetDataKey(dataKey: String): Boolean =
    dataKey == TRIP_METRIC_WIDGET_DATA_KEY

fun isObdMetricWidgetDataKey(dataKey: String): Boolean =
    dataKey == OBD_METRIC_WIDGET_DATA_KEY

/** Widgets that use [FloatingDashboardWidgetConfig.tripWidgetSource]. */
fun usesTripWidgetSource(dataKey: String): Boolean =
    isActiveTripWidgetDataKey(dataKey) || isTripMetricWidgetDataKey(dataKey)

/** [FloatingDashboardWidgetConfig.tripWidgetSource]: show current (or last finished) trip. */
const val TRIP_WIDGET_SOURCE_CURRENT = 0

/** [FloatingDashboardWidgetConfig.tripWidgetSource]: show live persistent (daily) trip. */
const val TRIP_WIDGET_SOURCE_PERSISTENT = 1

fun normalizeTripWidgetSource(raw: Int): Int =
    if (raw == TRIP_WIDGET_SOURCE_PERSISTENT) TRIP_WIDGET_SOURCE_PERSISTENT
    else TRIP_WIDGET_SOURCE_CURRENT
