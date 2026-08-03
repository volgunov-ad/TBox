package vad.dashing.tbox.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JunkSpeedMismatchDebouncerTest {

    @Before
    fun reset() {
        JunkSpeedMismatchDebouncer.reset()
    }

    @Test
    fun latchesJunkAfterFiveSeconds() {
        assertFalse(JunkSpeedMismatchDebouncer.update(0L, rawMismatch = true, carSpeedKnown = true))
        assertFalse(JunkSpeedMismatchDebouncer.update(4_999L, rawMismatch = true, carSpeedKnown = true))
        assertTrue(JunkSpeedMismatchDebouncer.update(5_000L, rawMismatch = true, carSpeedKnown = true))
    }

    @Test
    fun clearsJunkAfterTwoSecondsMatch() {
        JunkSpeedMismatchDebouncer.update(0L, true, true)
        JunkSpeedMismatchDebouncer.update(5_000L, true, true)
        assertTrue(JunkSpeedMismatchDebouncer.isLatched())
        assertTrue(JunkSpeedMismatchDebouncer.update(5_000L, rawMismatch = false, carSpeedKnown = true))
        assertTrue(JunkSpeedMismatchDebouncer.update(6_999L, rawMismatch = false, carSpeedKnown = true))
        assertFalse(JunkSpeedMismatchDebouncer.update(7_000L, rawMismatch = false, carSpeedKnown = true))
    }

    @Test
    fun briefMatchDoesNotClearLatch() {
        JunkSpeedMismatchDebouncer.update(0L, true, true)
        JunkSpeedMismatchDebouncer.update(5_000L, true, true)
        JunkSpeedMismatchDebouncer.update(5_500L, false, true)
        assertTrue(JunkSpeedMismatchDebouncer.update(6_000L, true, true))
    }

    @Test
    fun unknownCarSpeedClearsLatch() {
        JunkSpeedMismatchDebouncer.update(0L, true, true)
        JunkSpeedMismatchDebouncer.update(5_000L, true, true)
        assertFalse(JunkSpeedMismatchDebouncer.update(6_000L, true, carSpeedKnown = false))
    }
}
