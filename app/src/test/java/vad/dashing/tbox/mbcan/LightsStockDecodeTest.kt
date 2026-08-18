package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.HeadlightMode

class LightsStockDecodeTest {

    @Test
    fun lightControl_acceptsOneThroughFour() {
        assertEquals(1, MbCanSignalStateEngine.decodeLightControlRaw(1))
        assertEquals(2, MbCanSignalStateEngine.decodeLightControlRaw(2))
        assertEquals(3, MbCanSignalStateEngine.decodeLightControlRaw(3))
        assertEquals(4, MbCanSignalStateEngine.decodeLightControlRaw(4))
        assertNull(MbCanSignalStateEngine.decodeLightControlRaw(0))
        assertNull(MbCanSignalStateEngine.decodeLightControlRaw(5))
    }

    @Test
    fun headlightMode_cyclesAutoParkLowOff() {
        assertEquals(HeadlightMode.Auto, HeadlightMode.nextInCycle(null))
        assertEquals(HeadlightMode.Park, HeadlightMode.nextInCycle(1))
        assertEquals(HeadlightMode.Low, HeadlightMode.nextInCycle(2))
        assertEquals(HeadlightMode.Off, HeadlightMode.nextInCycle(3))
        assertEquals(HeadlightMode.Auto, HeadlightMode.nextInCycle(4))
        assertEquals(HeadlightMode.Auto, HeadlightMode.nextInCycle(99))
    }

    @Test
    fun rearFog_mbCanOneOffTwoOn() {
        assertEquals(MbCanBinaryState.Off, MbCanSignalStateEngine.decodeRearFogMbCanRaw(1))
        assertEquals(MbCanBinaryState.On, MbCanSignalStateEngine.decodeRearFogMbCanRaw(2))
        assertEquals(MbCanBinaryState.Unknown, MbCanSignalStateEngine.decodeRearFogMbCanRaw(0))
    }
}
