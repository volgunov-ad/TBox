package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Test

class VhalMirrorAutoFoldCodecTest {
    @Test
    fun `A10 mirror auto-fold reads zero as on`() {
        val property = MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW
        assertEquals(MbCanBinaryState.On, VhalBinaryToggleCodec.decodeReadState(property, 0))
        assertEquals(MbCanBinaryState.Off, VhalBinaryToggleCodec.decodeReadState(property, 1))
    }

    @Test
    fun `A10 mirror auto-fold writes one on and two off`() {
        val property = MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW
        assertEquals(1, VhalBinaryToggleCodec.encodeWriteValue(property, true))
        assertEquals(2, VhalBinaryToggleCodec.encodeWriteValue(property, false))
    }
}
