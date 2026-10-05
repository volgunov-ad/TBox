package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppWidgetOptionsUtilTest {

    @Test
    fun nudgeBumpsWidthByOneDp() {
        assertEquals(121 to 80, embeddedWidgetSizeNudgeDp(120, 80))
    }

    @Test
    fun nudgeCoercesNonPositiveSidesToAtLeastOne() {
        // width coerced to 1, then bumped → 2; height coerced to 1
        assertEquals(2 to 1, embeddedWidgetSizeNudgeDp(0, 0))
        assertEquals(2 to 1, embeddedWidgetSizeNudgeDp(1, 0))
        assertEquals(2 to 50, embeddedWidgetSizeNudgeDp(-5, 50))
    }

    @Test
    fun nudgeDiffersFromTargetSoIdenticalOptionsNoOpIsBroken() {
        val width = 180
        val height = 96
        val (nudgeW, nudgeH) = embeddedWidgetSizeNudgeDp(width, height)
        assertFalse(nudgeW == width && nudgeH == height)
        assertTrue(nudgeW > width || nudgeH != height)
        assertEquals(height, nudgeH)
        assertEquals(width + 1, nudgeW)
    }
}
