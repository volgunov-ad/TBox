package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourceSelectionHoldTest {

    @Test
    fun beginHold_extendsDeadlineAndInvalidatesPreviousGeneration() {
        val hold = MediaSourceSelectionHold()
        val first = hold.beginHold(nowElapsedMs = 1_000L, holdMs = 7_500L)
        val second = hold.beginHold(nowElapsedMs = 2_000L, holdMs = 7_500L)
        assertEquals(1, first)
        assertEquals(2, second)
        assertTrue(hold.isHeld(9_000L))
        assertFalse(hold.consumeRelease(first))
        assertTrue(hold.isHeld(9_000L))
        assertTrue(hold.consumeRelease(second))
        assertFalse(hold.isHeld(9_000L))
    }

    @Test
    fun isHeld_isFalseAfterDeadline() {
        val hold = MediaSourceSelectionHold()
        hold.beginHold(nowElapsedMs = 0L, holdMs = 7_500L)
        assertTrue(hold.isHeld(7_499L))
        assertFalse(hold.isHeld(7_500L))
    }

    @Test
    fun cancel_dropsHoldAndRejectsPendingRelease() {
        val hold = MediaSourceSelectionHold()
        val generation = hold.beginHold(nowElapsedMs = 0L, holdMs = 7_500L)
        hold.cancel()
        assertFalse(hold.isHeld(1_000L))
        assertFalse(hold.consumeRelease(generation))
    }
}
