package vad.dashing.camd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderMediaPauseTest {
    @Test
    fun pausesOnlyWhenEveryRecordingCameraWaits() {
        assertFalse(RecorderMediaPause.paused(setOf(8, 9), emptySet()))
        assertFalse(RecorderMediaPause.paused(setOf(8, 9), setOf(8)))
        assertTrue(RecorderMediaPause.paused(setOf(8, 9), setOf(8, 9)))
        assertTrue(RecorderMediaPause.paused(setOf(8), setOf(8, 9)))
        assertFalse(RecorderMediaPause.paused(emptySet(), setOf(8)))
    }
}
