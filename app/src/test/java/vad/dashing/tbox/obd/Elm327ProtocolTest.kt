package vad.dashing.tbox.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Elm327ProtocolTest {
    @Test
    fun decodeMode01_rpm() {
        val data = byteArrayOf(0x1A.toByte(), 0xF8.toByte()) // 6904/4 = 1726
        assertEquals(1726.0, Elm327Protocol.decodeMode01Pid(0x0C, data)!!, 0.01)
    }

    @Test
    fun decodeMode01_speed() {
        assertEquals(80.0, Elm327Protocol.decodeMode01Pid(0x0D, byteArrayOf(80))!!, 0.01)
    }

    @Test
    fun decodeMode01_coolant() {
        assertEquals(50.0, Elm327Protocol.decodeMode01Pid(0x05, byteArrayOf(90))!!, 0.01)
    }

    @Test
    fun parseMode01DataBytes_withSpacesAndPromptNoise() {
        val raw = "SEARCHING...\r41 0C 1A F8\r>"
        val data = Elm327Protocol.parseMode01DataBytes(raw, 0x0C)!!
        assertEquals(2, data.size)
        assertEquals(0x1A, data[0].toInt() and 0xFF)
        assertEquals(0xF8, data[1].toInt() and 0xFF)
    }

    @Test
    fun parseAdapterVoltage() {
        assertEquals(12.6, Elm327Protocol.parseAdapterVoltage("12.6V")!!, 0.01)
        assertEquals(13.1, Elm327Protocol.parseAdapterVoltage("13.1")!!, 0.01)
    }

    @Test
    fun parseStoredDtcs_single() {
        // 43 01 03 01 => one DTC P0301
        val result = Elm327Protocol.parseStoredDtcs("43 01 03 01")
        assertTrue(result.isSuccess)
        val codes = result.getOrThrow()
        assertEquals(1, codes.size)
        assertEquals("P0301", codes[0].code)
    }

    @Test
    fun parseStoredDtcs_noData() {
        val result = Elm327Protocol.parseStoredDtcs("NO DATA")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun parseStoredDtcs_unableToConnect_isFailure() {
        val result = Elm327Protocol.parseStoredDtcs("UNABLE TO CONNECT")
        assertTrue(result.isFailure)
    }
}

class ObdDtcTest {
    @Test
    fun fromBytes_p0301() {
        val dtc = ObdDtc.fromBytes(0x03, 0x01)!!
        assertEquals("P0301", dtc.code)
    }

    @Test
    fun fromBytes_c0035() {
        val dtc = ObdDtc.fromBytes(0x40, 0x35)!!
        assertEquals("C0035", dtc.code)
    }

    @Test
    fun fromBytes_zeroPadding_isNull() {
        assertNull(ObdDtc.fromBytes(0, 0))
    }
}

class ObdPidTest {
    @Test
    fun normalizeId_unknownFallsBackToRpm() {
        assertEquals(ObdPid.RPM.id, ObdPid.normalizeId("not_a_pid"))
    }

    @Test
    fun fromId_roundTrip() {
        for (pid in ObdPid.entries) {
            assertEquals(pid, ObdPid.fromId(pid.id))
        }
    }
}
