package vad.dashing.tbox.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CanFramesProcessTboxJournalTest {
    @Test
    fun tboxFrameLogLine_formatsIdMsAndPayload() {
        val payload = byteArrayOf(0x2A, 0x01, 0x00, 0x00, 0x00, 0x00, 0x7B, 0xC4.toByte())
        assertEquals(
            "id=0x52F ms=042 data=2A 01 00 00 00 00 7B C4",
            CanFramesProcess.tboxFrameLogLine(0x52F, payload, 123_042L),
        )
    }

    @Test
    fun tboxFrameLogLine_padsShortCanIdAndZeroMillis() {
        assertEquals(
            "id=0x0C4 ms=000 data=00 00 00 00 00 00 00 00",
            CanFramesProcess.tboxFrameLogLine(0xC4, ByteArray(8), 123_000L),
        )
    }

    @Test
    fun tboxFrameLogLine_formatsExtendedId() {
        assertEquals(
            "id=0x18FF50E5 ms=999 data=AA 00 00 00 00 00 00 00",
            CanFramesProcess.tboxFrameLogLine(
                0x18FF50E5,
                byteArrayOf(0xAA.toByte(), 0, 0, 0, 0, 0, 0, 0),
                999L,
            ),
        )
    }

    @Test
    fun journalTag_matchesPythonConverterExpectation() {
        assertEquals("CANDIAG_TBOX", CanFramesProcess.TBOX_JOURNAL_TAG)
    }
}
