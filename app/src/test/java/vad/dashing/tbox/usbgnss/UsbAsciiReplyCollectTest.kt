package vad.dashing.tbox.usbgnss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAsciiReplyCollectTest {
    @Test
    fun streamingNmeaDoesNotFinishTheWait() {
        val lines = listOf(
            "\$GNGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47",
            "\$GNRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A",
        )
        assertTrue(lines.all { UsbAsciiReplyCollect.isStreamingNmea(it) })
        assertFalse(UsbAsciiReplyCollect.ready(lines, 5_000L))
    }

    @Test
    fun uniloglistReadyAfterQuiet() {
        val lines = listOf(
            "\$GNGGA,1,2,3*00",
            "\$command,UNILOGLIST,response: OK*4A",
            "#UNILOGLIST,92,GPS,FINE,2353,142915000,0,0,18,296;",
            "< GPGGA COM1 1",
        )
        assertFalse(UsbAsciiReplyCollect.ready(lines, 100L))
        assertTrue(UsbAsciiReplyCollect.ready(lines, UsbAsciiReplyCollect.QUIET_AFTER_REPLY_MS))
    }
}
