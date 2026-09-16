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

    @Test
    fun parsePidSupportBitfield_0100_example() {
        // Classic example: BE 1F B8 13 → PIDs incl. 0x0C, 0x0D; next page via 0x20 bit
        // A=0xBE: 10111110 → 01,03,04,05,06,07
        // Better use a hand-built mask: A7..D0 for PIDs 01..20
        // Set only 0x0C (offset 11 → byte1 bit4) and 0x20 next flag (D0)
        // PID 0x0C: offset = 0x0C - 1 = 11 → byteIndex=1, bit from MSB: bit position in byte = 7-(11%8)=7-3=4 → B bit4
        // So B = 0x10
        // next 0x20: offset 31 → byte3 bit0 → D = 0x01
        val raw = "41 00 00 10 00 01"
        val page = Elm327Protocol.parsePidSupportBitfield(raw, 0x00).getOrThrow()
        assertTrue(0x0C in page.supportedPids)
        assertEquals(0x20, page.nextBitfieldPid)
    }

    @Test
    fun parsePidSupportBitfield_noNextPage() {
        // Only PID 0x05: offset 4 → byte0 bit3 → A = 0x08
        val raw = "41 00 08 00 00 00"
        val page = Elm327Protocol.parsePidSupportBitfield(raw, 0x00).getOrThrow()
        assertEquals(setOf(0x05), page.supportedPids)
        assertEquals(null, page.nextBitfieldPid)
    }

    @Test
    fun parsePendingDtcs_single() {
        val result = Elm327Protocol.parsePendingDtcs("47 01 03 01")
        assertTrue(result.isSuccess)
        assertEquals("P0301", result.getOrThrow().single().code)
    }

    @Test
    fun parseClearDtcs_ok() {
        assertTrue(Elm327Protocol.parseClearDtcsResponse("44").isSuccess)
    }

    @Test
    fun decodeMode01_fuelRate_andTrim() {
        assertEquals(12.5, Elm327Protocol.decodeMode01Pid(0x5E, byteArrayOf(0x00, 0xFA.toByte()))!!, 0.01)
        assertEquals(0.0, Elm327Protocol.decodeMode01Pid(0x06, byteArrayOf(0x80.toByte()))!!, 0.01)
    }

    @Test
    fun decodeMode01_catalyst_and_eqRatio() {
        // ((0x0F*256)+0xA0)/10 - 40 = 4000/10 - 40 = 360
        assertEquals(
            360.0,
            Elm327Protocol.decodeMode01Pid(0x3C, byteArrayOf(0x0F, 0xA0.toByte()))!!,
            0.01,
        )
        // 32768/32768 = 1.0
        assertEquals(
            1.0,
            Elm327Protocol.decodeMode01Pid(0x44, byteArrayOf(0x80.toByte(), 0x00))!!,
            0.001,
        )
    }

    @Test
    fun parseAtTextResponse_stripsNoise() {
        assertEquals("ISO 15765-4 (CAN 11/500)", Elm327Protocol.parseAtTextResponse("ISO 15765-4 (CAN 11/500)"))
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
