package vad.dashing.tbox.ui

import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.APP_LIST_WIDGET_DATA_KEY
import vad.dashing.tbox.AUTOMATION_TRIGGER_WIDGET_DATA_KEY
import vad.dashing.tbox.DAY_NIGHT_THEME_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.EMPTY_TILE_WIDGET_DATA_KEY
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.HTTP_REQUEST_WIDGET_DATA_KEY
import vad.dashing.tbox.utils.GEARBOX_MODE_CURRENT_GEAR_DATA_KEY
import vad.dashing.tbox.MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.MUSIC_COVER_WIDGET_DATA_KEY
import vad.dashing.tbox.MUSIC_SQUARE_WIDGET_DATA_KEY
import vad.dashing.tbox.MUSIC_WIDGET_DATA_KEY
import vad.dashing.tbox.PHONE_DIAL_WIDGET_DATA_KEY
import vad.dashing.tbox.ROAD_MATCH_MAP_WIDGET_DATA_KEY
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.valueToString

/** How the editor preview draws a tile (fake data, not live feeds). */
internal enum class WidgetPreviewKind {
    /** Title + formatted sample value (+ unit). */
    METRIC,
    /** Title + control chips (icon / toggle / stepper stand-in). */
    CONTROL,
    /** Title + short fixed label (time, gear letter, music, map…). */
    SPECIAL,
}

internal data class WidgetPreviewSample(
    val kind: WidgetPreviewKind,
    /** Main line under the title; empty for [WidgetPreviewKind.CONTROL]. */
    val valueText: String,
)

/**
 * Builds the fake value shown in the panel-editor preview.
 * [unit] is already localized; applied only when [FloatingDashboardWidgetConfig.showUnit] is true.
 */
internal fun widgetPreviewSample(
    dataKey: String,
    config: FloatingDashboardWidgetConfig,
    unit: String,
): WidgetPreviewSample {
    if (dataKey.isEmpty()) {
        return WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "")
    }
    specialSample(dataKey)?.let { return it }
    if (!WidgetsRepository.supportsValueAccuracy(dataKey)) {
        return WidgetPreviewSample(WidgetPreviewKind.CONTROL, "")
    }
    val (number, defaultAccuracy) = sampleMetricNumber(dataKey)
    val accuracy = config.valueAccuracy?.coerceIn(0, 2) ?: defaultAccuracy
    val formatted = valueToString(number, accuracy)
    val withUnit = if (config.showUnit && unit.isNotBlank()) {
        "$formatted\u2009${unit.replace("/", "\u2060/\u2060")}"
    } else {
        formatted
    }
    return WidgetPreviewSample(WidgetPreviewKind.METRIC, withUnit)
}

private fun specialSample(dataKey: String): WidgetPreviewSample? = when (dataKey) {
    "timeWidget" -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "12:34")
    "dateWidget" -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "03.11")
    "restartTbox" -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "↻")
    EMPTY_TILE_WIDGET_DATA_KEY -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "")
    GEARBOX_MODE_CURRENT_GEAR_DATA_KEY,
    "gearBoxCurrentGear",
    "gearBoxMode",
    -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "D")
    DAY_NIGHT_THEME_WIDGET_DATA_KEY -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "◐")
    DRIVE_MODE_WIDGET_DATA_KEY,
    DRIVE_MODE_CYCLE_WIDGET_DATA_KEY,
    -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "NORMAL")
    MUSIC_WIDGET_DATA_KEY,
    MUSIC_COVER_WIDGET_DATA_KEY,
    MUSIC_SQUARE_WIDGET_DATA_KEY,
    MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
    MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY,
    -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "♪")
    ROAD_MATCH_MAP_WIDGET_DATA_KEY -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "▦")
    APP_LAUNCHER_WIDGET_DATA_KEY,
    APP_LIST_WIDGET_DATA_KEY,
    -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "App")
    HTTP_REQUEST_WIDGET_DATA_KEY -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "HTTP")
    AUTOMATION_TRIGGER_WIDGET_DATA_KEY -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "▶")
    PHONE_DIAL_WIDGET_DATA_KEY -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "☎")
    WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "Ext")
    "netWidget",
    "netWidgetNew",
    "netWidgetColored",
    -> WidgetPreviewSample(WidgetPreviewKind.SPECIAL, "LTE")
    else -> null
}

/** Sample magnitude + default decimals for metric tiles. */
internal fun sampleMetricNumber(dataKey: String): Pair<Number, Int> {
    val key = dataKey.lowercase()
    return when {
        key.contains("rpm") -> 2150 to 0
        key.contains("speed") && !key.contains("limit") && !key.contains("cam") -> 87.0 to 1
        key.contains("temperature") || key.contains("temp") -> 22.5 to 1
        key.contains("pressure") -> 2.4 to 1
        key.contains("voltage") || key == "voltage" -> 12.6 to 1
        key.contains("odometer") -> 12345 to 0
        key.contains("fuellevel") || key.contains("fuel_level") -> 62.0 to 1
        key.contains("fuel") && (key.contains("liter") || key.contains("litre")) -> 28.5 to 1
        key.contains("consumption") -> 8.4 to 1
        key.contains("distance") || key.contains("range") -> 312 to 0
        key.contains("steer") -> 12.0 to 1
        key.contains("motorhours") || key.contains("motor_hours") -> 128.5 to 1
        key.contains("airquality") || key.contains("air_quality") -> 42 to 0
        key.contains("brightness") -> 7 to 0
        key.contains("volume") -> 12 to 0
        else -> 42.0 to 1
    }
}
