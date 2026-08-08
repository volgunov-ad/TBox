package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeoDebugNmeaBufferTest {

    @Before
    fun clear() {
        GeoDebugNmeaBuffer.clear()
    }

    @Test
    fun noteAndDrain() {
        GeoDebugNmeaBuffer.noteSentence("\$GPGGA,1")
        GeoDebugNmeaBuffer.noteSentence("\$GPRMC,2")
        val drained = GeoDebugNmeaBuffer.drainSinceLastTick()
        assertEquals(2, drained.size)
        assertTrue(drained[0].contains("GPGGA"))
        assertTrue(GeoDebugNmeaBuffer.drainSinceLastTick().isEmpty())
    }

    @Test
    fun capsAtMax() {
        repeat(120) { GeoDebugNmeaBuffer.noteSentence("\$GPGGA,$it") }
        val snap = GeoDebugNmeaBuffer.snapshot()
        assertEquals(80, snap.size)
    }
}
