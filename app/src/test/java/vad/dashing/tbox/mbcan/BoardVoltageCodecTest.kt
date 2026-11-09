package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardVoltageCodecTest {
    @Test
    fun decodesCallbackFrameAsTenthsOfAVolt() {
        val payload = byteArrayOf(0x83.toByte(), 0x05, 0x76, 0x00, 0x00, 0x76)
        assertEquals(11.8f, BoardVoltageCodec.decode(payload)!!, 0.001f)
    }

    @Test
    fun ignoresOtherDevStatusFrames() {
        val powerStatus = byteArrayOf(0x83.toByte(), 0x00, 0xC0.toByte(), 0x00, 0x00, 0x00)
        assertNull(BoardVoltageCodec.decode(powerStatus))
    }

    @Test
    fun rejectsZeroAndOutOfRange() {
        assertNull(BoardVoltageCodec.decode(byteArrayOf(0x83.toByte(), 0x05, 0x00, 0x00)))
        assertNull(BoardVoltageCodec.decode(byteArrayOf(0x83.toByte(), 0x05, 0x00, 0xFF.toByte())))
        assertNull(BoardVoltageCodec.decode(byteArrayOf(0x83.toByte(), 0x05)))
    }

    @Test
    fun honorsDeclaredLength() {
        val payload = byteArrayOf(0x83.toByte(), 0x05, 0x7A, 0x00, 0x11)
        assertNull(BoardVoltageCodec.decode(payload, length = 3))
        assertEquals(12.2f, BoardVoltageCodec.decode(payload, length = 4)!!, 0.001f)
    }
}
