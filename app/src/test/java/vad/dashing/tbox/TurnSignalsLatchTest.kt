package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.TurnSignalSide
import vad.dashing.tbox.mbcan.TurnSignalsLatch
import vad.dashing.tbox.mbcan.TurnSignalsState

class TurnSignalsLatchTest {
    private val latch = TurnSignalsLatch()
    private val off = TurnSignalsState(
        leftActive = false,
        rightActive = false,
        hazardActive = false,
    )
    private val left = TurnSignalsState(
        leftActive = true,
        rightActive = false,
        hazardActive = false,
    )
    private val right = TurnSignalsState(
        leftActive = false,
        rightActive = true,
        hazardActive = false,
    )
    private val hazard = TurnSignalsState(
        leftActive = true,
        rightActive = true,
        hazardActive = true,
    )
    private val bothLamps = TurnSignalsState(
        leftActive = true,
        rightActive = true,
        hazardActive = false,
    )

    @Test
    fun flashHoldsThroughBlinkGapThenExpires() {
        assertEquals(TurnSignalSide.Right, latch.onState(right, 0L))
        assertEquals(TurnSignalSide.Right, latch.onState(off, 1_000L))
        assertEquals(TurnSignalSide.Right, latch.latchedForkHint(2_500L))
        assertNull(latch.latchedForkHint(2_501L))
    }

    @Test
    fun flashRetriggersHold() {
        latch.onState(right, 0L)
        latch.onState(off, 2_000L)
        assertEquals(TurnSignalSide.Right, latch.onState(right, 2_000L))
        assertEquals(TurnSignalSide.Right, latch.onState(off, 4_500L))
        assertNull(latch.latchedForkHint(4_501L))
    }

    @Test
    fun oppositeSideClearsOtherLatchImmediately() {
        latch.onState(right, 0L)
        assertEquals(TurnSignalSide.Right, latch.latchedForkHint(1_000L))
        assertEquals(TurnSignalSide.Left, latch.onState(left, 1_200L))
        assertEquals(TurnSignalSide.Left, latch.onState(off, 1_200L))
        assertEquals(TurnSignalSide.Left, latch.latchedForkHint(3_700L))
        assertNull("right was cleared; left hold starts at the left flash", latch.latchedForkHint(3_701L))
    }

    @Test
    fun hazardClearsLatchedSide() {
        latch.onState(right, 0L)
        assertNull(latch.onState(hazard, 400L))
        assertNull(latch.onState(off, 400L))
        assertNull(latch.latchedForkHint(400L))
        assertNull(latch.latchedForkHint(2_000L))
    }

    @Test
    fun hazardDuringHoldWaitClearsRightLatch() {
        latch.onState(right, 0L)
        latch.onState(off, 800L)
        assertEquals(TurnSignalSide.Right, latch.latchedForkHint(800L))
        assertNull(latch.onState(hazard, 900L))
        assertNull(latch.onState(off, 900L))
        assertNull(latch.latchedForkHint(1_500L))
    }

    @Test
    fun bothLampsWithoutHazardFlagClearLatch() {
        latch.onState(right, 0L)
        assertNull(latch.onState(bothLamps, 300L))
        assertNull(latch.onState(off, 300L))
    }

    @Test
    fun unknownSampleDoesNotClearHold() {
        latch.onState(right, 0L)
        assertEquals(TurnSignalSide.Right, latch.onState(TurnSignalsState(), 500L))
        assertEquals(TurnSignalSide.Right, latch.latchedForkHint(2_500L))
        assertNull(latch.latchedForkHint(2_501L))
    }

    @Test
    fun continuousA10StalkKeepsHintUntilHoldAfterRelease() {
        var t = 0L
        while (t <= 5_000L) {
            assertEquals("t=$t", TurnSignalSide.Right, latch.onState(right, t))
            t += 500L
        }
        assertEquals(TurnSignalSide.Right, latch.onState(off, 5_000L))
        assertEquals(TurnSignalSide.Right, latch.latchedForkHint(7_500L))
        assertNull(latch.latchedForkHint(7_501L))
    }
}
