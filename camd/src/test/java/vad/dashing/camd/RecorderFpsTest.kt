package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Test

class RecorderFpsTest {

    @Test
    fun optionsAreEvenDivisorsOfTheSensorRate() {
        assertEquals(listOf(1, 2, 5), RecorderFps.DIVISORS)
        RecorderFps.DIVISORS.forEach { assertEquals(0L, (RecorderFps.frameMs(it) % RecorderFps.SENSOR_FRAME_MS)) }
        assertEquals(40L, RecorderFps.frameMs(1))
        assertEquals(80L, RecorderFps.frameMs(2))
        assertEquals(200L, RecorderFps.frameMs(5))
    }

    @Test
    fun bitrateScalesWithTheRate() {
        assertEquals(6_000_000, RecorderFps.bitRate(1))
        assertEquals(3_000_000, RecorderFps.bitRate(2))
        assertEquals(1_200_000, RecorderFps.bitRate(5))
        assertEquals("6", RecorderFps.bitRateLabel(1))
        assertEquals("3", RecorderFps.bitRateLabel(2))
        assertEquals("1,2", RecorderFps.bitRateLabel(5))
    }

    @Test
    fun labelsAndEncoderRate() {
        assertEquals("25", RecorderFps.label(1))
        assertEquals("12,5", RecorderFps.label(2))
        assertEquals("5", RecorderFps.label(5))
        assertEquals(13, RecorderFps.encoderFps(2))
        assertEquals(12.5, RecorderFps.fps(2), 1e-9)
    }

    @Test
    fun unknownStoredValueFallsBackToFullRate() {
        assertEquals(1, RecorderFps.normalize(3))
        assertEquals(1, RecorderFps.normalize(0))
        assertEquals(5, RecorderFps.normalize(5))
    }
}
