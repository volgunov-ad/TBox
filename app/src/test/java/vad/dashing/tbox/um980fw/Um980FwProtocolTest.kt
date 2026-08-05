package vad.dashing.tbox.um980fw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Um980PkgValidatorTest {
    @Test
    fun acceptsKnownMagic() {
        val magic = byteArrayOf(0xA5.toByte(), 0xA4.toByte(), 0xA3.toByte(), 0xA2.toByte())
        assertNull(Um980PkgValidator.validate(3004096L, magic))
    }

    @Test
    fun rejectsBadMagic() {
        assertEquals("bad_magic", Um980PkgValidator.validate(100_000L, byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun buildFromFileName() {
        assertEquals(25102, Um980PkgValidator.buildFromFileName("UM980_R4.10Build25102.pkg"))
    }

    @Test
    fun buildFromVersionA() {
        val line =
            "#VERSIONA,97,GPS,FINE,2430,313482500,25102,0,18,229;" +
                "\"UM980\",\"R4.10Build25102\",\"HRPT00-S10C-P\"*1ad470b2"
        assertEquals(25102, Um980PkgValidator.buildFromVersionA(line))
    }
}

class Xmodem1kTest {
    @Test
    fun checksumFrameLayout() {
        val payload = byteArrayOf(0xA5.toByte(), 0xA4.toByte(), 0xA3.toByte(), 0xA2.toByte())
        val frame = Xmodem1k.buildBlock(1, payload, Xmodem1k.CheckMode.CHECKSUM)
        assertEquals(Xmodem1k.STX, frame[0])
        assertEquals(1.toByte(), frame[1])
        assertEquals(0xFE.toByte(), frame[2])
        assertEquals(payload[0], frame[3])
        assertEquals(3 + 1024 + 1, frame.size)
        val data = frame.copyOfRange(3, 3 + 1024)
        assertEquals(Xmodem1k.checksum(data).toByte(), frame.last())
    }

    @Test
    fun crc16KnownVector() {
        // Empty 1024 zeros → CRC 0
        val zeros = ByteArray(1024)
        assertEquals(0, Xmodem1k.crc16(zeros))
        val frame = Xmodem1k.buildBlock(1, ByteArray(0), Xmodem1k.CheckMode.CRC16)
        assertEquals(3 + 1024 + 2, frame.size)
        assertTrue(frame[0] == Xmodem1k.STX)
    }
}
