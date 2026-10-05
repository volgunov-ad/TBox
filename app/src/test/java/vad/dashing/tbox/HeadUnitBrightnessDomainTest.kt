package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadUnitBrightnessDomainTest {
    @Test
    fun `encodes UI levels to stock backlight steps`() {
        assertEquals(10, HeadUnitBrightnessDomain.encodeRawLevel(1))
        assertEquals(80, HeadUnitBrightnessDomain.encodeRawLevel(8))
        assertEquals(100, HeadUnitBrightnessDomain.encodeRawLevel(12))
    }

    @Test
    fun `decodes and rounds raw stock backlight`() {
        assertEquals(1, HeadUnitBrightnessDomain.decodeUiLevel(10))
        assertEquals(8, HeadUnitBrightnessDomain.decodeUiLevel(80))
        assertEquals(10, HeadUnitBrightnessDomain.decodeUiLevel(96))
        assertEquals(10, HeadUnitBrightnessDomain.decodeUiLevel(100))
    }
}
