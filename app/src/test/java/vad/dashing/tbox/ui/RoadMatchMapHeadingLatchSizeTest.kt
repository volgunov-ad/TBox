package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RoadMatchMapHeadingLatchSizeTest {

    @Test
    fun defaultScaleKeeps18dpWhenUnitIsSmaller() {
        assertEquals(18f, headingUpLatchIconDp(unitFontSizeDp = 10f, textScale = 1f), 0.01f)
        assertEquals(18f, headingUpLatchIconDp(unitFontSizeDp = 14f, textScale = 1f), 0.01f)
        assertEquals(18f, headingUpLatchIconDp(unitFontSizeDp = 18f, textScale = 1f), 0.01f)
    }

    @Test
    fun largeTileUsesUnitWhenBiggerThanFloor() {
        assertEquals(26f, headingUpLatchIconDp(unitFontSizeDp = 26f, textScale = 1f), 0.01f)
    }

    @Test
    fun tileScaleRaisesTheFloor() {
        assertEquals(27f, headingUpLatchIconDp(unitFontSizeDp = 14f, textScale = 1.5f), 0.01f)
        assertEquals(30f, headingUpLatchIconDp(unitFontSizeDp = 30f, textScale = 1.5f), 0.01f)
    }
}
