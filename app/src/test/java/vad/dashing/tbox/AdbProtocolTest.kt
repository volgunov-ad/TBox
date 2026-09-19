package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import vad.dashing.tbox.adb.AdbProtocol
import vad.dashing.tbox.adb.AdbShellV2
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbProtocolTest {

    @Test
    fun commandConstantsSpellAsciiNames() {
        assertEquals("SYNC", AdbProtocol.commandName(AdbProtocol.CMD_SYNC))
        assertEquals("CNXN", AdbProtocol.commandName(AdbProtocol.CMD_CNXN))
        assertEquals("AUTH", AdbProtocol.commandName(AdbProtocol.CMD_AUTH))
        assertEquals("OPEN", AdbProtocol.commandName(AdbProtocol.CMD_OPEN))
        assertEquals("OKAY", AdbProtocol.commandName(AdbProtocol.CMD_OKAY))
        assertEquals("CLSE", AdbProtocol.commandName(AdbProtocol.CMD_CLSE))
        assertEquals("WRTE", AdbProtocol.commandName(AdbProtocol.CMD_WRTE))
    }

    @Test
    fun commandName_nonPrintable_fallsBackToHex() {
        assertEquals("0x00000000", AdbProtocol.commandName(0))
        assertEquals("0xFFFFFFFF", AdbProtocol.commandName(-1))
    }

    @Test
    fun encodedHeader_wireLayoutLittleEndian() {
        val packet = AdbProtocol.encode(AdbProtocol.CMD_CNXN, AdbProtocol.VERSION, 4096)
        assertEquals(24, packet.size)
        assertEquals("CNXN", String(packet, 0, 4, Charsets.US_ASCII))
        assertEquals(1, packet[4].toInt() and 0xFF)
        assertEquals(0, packet[5].toInt() and 0xFF)
        assertEquals(0, packet[6].toInt() and 0xFF)
        assertEquals(1, packet[7].toInt() and 0xFF)
        assertEquals(0, packet[8].toInt() and 0xFF)
        assertEquals(0x10, packet[9].toInt() and 0xFF)
        assertEquals(0, packet[10].toInt() and 0xFF)
        assertEquals(0, packet[11].toInt() and 0xFF)
        assertEquals(0, packet[12].toInt() and 0xFF)
        assertEquals(0xBC, packet[20].toInt() and 0xFF)
        assertEquals(0xB1, packet[21].toInt() and 0xFF)
        assertEquals(0xA7, packet[22].toInt() and 0xFF)
        assertEquals(0xB1, packet[23].toInt() and 0xFF)
    }

    @Test
    fun decodeHeader_roundTrip() {
        val payload = "host::features=shell_v2\u0000".toByteArray()
        val packet = AdbProtocol.encode(AdbProtocol.CMD_CNXN, AdbProtocol.VERSION, 4096, payload)
        val header = AdbProtocol.decodeHeader(packet)
        assertEquals(AdbProtocol.CMD_CNXN, header.command)
        assertEquals(AdbProtocol.VERSION, header.arg0)
        assertEquals(4096, header.arg1)
        assertEquals(payload.size, header.dataLength)
        assertEquals(AdbProtocol.checksum(payload), header.dataCheck)
        assertEquals(AdbProtocol.magic(header.command), header.magic)
        assertTrue(AdbProtocol.verifyPayload(header, payload))
        assertEquals(
            payload.size + AdbProtocol.HEADER_SIZE,
            AdbProtocol.encode(AdbProtocol.CMD_WRTE, 1, 2, payload).size,
        )
    }

    @Test
    fun checksum_knownVector() {
        assertEquals(0x1DD, AdbProtocol.checksum("123456789".toByteArray()))
        assertEquals(0, AdbProtocol.checksum(ByteArray(0)))
    }

    @Test
    fun encode_canSkipChecksum() {
        val payload = "device::features=shell_v2".toByteArray()
        val packet = AdbProtocol.encode(
            AdbProtocol.CMD_CNXN,
            AdbProtocol.VERSION,
            4096,
            payload,
            useChecksum = false,
        )
        val header = AdbProtocol.decodeHeader(packet)
        assertEquals(0, header.dataCheck)
        assertFalse(AdbProtocol.verifyPayload(header, payload))
    }

    @Test
    fun decodeHeader_rejectsBadMagic() {
        val packet = AdbProtocol.encode(AdbProtocol.CMD_OKAY, 1, 2)
        packet[23] = (packet[23].toInt() + 1).toByte()
        try {
            AdbProtocol.decodeHeader(packet)
            fail("expected IOException")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun decodeHeader_rejectsOversizedDataLength() {
        val buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(AdbProtocol.CMD_WRTE)
        buffer.putInt(1)
        buffer.putInt(2)
        buffer.putInt(AdbProtocol.MAX_INBOUND_PAYLOAD + 1)
        buffer.putInt(0)
        buffer.putInt(AdbProtocol.magic(AdbProtocol.CMD_WRTE))
        try {
            AdbProtocol.decodeHeader(buffer.array())
            fail("expected IOException")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun decodeHeader_rejectsShortInput() {
        try {
            AdbProtocol.decodeHeader(ByteArray(10))
            fail("expected IOException")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun verifyPayload_mismatchFails() {
        val header = AdbProtocol.decodeHeader(AdbProtocol.encode(AdbProtocol.CMD_WRTE, 1, 2, "abc".toByteArray()))
        assertFalse(AdbProtocol.verifyPayload(header, "abd".toByteArray()))
        assertFalse(AdbProtocol.verifyPayload(header, "abcd".toByteArray()))
    }

    @Test
    fun shellServiceStrings() {
        assertEquals("shell,v2:getprop ro.product.model\u0000", AdbShellV2.service("getprop ro.product.model"))
        assertEquals("shell:getprop ro.product.model\u0000", AdbShellV2.legacyService("getprop ro.product.model"))
    }
}
