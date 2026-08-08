package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A9 mbCAN vs A10 VHAL encodings for AC clean when locked ([HVAC_BLOWER_DELAY]).
 */
class HvacAcCleanWhenLockedCodecTest {

    @Test
    fun mbCanDecode_twoOnOneOff() {
        assertEquals(MbCanBinaryState.On, MbCanSignalStateEngine.decodeHvacBlowerDelayMbCanRaw(2))
        assertEquals(MbCanBinaryState.Off, MbCanSignalStateEngine.decodeHvacBlowerDelayMbCanRaw(1))
        assertEquals(MbCanBinaryState.Unknown, MbCanSignalStateEngine.decodeHvacBlowerDelayMbCanRaw(0))
    }

    @Test
    fun mbCanRegistry_toggleUsesTwoOnOneOff() {
        val spec = MbCanCommandRegistry.get(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY)
        assertTrue(spec != null)
        val policy = spec!!.policy as MbCanCommandPolicy.ToggleBinary
        assertEquals(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY_VALUE_ON, policy.onValue)
        assertEquals(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY_VALUE_OFF, policy.offValue)
        assertEquals(MbCanSignal.HvacAcCleanWhenLocked, spec.refreshSignal)
    }

    @Test
    fun vhalWrite_oneOnTwoOff() {
        assertEquals(1, VhalBinaryToggleCodec.encodeWriteValue(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY, true))
        assertEquals(2, VhalBinaryToggleCodec.encodeWriteValue(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY, false))
    }
}
