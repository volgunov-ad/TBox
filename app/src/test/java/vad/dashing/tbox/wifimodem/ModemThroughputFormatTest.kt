package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModemThroughputFormatTest {
    @Test
    fun parseBps_readsIntegers() {
        assertEquals(290L, ModemThroughputFormat.parseBps("290"))
        assertEquals(0L, ModemThroughputFormat.parseBps("0"))
        assertNull(ModemThroughputFormat.parseBps(""))
        assertNull(ModemThroughputFormat.parseBps(null))
        assertNull(ModemThroughputFormat.parseBps("abc"))
    }

    @Test
    fun formatBps_scalesUnits() {
        assertEquals("-", ModemThroughputFormat.formatBps(null))
        assertEquals("0 B/s", ModemThroughputFormat.formatBps(0))
        assertEquals("512 B/s", ModemThroughputFormat.formatBps(512))
        assertEquals("1.5 KB/s", ModemThroughputFormat.formatBps(1536))
        assertEquals("1.0 MB/s", ModemThroughputFormat.formatBps(1024L * 1024L))
    }
}
