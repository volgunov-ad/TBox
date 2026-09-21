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
        assertTrue(Elm327Protocol.parseClearDtcsResponse("OK").isSuccess)
        assertTrue(Elm327Protocol.parseClearDtcsResponse("NO DATA").isFailure)
        assertTrue(Elm327Protocol.parseClearDtcsResponse("SEARCHING...").isFailure)
    }

    @Test
    fun isSilentTimeout_detection() {
        // Blank response at any elapsed time — adapter never answered.
        assertTrue(Elm327Protocol.isSilentTimeout("", 100L, 4_000L))
        assertTrue(Elm327Protocol.isSilentTimeout("   \r\n", 3_990L, 4_000L))
        // Full window elapsed without a prompt — partial data, dead-ish link.
        assertTrue(Elm327Protocol.isSilentTimeout("41 0C 1A", 4_000L, 4_000L))
        assertTrue(Elm327Protocol.isSilentTimeout("41 0C 1A", 4_015L, 4_000L))
        // Prompt received inside the window — healthy, even for slow VIN requests.
        assertTrue(!Elm327Protocol.isSilentTimeout("49 02 01", 9_500L, 10_000L))
        assertTrue(!Elm327Protocol.isSilentTimeout("12.6V", 20L, 4_000L))
    }

    @Test
    fun isElmError_busInitOk_isNotError() {
        assertTrue(!Elm327Protocol.isElmError("BUS INIT: OK"))
        assertTrue(!Elm327Protocol.isElmError("SEARCHING...\rBUS INIT: OK\r41 0C 1A F8"))
        assertTrue(Elm327Protocol.isElmError("BUS INIT: ERROR"))
        assertTrue(Elm327Protocol.isElmError("UNABLE TO CONNECT"))
        assertTrue(Elm327Protocol.isElmError("?"))
    }

    @Test
    fun parsePermanentDtcs_single() {
        val result = Elm327Protocol.parsePermanentDtcs("4A 01 03 01")
        assertTrue(result.isSuccess)
        assertEquals("P0301", result.getOrThrow().single().code)
    }

    @Test
    fun parseVin_multiline() {
        // Classic multi-frame VIN for WVWZZZ1JZ3W386752
        val raw = """
            49 02 01 00 57 56 57
            49 02 02 5A 5A 5A 31
            49 02 03 4A 5A 33 57
            49 02 04 33 38 36 37
            49 02 05 35 32
        """.trimIndent()
        val vin = Elm327Protocol.parseVin(raw).getOrThrow()
        assertEquals("WVWZZZ1JZ3W386752", vin)
    }

    @Test
    fun parseVin_noData() {
        assertNull(Elm327Protocol.parseVin("NO DATA").getOrThrow())
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

    @Test
    fun mode02Request_format() {
        assertEquals("020C", Elm327Protocol.mode02Request(0x0C))
        assertEquals("0202", Elm327Protocol.mode02Request(Elm327Protocol.FREEZE_FRAME_DTC_PID))
    }

    @Test
    fun parseMode02DataBytes_stripsFrameNumber() {
        // 42 0C 00 1A F8 → RPM data after frame 00
        val data = Elm327Protocol.parseMode02DataBytes("42 0C 00 1A F8", 0x0C)!!
        assertEquals(2, data.size)
        assertEquals(0x1A, data[0].toInt() and 0xFF)
        assertEquals(0xF8, data[1].toInt() and 0xFF)
        assertEquals(1726.0, Elm327Protocol.decodeMode01Pid(0x0C, data)!!, 0.01)
    }

    @Test
    fun parseFreezeFrameDtc_p0301() {
        val result = Elm327Protocol.parseFreezeFrameDtc("42 02 00 03 01")
        assertTrue(result.isSuccess)
        assertEquals("P0301", result.getOrThrow()!!.code)
    }

    @Test
    fun parseFreezeFrameDtc_noData() {
        val result = Elm327Protocol.parseFreezeFrameDtc("NO DATA")
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun parseMode02PidSupportBitfield_mirrorsMode01Layout() {
        // Same mask as Mode 01 test: PID 0x0C + next page 0x20
        val raw = "42 00 00 00 10 00 01"
        val page = Elm327Protocol.parseMode02PidSupportBitfield(raw, 0x00).getOrThrow()
        assertTrue(0x0C in page.supportedPids)
        assertEquals(0x20, page.nextBitfieldPid)
    }

    @Test
    fun parseMonitorStatus_spark_milOn_withIncompleteCatalyst() {
        // A: MIL + 1 DTC = 0x81
        // B: spark, misfire+fuel+components available, all complete = 0x07
        // C: catalyst available only = 0x01
        // D: catalyst incomplete = 0x01
        val raw = "41 01 81 07 01 01"
        val status = Elm327Protocol.parseMonitorStatus(raw, 0x01).getOrThrow()
        assertTrue(status.milOn)
        assertEquals(1, status.confirmedDtcCount)
        assertTrue(status.sparkIgnition)
        val catalyst = status.monitors.first { it.id == ObdMonitorStatus.SPARK_CATALYST }
        assertTrue(catalyst.available)
        assertTrue(catalyst.incomplete)
        val misfire = status.monitors.first { it.id == ObdMonitorStatus.SPARK_MISFIRE }
        assertTrue(misfire.available)
        assertTrue(misfire.complete)
    }

    @Test
    fun parseMonitorStatus_compression_layout() {
        // B3 set → compression; B = 0x08 | 0x07 avail = 0x0F
        val raw = "41 01 00 0F 01 00"
        val status = Elm327Protocol.parseMonitorStatus(raw, 0x01).getOrThrow()
        assertTrue(!status.sparkIgnition)
        assertTrue(status.monitors.any { it.id == ObdMonitorStatus.COMP_NMHC && it.available })
    }
}

class ObdDtcCatalogTest {
    @Test
    fun parseTsv_andLookup() {
        ObdDtcCatalog.resetForTests()
        ObdDtcCatalog.loadForTests(
            sequenceOf(
                "P0301\tCylinder 1 Misfire Detected",
                "P0420\tCatalyst System Efficiency Below Threshold (Bank 1)",
                "# comment",
                "badline",
            ),
        )
        assertEquals("Cylinder 1 Misfire Detected", ObdDtcCatalog.description("p0301"))
        assertEquals(null, ObdDtcCatalog.description("P9999"))
        ObdDtcCatalog.resetForTests()
    }

    @Test
    fun assetPath_ruAndEn() {
        assertEquals("obd/dtc_ru.tsv", ObdDtcCatalog.assetPathForFlavor("ru"))
        assertEquals("obd/dtc_en.tsv", ObdDtcCatalog.assetPathForFlavor("en"))
    }

    @Test
    fun russianAsset_hasTranslatedCommonCodes() {
        val candidates = listOf(
            java.io.File("src/main/assets/obd/dtc_ru.tsv"),
            java.io.File("app/src/main/assets/obd/dtc_ru.tsv"),
        )
        val file = candidates.firstOrNull { it.isFile }
        assertTrue("dtc_ru.tsv not found in $candidates", file != null)
        val map = ObdDtcCatalog.parseTsv(file!!.readLines().asSequence())
        assertTrue(map.size > 1000)
        val p0301 = map["P0301"]
        assertTrue("P0301 missing", !p0301.isNullOrBlank())
        assertTrue("P0301 not RU: $p0301", p0301!!.any { it in '\u0400'..'\u04FF' })
        val p0100 = map["P0100"]!!
        assertTrue(p0100.contains("расход") || p0100.contains("воздух"))
    }
}

class ObdDtcExportTest {
    @Test
    fun formatText_includesStoredPendingAndFreezeFrame() {
        ObdDtcCatalog.resetForTests()
        ObdDtcCatalog.loadForTests(sequenceOf("P0301\tCylinder 1 Misfire Detected"))
        val text = ObdDtcExport.formatText(
            ObdDtcExport.Snapshot(
                exportedAtMs = 1_700_000_000_000L,
                adapterVersion = "ELM327 v1.5",
                stored = listOf(ObdDtc.fromBytes(0x03, 0x01)!!),
                storedReadAtMs = 1_700_000_000_000L,
                storedRead = true,
                pending = emptyList(),
                pendingReadAtMs = 1_700_000_000_000L,
                pendingRead = true,
                freezeFrameDtc = ObdDtc.fromBytes(0x03, 0x01)!!,
                freezeFrameValues = mapOf(ObdPid.RPM.id to 1726.0),
                freezeFrameReadAtMs = 1_700_000_000_000L,
                freezeFrameRead = true,
                monitorSinceCleared = ObdMonitorStatus(
                    milOn = true,
                    confirmedDtcCount = 1,
                    sparkIgnition = true,
                    monitors = listOf(
                        ObdMonitorItem(ObdMonitorStatus.SPARK_MISFIRE, available = true, complete = true),
                    ),
                ),
                monitorReadAtMs = 1_700_000_000_000L,
                monitorRead = true,
                vin = "WVWZZZ1JZ3W386752",
                vinRead = true,
                permanent = emptyList(),
                permanentRead = true,
                permanentReadAtMs = 1_700_000_000_000L,
            ),
        )
        assertTrue(text.contains("P0301 — Cylinder 1 Misfire Detected"))
        assertTrue(text.contains("Stored (Mode 03)"))
        assertTrue(text.contains("Pending (Mode 07)"))
        assertTrue(text.contains("Permanent (Mode 0A)"))
        assertTrue(text.contains("(none)"))
        assertTrue(text.contains("Freeze frame (Mode 02)"))
        assertTrue(text.contains("rpm=1726.0"))
        assertTrue(text.contains("ELM327 v1.5"))
        assertTrue(text.contains("Monitor status"))
        assertTrue(text.contains("MIL: ON"))
        assertTrue(text.contains("VIN: WVWZZZ1JZ3W386752"))
        ObdDtcCatalog.resetForTests()
    }

    @Test
    fun fileName_hasPrefixAndTxt() {
        val name = ObdDtcExport.fileName(1_700_000_000_000L)
        assertTrue(name.startsWith(ObdDtcExport.FILE_PREFIX))
        assertTrue(name.endsWith(".${ObdDtcExport.FILE_EXTENSION}"))
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
