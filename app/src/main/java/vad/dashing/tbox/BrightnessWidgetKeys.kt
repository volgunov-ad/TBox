package vad.dashing.tbox

const val HU_SCREEN_BRIGHTNESS_WIDGET_HORIZONTAL_DATA_KEY = "huScreenBrightnessWidgetHorizontal"
const val HU_SCREEN_BRIGHTNESS_WIDGET_VERTICAL_DATA_KEY = "huScreenBrightnessWidgetVertical"
const val ICM_BRIGHTNESS_WIDGET_HORIZONTAL_DATA_KEY = "icmBrightnessWidgetHorizontal"
const val ICM_BRIGHTNESS_WIDGET_VERTICAL_DATA_KEY = "icmBrightnessWidgetVertical"
const val HUD_BRIGHTNESS_WIDGET_HORIZONTAL_DATA_KEY = "hudBrightnessWidgetHorizontal"
const val HUD_BRIGHTNESS_WIDGET_VERTICAL_DATA_KEY = "hudBrightnessWidgetVertical"

val BRIGHTNESS_STEPPER_WIDGET_DATA_KEYS: Set<String> = setOf(
    HU_SCREEN_BRIGHTNESS_WIDGET_HORIZONTAL_DATA_KEY,
    HU_SCREEN_BRIGHTNESS_WIDGET_VERTICAL_DATA_KEY,
    ICM_BRIGHTNESS_WIDGET_HORIZONTAL_DATA_KEY,
    ICM_BRIGHTNESS_WIDGET_VERTICAL_DATA_KEY,
    HUD_BRIGHTNESS_WIDGET_HORIZONTAL_DATA_KEY,
    HUD_BRIGHTNESS_WIDGET_VERTICAL_DATA_KEY,
)

fun isBrightnessStepperWidgetDataKey(dataKey: String): Boolean =
    dataKey in BRIGHTNESS_STEPPER_WIDGET_DATA_KEYS

fun isHuScreenBrightnessWidgetDataKey(dataKey: String): Boolean =
    dataKey == HU_SCREEN_BRIGHTNESS_WIDGET_HORIZONTAL_DATA_KEY ||
        dataKey == HU_SCREEN_BRIGHTNESS_WIDGET_VERTICAL_DATA_KEY

fun isIcmBrightnessWidgetDataKey(dataKey: String): Boolean =
    dataKey == ICM_BRIGHTNESS_WIDGET_HORIZONTAL_DATA_KEY ||
        dataKey == ICM_BRIGHTNESS_WIDGET_VERTICAL_DATA_KEY

fun isHudBrightnessWidgetDataKey(dataKey: String): Boolean =
    dataKey == HUD_BRIGHTNESS_WIDGET_HORIZONTAL_DATA_KEY ||
        dataKey == HUD_BRIGHTNESS_WIDGET_VERTICAL_DATA_KEY
