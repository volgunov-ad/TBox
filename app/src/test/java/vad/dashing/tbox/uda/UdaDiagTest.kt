package vad.dashing.tbox.uda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdaDiagTest {
    @Test
    fun parseResult_capturedEmptyAbmReport() {
        val hex = "00 00 00 00 00 01 00 00 00 00 00 00 80 00 00 00 00 " +
            "55 44 45 5F 45 5F 4F 4B 00 00 00 00 00 00 00 00 00 " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
        val data = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        val result = UdaDiag.parseResult(data)!!
        assertEquals(53, data.size)
        assertEquals(0, result.type)
        assertEquals(0, result.ecuId)
        assertEquals(1, result.endCode)
        assertEquals(0, result.udeCode)
        assertTrue(result.ok)
        assertEquals("UDE_E_OK", result.resultInfo)
        assertEquals(0, result.data.size)
        assertEquals(listOf(UdaDtcEntry.None("ABM")), UdaDiag.entriesFor("ABM", result))
    }

    @Test
    fun parseDtcCodes_uds19Response() {
        val payload = byteArrayOf(
            0x59, 0x02, 0xFF.toByte(),
            0x03, 0x01, 0x00, 0x09,
            0x00, 0x00, 0x00, 0x00,
        )
        assertEquals(listOf("P0301-00/09"), UdaDiag.parseDtcCodes(payload))
    }

    @Test
    fun entriesFor_failureKeepsResultText() {
        val result = UdaDiagResult(
            type = 0,
            ecuId = 6,
            endCode = 1,
            resultCode = 0x80000200.toInt(),
            resultInfo = "UDE_E_RESPONSE_TIMEOUT",
            data = ByteArray(0),
        )
        assertEquals(0x200, result.udeCode)
        assertEquals(
            listOf(UdaDtcEntry.Failure("ICM", "UDE_E_RESPONSE_TIMEOUT")),
            UdaDiag.entriesFor("ICM", result),
        )
    }

    @Test
    fun session_appendsWithoutReplacingOtherBlocks() {
        UdaDtcSession.clear()
        UdaDtcSession.append(listOf(UdaDtcEntry.None("ICM")))
        UdaDtcSession.append(listOf(UdaDtcEntry.Code("ESP", "C0035-00")))
        assertEquals(
            listOf(UdaDtcEntry.None("ICM"), UdaDtcEntry.Code("ESP", "C0035-00")),
            UdaDtcSession.entries.value,
        )
        UdaDtcSession.clear()
    }

    @Test
    fun catalog_ecuZeroIsAbm() {
        assertEquals("ABM", UdaEcuCatalog.nameOf(0))
        assertEquals("ICM", UdaEcuCatalog.nameOf(6))
        assertEquals(37, UdaEcuCatalog.all.size)
    }
}
