package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Chassis status decode helpers (stock CarSettings ConvertValue / CarCommon1).
 */
class ChassisToggleDecodeTest {

    @Test
    fun avhHdc_onWhenRawOneOrTwo() {
        assertEquals(MbCanBinaryState.On, MbCanSignalStateEngine.decodeAvhHdcStatusRaw(1))
        assertEquals(MbCanBinaryState.On, MbCanSignalStateEngine.decodeAvhHdcStatusRaw(2))
        assertEquals(MbCanBinaryState.Off, MbCanSignalStateEngine.decodeAvhHdcStatusRaw(0))
        assertEquals(MbCanBinaryState.Off, MbCanSignalStateEngine.decodeAvhHdcStatusRaw(3))
    }

    @Test
    fun espOff_onWhenRawTwo() {
        assertEquals(MbCanBinaryState.Off, MbCanSignalStateEngine.decodeEspOffStatusRaw(1))
        assertEquals(MbCanBinaryState.On, MbCanSignalStateEngine.decodeEspOffStatusRaw(2))
        assertEquals(MbCanBinaryState.Unknown, MbCanSignalStateEngine.decodeEspOffStatusRaw(0))
    }
}
