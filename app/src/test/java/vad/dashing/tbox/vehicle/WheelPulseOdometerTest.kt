package vad.dashing.tbox.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.trip.TripDistanceFormat

class WheelPulseOdometerTest {

    @Before
    fun setUp() {
        WheelPulseOdometer.resetSession()
        WheelPulseOdometer.configure(metersPerPulse = 0.5f, confidence = 0.8f)
    }

    @Test
    fun forwardDelta_wrapsAt16Bits() {
        assertEquals(1, WheelPulseOdometer.forwardDelta(65535, 0))
    }

    @Test
    fun flushDistance_zeroUntilFullyCalibrated() {
        WheelPulseOdometer.configure(metersPerPulse = 0.5f, confidence = 0.2f)
        WheelPulseOdometer.onWheelSample(
            counters = WheelCounters(0, 0, 0, 0),
            reverse = false,
            steerDeg = 0f,
            speedKmh = 20f,
            nowElapsedMs = 1L,
        )
        WheelPulseOdometer.onWheelSample(
            counters = WheelCounters(10, 10, 0, 0),
            reverse = false,
            steerDeg = 0f,
            speedKmh = 20f,
            nowElapsedMs = 2L,
        )
        assertEquals(0f, WheelPulseOdometer.flushDistanceM(), 0f)
        assertFalse(WheelPulseCalibrationStore.isUsableForDistance())
    }

    @Test
    fun flushDistance_positiveWhenCalibrated() {
        WheelPulseOdometer.onWheelSample(
            counters = WheelCounters(0, 0, 0, 0),
            reverse = false,
            steerDeg = 0f,
            speedKmh = 20f,
            nowElapsedMs = 1L,
        )
        WheelPulseOdometer.onWheelSample(
            counters = WheelCounters(10, 10, 0, 0),
            reverse = false,
            steerDeg = 0f,
            speedKmh = 20f,
            nowElapsedMs = 2L,
        )
        val m = WheelPulseOdometer.flushDistanceM()
        assertEquals(5f, m, 0.01f)
    }

    @Test
    fun reverse_stillAddsPositiveDistance() {
        WheelPulseOdometer.onWheelSample(
            counters = WheelCounters(0, 0, 0, 0),
            reverse = false,
            steerDeg = 0f,
            speedKmh = 5f,
            nowElapsedMs = 1L,
        )
        WheelPulseOdometer.onWheelSample(
            counters = WheelCounters(4, 4, 0, 0),
            reverse = true,
            steerDeg = 0f,
            speedKmh = 5f,
            nowElapsedMs = 2L,
        )
        assertTrue(WheelPulseOdometer.flushDistanceM() > 0f)
    }

    @Test
    fun tripDistanceFormat_roundsToOneDecimal() {
        assertEquals(12.3f, TripDistanceFormat.roundKm(12.34f), 0.001f)
        assertEquals(12.4f, TripDistanceFormat.addKm(12.3f, 0.05f), 0.001f)
    }
}
