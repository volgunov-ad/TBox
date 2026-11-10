package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class CamdWindowWidgetTest {
    @Test
    fun openLineUsesScreenFractions() {
        assertEquals(
            "OPEN 4 0.0200 0.5500 0.4000 0.4000",
            camdWindowOpenLine(4, 2, 55, 40, 40),
        )
    }

    @Test
    fun openLineKeepsTheWindowOnScreen() {
        assertEquals(
            "OPEN 5 0.2000 0.0000 0.8000 0.4000",
            camdWindowOpenLine(5, 90, -10, 80, 40),
        )
    }

    @Test
    fun unknownCameraBecomesTheFrontCamera() {
        assertEquals("CLOSE 4", camdWindowCloseLine(99))
        assertEquals(4, normalizeCamdWindowCamera(2))
    }
}
