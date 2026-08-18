package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.LocValues

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MockLastGoodFixTest {

    @Test
    fun jsonRoundTrip() {
        val fix = MockLastGoodFix(55.1, 37.2, 120.0, 84f, 1_700_000_000_000L)
        val parsed = MockLastGoodFix.fromJson(fix.toJson())
        assertNotNull(parsed)
        assertEquals(fix.latitude, parsed!!.latitude, 1e-9)
        assertEquals(fix.longitude, parsed.longitude, 1e-9)
        assertEquals(fix.altitude, parsed.altitude, 1e-9)
        assertEquals(fix.bearingDeg, parsed.bearingDeg, 1e-3f)
        assertEquals(fix.savedAtEpochMs, parsed.savedAtEpochMs)
    }

    @Test
    fun fromLiveRejectsZeroZero() {
        assertNull(MockLastGoodFix.fromLive(LocValues(), 1L))
    }

    @Test
    fun fromShadowPersistsInertialPoint() {
        val fix = MockLastGoodFix.fromShadow(57.636, 39.965, 90.0, 178f, 1_700_000_000_000L)
        assertNotNull(fix)
        assertEquals(57.636, fix!!.latitude, 1e-9)
        assertEquals(39.965, fix.longitude, 1e-9)
        assertEquals(178f, fix.bearingDeg, 1e-3f)
        val parsed = MockLastGoodFix.fromJson(fix.toJson())
        assertNotNull(parsed)
        assertEquals(fix.latitude, parsed!!.latitude, 1e-9)
        assertEquals(fix.bearingDeg, parsed.bearingDeg, 1e-3f)
    }

    @Test
    fun fromShadowRejectsZeroZero() {
        assertNull(MockLastGoodFix.fromShadow(0.0, 0.0, 0.0, 10f, 1L))
    }

    @Test
    fun freshWithinMaxAge() {
        val now = 1_700_000_000_000L
        val fix = MockLastGoodFix(55.0, 37.0, 0.0, 10f, now - MockLastGoodFix.MAX_AGE_MS)
        assertTrue(fix.isFresh(now))
        assertFalse(fix.isFresh(now + 1L))
    }

    @Test
    fun coldStartOnlyWithCanSpeedModes() {
        assertFalse(MockLastGoodFix.canUseForColdStart(MockCanSpeedMode.NONE))
        assertTrue(MockLastGoodFix.canUseForColdStart(MockCanSpeedMode.ALWAYS))
        assertTrue(MockLastGoodFix.canUseForColdStart(MockCanSpeedMode.WHEN_FIX_LOST))
    }

    @Test
    fun debouncerWritesFirstImmediately() {
        val d = MockLastGoodFixDebouncer(debounceMs = 60_000L)
        val fix = MockLastGoodFix(55.0, 37.0, 0.0, 1f, 1L)
        assertEquals(fix, d.note(fix, nowElapsedMs = 100L))
        assertNull(d.pending)
    }

    @Test
    fun debouncerHoldsWithinWindowThenFlush() {
        val d = MockLastGoodFixDebouncer(debounceMs = 60_000L)
        val a = MockLastGoodFix(55.0, 37.0, 0.0, 1f, 1L)
        val b = MockLastGoodFix(55.1, 37.1, 0.0, 2f, 2L)
        assertNotNull(d.note(a, 0L))
        assertNull(d.note(b, 30_000L))
        assertEquals(b, d.takeFlush())
        assertNull(d.takeFlush())
    }

    @Test
    fun debouncerWritesAfterDebounce() {
        val d = MockLastGoodFixDebouncer(debounceMs = 60_000L)
        val a = MockLastGoodFix(55.0, 37.0, 0.0, 1f, 1L)
        val b = MockLastGoodFix(55.1, 37.1, 0.0, 2f, 2L)
        assertNotNull(d.note(a, 0L))
        assertNull(d.note(b, 30_000L))
        assertEquals(b, d.note(b, 60_000L))
        assertNull(d.pending)
    }
}
