package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationIntervalLogicTest {
    @Test
    fun firstBoundaryIsOneFullIntervalAfterAnchor() {
        assertEquals(0L, AutomationIntervalLogic.elapsedBoundaryCount(1_000L, 30_000L, 1_000L))
        assertEquals(0L, AutomationIntervalLogic.elapsedBoundaryCount(1_000L, 30_000L, 30_999L))
        assertEquals(1L, AutomationIntervalLogic.elapsedBoundaryCount(1_000L, 30_000L, 31_000L))
        assertEquals(
            31_000L,
            AutomationIntervalLogic.boundaryElapsedMillis(1_000L, 30_000L, 1L),
        )
    }

    @Test
    fun delayedTickReturnsLatestBoundaryWithoutChangingPhase() {
        assertEquals(3L, AutomationIntervalLogic.elapsedBoundaryCount(0L, 30_000L, 95_000L))
        assertEquals(
            90_000L,
            AutomationIntervalLogic.boundaryElapsedMillis(0L, 30_000L, 3L),
        )
        assertEquals(
            120_000L,
            AutomationIntervalLogic.boundaryElapsedMillis(0L, 30_000L, 4L),
        )
    }
}
