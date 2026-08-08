package vad.dashing.tbox.mbcan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unmapped VHAL binary toggles must not fall back to mbCAN 1↔2 write semantics (M2).
 */
class VhalBinaryToggleNoFallbackTest {

    @Test
    fun unmappedToggle_isNotVhalBinaryProperty_andEncodeIsNull() {
        val unmapped = MbCanKnownVehiclePropertyId.SYSTEM_REBOOT
        assertFalse(VhalBinaryToggleCodec.isVhalBinaryToggleProperty(unmapped))
        assertNull(VhalBinaryToggleCodec.encodeWriteValue(unmapped, targetOn = true))
        assertNull(VhalBinaryToggleCodec.encodeWriteValue(unmapped, targetOn = false))
    }

    @Test
    fun speedLimiter_unsupportedOnDashing_hasNoVhalToggleMap() {
        val id = MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH
        assertFalse(VhalBinaryToggleCodec.isVhalBinaryToggleProperty(id))
        assertNull(VhalBinaryToggleCodec.encodeWriteValue(id, true))
    }

    @Test
    fun mappedHvacToggle_isRecognized() {
        assertTrue(VhalBinaryToggleCodec.isVhalBinaryToggleProperty(MbCanKnownVehiclePropertyId.HVAC_POWER))
        assertTrue(VhalBinaryToggleCodec.encodeWriteValue(MbCanKnownVehiclePropertyId.HVAC_POWER, true) != null)
    }
}
