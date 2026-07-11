package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.R

class StepperAdjustIconStyleTest {

    @Test
    fun resolveStepperAdjustIconDrawableRes_plusMinus() {
        assertEquals(
            R.drawable.ic_media_volume_plus,
            resolveStepperAdjustIconDrawableRes(increase = true, isVertical = true, style = STEPPER_ADJUST_ICON_PLUS_MINUS),
        )
        assertEquals(
            R.drawable.ic_media_volume_minus,
            resolveStepperAdjustIconDrawableRes(increase = false, isVertical = false, style = STEPPER_ADJUST_ICON_PLUS_MINUS),
        )
    }

    @Test
    fun resolveStepperAdjustIconDrawableRes_arrowsFollowOrientation() {
        assertEquals(
            R.drawable.ic_stepper_arrow_up,
            resolveStepperAdjustIconDrawableRes(increase = true, isVertical = true, style = STEPPER_ADJUST_ICON_ARROWS),
        )
        assertEquals(
            R.drawable.ic_stepper_arrow_down,
            resolveStepperAdjustIconDrawableRes(increase = false, isVertical = true, style = STEPPER_ADJUST_ICON_ARROWS),
        )
        assertEquals(
            R.drawable.ic_stepper_arrow_right,
            resolveStepperAdjustIconDrawableRes(increase = true, isVertical = false, style = STEPPER_ADJUST_ICON_ARROWS),
        )
        assertEquals(
            R.drawable.ic_stepper_arrow_left,
            resolveStepperAdjustIconDrawableRes(increase = false, isVertical = false, style = STEPPER_ADJUST_ICON_ARROWS),
        )
    }

    @Test
    fun normalizeStepperAdjustIconStyle_unknownFallsBackToPlusMinus() {
        assertEquals(STEPPER_ADJUST_ICON_PLUS_MINUS, normalizeStepperAdjustIconStyle(99))
    }
}
