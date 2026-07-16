package vad.dashing.tbox

import vad.dashing.tbox.R

/** Plus/minus icons for stepper decrease/increase controls. */
const val STEPPER_ADJUST_ICON_PLUS_MINUS = 0

/** Directional arrows (up/down or left/right depending on widget orientation). */
const val STEPPER_ADJUST_ICON_ARROWS = 1

val STEPPER_WIDGET_DATA_KEYS: Set<String> = setOf(
    MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY,
    MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY,
    HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY,
    HVAC_FAN_WIDGET_VERTICAL_DATA_KEY,
    HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY,
    HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY,
    HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY,
    HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY,
    SPEED_LIMITER_WIDGET_DATA_KEY,
)

fun isStepperWidgetDataKey(dataKey: String): Boolean = dataKey in STEPPER_WIDGET_DATA_KEYS

fun normalizeStepperAdjustIconStyle(raw: Int): Int =
    if (raw == STEPPER_ADJUST_ICON_ARROWS) STEPPER_ADJUST_ICON_ARROWS else STEPPER_ADJUST_ICON_PLUS_MINUS

fun resolveStepperAdjustIconDrawableRes(
    increase: Boolean,
    isVertical: Boolean,
    style: Int,
): Int = when (normalizeStepperAdjustIconStyle(style)) {
    STEPPER_ADJUST_ICON_ARROWS -> when {
        isVertical && increase -> R.drawable.ic_stepper_arrow_up
        isVertical && !increase -> R.drawable.ic_stepper_arrow_down
        !isVertical && increase -> R.drawable.ic_stepper_arrow_right
        else -> R.drawable.ic_stepper_arrow_left
    }
    else -> if (increase) {
        R.drawable.ic_media_volume_plus
    } else {
        R.drawable.ic_media_volume_minus
    }
}
