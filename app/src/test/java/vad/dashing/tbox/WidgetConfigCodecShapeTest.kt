package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigCodecShapeTest {

    @Test
    fun normalizeWidgetShape_clampsToAllowedRange() {
        assertEquals(0, normalizeWidgetShape(-5))
        assertEquals(15, normalizeWidgetShape(15))
        assertEquals(50, normalizeWidgetShape(99))
    }

    @Test
    fun normalizeWidgetElevation_clampsToAllowedRange() {
        assertEquals(0, normalizeWidgetElevation(-3))
        assertEquals(5, normalizeWidgetElevation(5))
        assertEquals(10, normalizeWidgetElevation(100))
    }
}
