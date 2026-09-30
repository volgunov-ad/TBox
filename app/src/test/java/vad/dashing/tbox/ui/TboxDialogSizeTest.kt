package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TboxDialogSizeTest {
    @Test
    fun largeAndCompactWidthFractions() {
        assertEquals(0.94f, TboxDialogSize.WidthFraction)
        assertEquals(0.6f, TboxDialogSize.CompactWidthFraction)
        assertTrue(TboxDialogSize.CompactWidthFraction < TboxDialogSize.WidthFraction)
    }

    @Test
    fun heightCapIsNearFullScreen() {
        assertEquals(0.92f, TboxDialogSize.HeightFraction)
        assertTrue(TboxDialogSize.HeightFraction in 0.8f..0.95f)
    }

    @Test
    fun scrollBodyChromeReserveIsPositive() {
        assertTrue(TboxDialogSize.ScrollBodyChromeReserveDp > 0)
        assertTrue(TboxDialogSize.ScrollBodyChromeReserveDp < 300)
    }
}
