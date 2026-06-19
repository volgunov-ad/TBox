package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorThemeSegmentTest {

    @Test
    fun colorThemeSegmentFor_mapsLightAndDarkAppTheme() {
        assertEquals(0, colorThemeSegmentFor(1))
        assertEquals(1, colorThemeSegmentFor(2))
    }
}
