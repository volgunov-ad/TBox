package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.WiperOperatingMode
import vad.dashing.tbox.mbcan.WiperStsDomain

class WiperStsDomainTest {
    @Test
    fun decode_ttgScale() {
        assertEquals(WiperOperatingMode.Off, WiperStsDomain.decode(0))
        assertEquals(WiperOperatingMode.Intermittent, WiperStsDomain.decode(1))
        assertEquals(WiperOperatingMode.Low, WiperStsDomain.decode(2))
        assertEquals(WiperOperatingMode.High, WiperStsDomain.decode(3))
    }

    @Test
    fun decode_unknownOutsideTtgRange() {
        assertNull(WiperStsDomain.decode(-1))
        assertNull(WiperStsDomain.decode(4))
        assertNull(WiperStsDomain.decode(5))
        assertNull(WiperStsDomain.decode(255))
    }
}
