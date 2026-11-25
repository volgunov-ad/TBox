package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderPacingTest {

    @Test
    fun targetIsSensorRate() {
        assertEquals(25, RecorderPacing.TARGET_FPS)
        assertEquals(40L, RecorderPacing.FRAME_MS)
    }

    @Test
    fun sleepsUntilShortlyBeforeNextFrame() {
        assertEquals(RecorderPacing.EXPECT_MS, RecorderPacing.sleepMs(0))
        assertEquals(RecorderPacing.EXPECT_MS - 10, RecorderPacing.sleepMs(10))
        assertTrue(RecorderPacing.EXPECT_MS < RecorderPacing.FRAME_MS)
    }

    @Test
    fun pollsFinelyAroundExpectedFrame() {
        assertEquals(RecorderPacing.POLL_MS, RecorderPacing.sleepMs(RecorderPacing.EXPECT_MS))
        assertEquals(RecorderPacing.POLL_MS, RecorderPacing.sleepMs(RecorderPacing.FRAME_MS + 20))
    }

    @Test
    fun silentInputPollsLazily() {
        assertEquals(RecorderPacing.SILENT_POLL_MS, RecorderPacing.sleepMs(RecorderPacing.SILENT_MS))
        assertEquals(RecorderPacing.SILENT_POLL_MS, RecorderPacing.sleepMs(60_000))
    }

    @Test
    fun clockStepBackPollsInsteadOfSleepingLong() {
        assertEquals(RecorderPacing.POLL_MS, RecorderPacing.sleepMs(-5_000))
    }

    @Test
    fun skippedCountsLostGrabberFrames() {
        assertEquals(0, RecorderPacing.skippedBetween(10, 11))
        assertEquals(2, RecorderPacing.skippedBetween(10, 13))
    }

    @Test
    fun grabberRestartIsNotALoss() {
        assertEquals(0, RecorderPacing.skippedBetween(500, 1))
    }
}
