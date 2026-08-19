package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.TurnSignalIntentTracker
import vad.dashing.tbox.mbcan.TurnSignalSide
import vad.dashing.tbox.mbcan.TurnSignalsLatch
import vad.dashing.tbox.mbcan.TurnSignalsState

class TurnSignalIntentTrackerTest {
    private val off = TurnSignalsState(
        leftActive = false,
        rightActive = false,
        hazardActive = false,
    )
    private val right = TurnSignalsState(
        leftActive = false,
        rightActive = true,
        hazardActive = false,
    )

    @Test
    fun comfortThreeFlashes_notIntentional() {
        val latch = TurnSignalsLatch()
        var t = 0L
        repeat(3) {
            latch.onState(right, t)
            t += 400L
            latch.onState(off, t)
            t += 400L
        }
        val snap = latch.lastIntentSnapshot()
        assertEquals(TurnSignalSide.Right, snap.side)
        assertEquals(3, snap.flashCount)
        assertFalse(snap.intentional)
    }

    @Test
    fun fourthFlash_marksIntentional() {
        val latch = TurnSignalsLatch()
        var t = 0L
        repeat(4) {
            latch.onState(right, t)
            t += 400L
            latch.onState(off, t)
            t += 400L
        }
        val snap = latch.lastIntentSnapshot()
        assertEquals(4, snap.flashCount)
        assertTrue(snap.intentional)
    }

    @Test
    fun continuousA10Stalk_marksIntentionalAfterTwoSeconds() {
        val latch = TurnSignalsLatch()
        latch.onState(right, 0L)
        assertFalse(latch.lastIntentSnapshot().intentional)
        latch.onState(right, 1_500L)
        assertFalse(latch.lastIntentSnapshot().intentional)
        latch.onState(right, TurnSignalIntentTracker.CONTINUOUS_STALK_MS)
        assertTrue(latch.lastIntentSnapshot().intentional)
        assertEquals(1, latch.lastIntentSnapshot().flashCount)
    }

    @Test
    fun holdExpiry_resetsIntent() {
        val latch = TurnSignalsLatch()
        latch.onState(right, 0L)
        latch.onState(right, 500L)
        latch.onState(right, 2_000L)
        assertTrue(latch.lastIntentSnapshot().intentional)
        assertNullSideAfterHold(latch)
    }

    private fun assertNullSideAfterHold(latch: TurnSignalsLatch) {
        assertEquals(null, latch.onState(off, 2_000L + TurnSignalsLatch.HOLD_MS + 1L))
        assertEquals(0, latch.lastIntentSnapshot().flashCount)
        assertFalse(latch.lastIntentSnapshot().intentional)
    }
}
