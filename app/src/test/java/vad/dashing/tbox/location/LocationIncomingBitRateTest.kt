package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.esp.LocationSource

class LocationIncomingBitRateTest {

    @Test
    fun rollingMeter_reportsBitsPerSecAfterWindow() {
        val m = RollingBitRateMeter(windowMs = 1_000L, staleMs = 2_000L)
        // 125 bytes in 1 s -> 1000 bit/s
        m.note(125, nowElapsedMs = 0L)
        assertEquals(0L, m.sample(500L))
        assertEquals(1_000L, m.sample(1_000L))
    }

    @Test
    fun rollingMeter_accumulatesAcrossNotes() {
        val m = RollingBitRateMeter(windowMs = 1_000L, staleMs = 2_000L)
        m.note(50, 0L)
        m.note(50, 400L)
        m.note(25, 800L)
        // 125 bytes over 1000 ms -> 1000 bit/s
        assertEquals(1_000L, m.sample(1_000L))
    }

    @Test
    fun rollingMeter_goesToZeroWhenStale() {
        val m = RollingBitRateMeter(windowMs = 1_000L, staleMs = 2_000L)
        m.note(125, 0L)
        assertEquals(1_000L, m.sample(1_000L))
        assertEquals(0L, m.sample(3_500L))
    }

    @Test
    fun androidSource_hasNoMeter() {
        assertNull(LocationIncomingBitRate.bitsPerSec(LocationSource.ANDROID, nowElapsedMs = 0L))
        assertEquals("\u2014", LocationIncomingBitRate.formatBitsPerSec(null))
    }

    @Test
    fun noteBytes_usbSeparateFromTbox() {
        LocationIncomingBitRate.resetForTests()
        LocationIncomingBitRate.noteBytes(LocationSource.USB, 125, nowElapsedMs = 10_000L)
        LocationIncomingBitRate.noteBytes(LocationSource.TBOX, 250, nowElapsedMs = 10_000L)
        assertEquals(1_000L, LocationIncomingBitRate.bitsPerSec(LocationSource.USB, 11_000L))
        assertEquals(2_000L, LocationIncomingBitRate.bitsPerSec(LocationSource.TBOX, 11_000L))
        LocationIncomingBitRate.resetForTests()
    }
}
