package vad.dashing.tbox

const val SUNSHADE_POSITION_WIDGET_HORIZONTAL_DATA_KEY = "sunshadePositionWidgetHorizontal"
const val SUNSHADE_POSITION_WIDGET_VERTICAL_DATA_KEY = "sunshadePositionWidgetVertical"
const val SUNROOF_POSITION_WIDGET_HORIZONTAL_DATA_KEY = "sunroofPositionWidgetHorizontal"
const val SUNROOF_POSITION_WIDGET_VERTICAL_DATA_KEY = "sunroofPositionWidgetVertical"

val SHADE_ROOF_POSITION_STEPPER_WIDGET_DATA_KEYS: Set<String> = setOf(
    SUNSHADE_POSITION_WIDGET_HORIZONTAL_DATA_KEY,
    SUNSHADE_POSITION_WIDGET_VERTICAL_DATA_KEY,
    SUNROOF_POSITION_WIDGET_HORIZONTAL_DATA_KEY,
    SUNROOF_POSITION_WIDGET_VERTICAL_DATA_KEY,
)

fun isShadeRoofPositionWidgetDataKey(dataKey: String): Boolean =
    dataKey in SHADE_ROOF_POSITION_STEPPER_WIDGET_DATA_KEYS

fun isSunshadePositionWidgetDataKey(dataKey: String): Boolean =
    dataKey == SUNSHADE_POSITION_WIDGET_HORIZONTAL_DATA_KEY ||
        dataKey == SUNSHADE_POSITION_WIDGET_VERTICAL_DATA_KEY

fun isSunroofPositionWidgetDataKey(dataKey: String): Boolean =
    dataKey == SUNROOF_POSITION_WIDGET_HORIZONTAL_DATA_KEY ||
        dataKey == SUNROOF_POSITION_WIDGET_VERTICAL_DATA_KEY
