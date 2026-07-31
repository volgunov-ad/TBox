package vad.dashing.tbox.usbgnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbUartBridgeInitTest {
    @Test
    fun cp210xBaudLittleEndian() {
        val bytes = UsbUartBridgeInit.encodeCp210xBaud(57_600)
        assertEquals(4, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0xE1.toByte(), bytes[1])
        assertEquals(0x00.toByte(), bytes[2])
        assertEquals(0x00.toByte(), bytes[3])
        // 0x0000E100 == 57600
        val decoded = (bytes[0].toInt() and 0xff) or
            ((bytes[1].toInt() and 0xff) shl 8) or
            ((bytes[2].toInt() and 0xff) shl 16) or
            ((bytes[3].toInt() and 0xff) shl 24)
        assertEquals(57_600, decoded)
    }

    @Test
    fun ch34xBaudRegistersFor115200() {
        val (v1, v2) = UsbUartBridgeInit.encodeCh34xBaudRegisters(115_200)
        assertTrue(v1 != 0)
        assertTrue(v2 in 0..0xff)
    }

    @Test
    fun nmeaEnableCommandsDefaultEmpty() {
        assertTrue(UsbGnssNmeaEnableCommands.buildUnicoreLines(false, false).isEmpty())
        assertEquals(
            listOf("GPVTG 1"),
            UsbGnssNmeaEnableCommands.buildUnicoreLines(true, false),
        )
        assertEquals(
            listOf("GPZDA 1"),
            UsbGnssNmeaEnableCommands.buildUnicoreLines(false, true),
        )
    }
}

class UsbGnssSilencePolicyTest {
    @Test
    fun silenceReopenConstantIsTenSeconds() {
        assertEquals(10_000L, UsbGnssRepository.NMEA_SILENCE_REOPEN_MS)
    }

    @Test
    fun needsReopenOnlyAfterGraceWithoutNmea() {
        UsbGnssRepository.reset()
        assertFalse(UsbGnssRepository.needsNmeaSilenceReopen(nowMs = 100_000L, silenceMs = 10_000L))
        UsbGnssRepository.setConnected(true, atMs = 90_000L)
        assertFalse(UsbGnssRepository.needsNmeaSilenceReopen(nowMs = 95_000L, silenceMs = 10_000L))
        assertTrue(UsbGnssRepository.needsNmeaSilenceReopen(nowMs = 101_000L, silenceMs = 10_000L))
        UsbGnssRepository.markNmeaReceived(atMs = 100_500L)
        assertFalse(UsbGnssRepository.needsNmeaSilenceReopen(nowMs = 101_000L, silenceMs = 10_000L))
        UsbGnssRepository.reset()
    }
}
