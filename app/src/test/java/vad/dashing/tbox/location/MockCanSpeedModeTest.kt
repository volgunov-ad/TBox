package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockCanSpeedModeTest {

    @Test
    fun fromStorage_parsesKnownValues() {
        assertEquals(MockCanSpeedMode.ALWAYS, MockCanSpeedMode.fromStorage("ALWAYS"))
        assertEquals(MockCanSpeedMode.WHEN_FIX_LOST, MockCanSpeedMode.fromStorage("WHEN_FIX_LOST"))
        assertEquals(MockCanSpeedMode.WHEN_FIX_LOST, MockCanSpeedMode.fromStorage("holdover_only"))
        assertEquals(MockCanSpeedMode.NONE, MockCanSpeedMode.fromStorage(null))
        assertEquals(MockCanSpeedMode.NONE, MockCanSpeedMode.fromStorage(""))
        assertEquals(MockCanSpeedMode.NONE, MockCanSpeedMode.fromStorage("bogus"))
    }

    @Test
    fun enhancesMockFlags() {
        assertFalse(MockCanSpeedMode.NONE.enhancesMock)
        assertTrue(MockCanSpeedMode.ALWAYS.enhancesMock)
        assertTrue(MockCanSpeedMode.WHEN_FIX_LOST.enhancesMock)
    }
}
