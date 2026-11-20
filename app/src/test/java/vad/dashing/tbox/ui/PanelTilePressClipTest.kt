package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelTilePressClipTest {

    @Test
    fun pressClipRadius_zeroStaysRectangular() {
        assertEquals(0, panelTilePressClipRadiusDp(0))
        assertEquals(0, panelTilePressClipRadiusDp(-3))
    }

    @Test
    fun pressClipRadius_matchesNormalizedWidgetShape() {
        assertEquals(12, panelTilePressClipRadiusDp(12))
        assertEquals(50, panelTilePressClipRadiusDp(50))
        assertEquals(50, panelTilePressClipRadiusDp(99))
    }
}
