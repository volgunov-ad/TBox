package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.utils.FuelLevelDwellFilter

class FuelLevelDwellFilterTest {
    @Test
    fun acceptsAfterDwellOfSameValue() {
        val filter = FuelLevelDwellFilter(dwellMs = 15_000L)
        assertNull(filter.onSample(40u, nowElapsedMs = 0L))
        assertNull(filter.onSample(40u, nowElapsedMs = 14_999L))
        assertEquals(40u, filter.onSample(40u, nowElapsedMs = 15_000L))
        // Same accepted value is not re-emitted
        assertNull(filter.onSample(40u, nowElapsedMs = 20_000L))
    }

    @Test
    fun valueChangeResetsDwell() {
        val filter = FuelLevelDwellFilter(dwellMs = 15_000L)
        assertNull(filter.onSample(40u, nowElapsedMs = 0L))
        assertNull(filter.onSample(41u, nowElapsedMs = 10_000L))
        assertNull(filter.onSample(41u, nowElapsedMs = 20_000L))
        assertEquals(41u, filter.onSample(41u, nowElapsedMs = 25_000L))
    }

    @Test
    fun singleSampleThenTimePassesStillAccepts() {
        val filter = FuelLevelDwellFilter(dwellMs = 15_000L)
        assertNull(filter.onSample(55u, nowElapsedMs = 0L))
        // HU-style: next sample only after dwell window
        assertEquals(55u, filter.onSample(55u, nowElapsedMs = 30_000L))
    }
}
