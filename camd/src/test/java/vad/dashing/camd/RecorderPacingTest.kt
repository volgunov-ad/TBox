package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderPacingTest {

    private val full = RecorderFps.frameMs(1)
    private val half = RecorderFps.frameMs(2)

    @Test
    fun sleepsUntilShortlyBeforeNextFrame() {
        val expect = full - RecorderPacing.EXPECT_MARGIN_MS
        assertEquals(expect, RecorderPacing.sleepMs(0, full))
        assertEquals(expect - 10, RecorderPacing.sleepMs(10, full))
        assertEquals(half - RecorderPacing.EXPECT_MARGIN_MS, RecorderPacing.sleepMs(0, half))
    }

    @Test
    fun pollsFinelyAroundExpectedFrame() {
        assertEquals(RecorderPacing.POLL_MS, RecorderPacing.sleepMs(full - RecorderPacing.EXPECT_MARGIN_MS, full))
        assertEquals(RecorderPacing.POLL_MS, RecorderPacing.sleepMs(full + 20, full))
    }

    @Test
    fun silentInputPollsLazily() {
        assertEquals(RecorderPacing.SILENT_POLL_MS, RecorderPacing.sleepMs(RecorderPacing.SILENT_MS, full))
        assertEquals(RecorderPacing.SILENT_POLL_MS, RecorderPacing.sleepMs(60_000, full))
    }

    @Test
    fun slowRateIsNotTakenForASilentInput() {
        val fifth = RecorderFps.frameMs(5)
        assertEquals(RecorderPacing.POLL_MS, RecorderPacing.sleepMs(fifth + 100, fifth))
    }

    @Test
    fun clockStepBackPollsInsteadOfSleepingLong() {
        assertEquals(RecorderPacing.POLL_MS, RecorderPacing.sleepMs(-5_000, full))
    }

    @Test
    fun fullRateTakesEveryFrame() {
        assertTrue(RecorderPacing.accept(11, 10, 1))
    }

    @Test
    fun divisorPassesOverFramesFromAFullRateGrabber() {
        assertFalse(RecorderPacing.accept(11, 10, 2))
        assertTrue(RecorderPacing.accept(12, 10, 2))
        assertFalse(RecorderPacing.accept(14, 10, 5))
        assertTrue(RecorderPacing.accept(15, 10, 5))
    }

    @Test
    fun unknownOrRestartedNumbersAreTaken() {
        assertTrue(RecorderPacing.accept(0, 10, 2))
        assertTrue(RecorderPacing.accept(5, 0, 2))
        assertTrue(RecorderPacing.accept(3, 500, 2))
    }

    @Test
    fun skippedCountsLostFramesAtTheRecordingRate() {
        assertEquals(0, RecorderPacing.skippedFrames(11, 10, 1))
        assertEquals(2, RecorderPacing.skippedFrames(13, 10, 1))
        assertEquals(0, RecorderPacing.skippedFrames(12, 10, 2))
        assertEquals(1, RecorderPacing.skippedFrames(14, 10, 2))
        assertEquals(0, RecorderPacing.skippedFrames(20, 15, 5))
    }

    @Test
    fun grabberRestartIsNotALoss() {
        assertEquals(0, RecorderPacing.skippedFrames(1, 500, 1))
        assertEquals(0, RecorderPacing.skippedFrames(5, 0, 1))
    }
}
