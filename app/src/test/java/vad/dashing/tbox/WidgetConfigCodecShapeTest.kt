package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigCodecShapeTest {

    @Test
    fun normalizeWidgetShape_clampsToAllowedRange() {
        assertEquals(0, normalizeWidgetShape(-5))
        assertEquals(15, normalizeWidgetShape(15))
        assertEquals(30, normalizeWidgetShape(99))
    }
}
