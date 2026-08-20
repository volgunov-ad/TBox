package vad.dashing.tbox.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.trip.TripDistanceFormat
import vad.dashing.tbox.trip.TripPulseDistance

class WheelPulseOdometerTest {

    @Before
    fun setUp() {
        WheelPulseOdometer.resetAllForTest()
        WheelPulseCalibrationStore.update(WheelPulseCalibration())
        WheelPulseOdometer.configure(metersPerPulse = TEST_K, confidence = 0.8f)
    }

    @Test
    fun forwardDelta_wrapsAt13Bits() {
        assertEquals(1, WheelPulseOdometer.forwardDelta(8191, 0))
        assertEquals(62, WheelPulseOdometer.forwardDelta(8180, 50))
        assertEquals(786, WheelPulseOdometer.forwardDelta(7466, 60))
        assertEquals(0, WheelPulseOdometer.forwardDelta(8180, 50, bits = 16))
    }

    @Test
    fun configure_dropsOutOfRangeK() {
        WheelPulseOdometer.configure(0.5109f, 0.15f)
        val snap = WheelPulseOdometer.peekCalibration()
        assertEquals(0f, snap.metersPerPulse, 0f)
        assertEquals(0f, snap.confidence, 0f)
        assertFalse(snap.usableForDistance)
    }

    @Test
    fun flushDrDistance_zeroUntilFullyCalibrated() {
        WheelPulseOdometer.configure(metersPerPulse = TEST_K, confidence = 0.2f)
        sample(0, 1L)
        sample(10, 2L)
        assertEquals(0f, WheelPulseOdometer.flushDrDistanceM(), 0f)
        assertFalse(WheelPulseCalibrationStore.isUsableForDistance())
    }

    @Test
    fun flushDrDistance_positiveWhenCalibrated() {
        sample(0, 1L)
        sample(200, 2L)
        val m = WheelPulseOdometer.flushDrDistanceM()
        assertEquals(5f, m, 0.01f)
    }

    @Test
    fun reverse_stillAddsPositiveDistance() {
        sample(0, 1L, speed = 5f)
        WheelPulseOdometer.onWheelSample(
            counters = mask(4),
            reverse = true,
            steerDeg = 0f,
            speedKmh = 5f,
            nowElapsedMs = 2L,
        )
        assertTrue(WheelPulseOdometer.flushDrDistanceM() > 0f)
    }

    @Test
    fun drFlush_doesNotClearTripFraction() {
        sample(0, 1L)
        sample(400, 2L)
        assertEquals(10f, WheelPulseOdometer.flushDrDistanceM(), 0.01f)
        assertEquals(10f, WheelPulseOdometer.peekPulseSinceLastOdoM(), 0.01f)
        assertEquals(0f, WheelPulseOdometer.flushDrDistanceM(), 0.01f)
        assertEquals(10f, WheelPulseOdometer.peekPulseSinceLastOdoM(), 0.01f)
    }

    @Test
    fun wrap13_doesNotZeroDeltaOrInflateAsym() {
        sample(8180, 1L)
        sample(50, 2L)
        val snap = WheelPulseOdometer.peekDebugSnapshot()
        assertEquals(62, snap.dLhf)
        assertEquals(62, snap.dRhf)
        assertEquals(0f, snap.asymFront, 0.001f)
        assertEquals(62 * TEST_K, WheelPulseOdometer.flushDrDistanceM(), 0.01f)
    }

    @Test
    fun hardCalib_acceptsWindowThatCrosses13BitWrap() {
        WheelPulseOdometer.resetAllForTest()
        WheelPulseCalibrationStore.update(WheelPulseCalibration())
        WheelPulseOdometer.configure(0f, 0f)
        WheelPulseOdometer.onOdometerKm(1_000u, 0L)
        var pulse = 8_000
        var t = 1L
        sample(pulse, t++)
        repeat(CALIB_STEPS) {
            pulse += CALIB_STEP
            sample(pulse, t++)
        }
        WheelPulseOdometer.onOdometerKm(1_005u, t++)
        sample(pulse + 10, t)
        val snap = WheelPulseOdometer.peekCalibration()
        assertEquals(0.15f, snap.confidence, 0.001f)
        assertEquals(5_000f / 210_000f, snap.metersPerPulse, 0.0002f)
        assertFalse(snap.usableForDistance)
    }

    @Test
    fun hardCalib_rejectsSparseWindow() {
        WheelPulseOdometer.resetAllForTest()
        WheelPulseCalibrationStore.update(WheelPulseCalibration())
        WheelPulseOdometer.configure(0f, 0f)
        WheelPulseOdometer.onOdometerKm(1_000u, 0L)
        sample(0, 1L)
        sample(2_000, 2L)
        WheelPulseOdometer.onOdometerKm(1_005u, 3L)
        sample(2_010, 4L)
        val snap = WheelPulseOdometer.peekCalibration()
        assertEquals(0f, snap.metersPerPulse, 0f)
        assertEquals(0f, snap.confidence, 0f)
    }

    @Test
    fun hardCalib_reseedsWhenStoredKDisagrees() {
        WheelPulseOdometer.resetAllForTest()
        WheelPulseCalibrationStore.update(WheelPulseCalibration())
        WheelPulseOdometer.configure(0.060f, 0.15f)
        WheelPulseOdometer.onOdometerKm(1_000u, 0L)
        var pulse = 0
        var t = 1L
        sample(pulse, t++)
        repeat(CALIB_STEPS) {
            pulse += CALIB_STEP
            sample(pulse, t++)
        }
        WheelPulseOdometer.onOdometerKm(1_005u, t++)
        sample(pulse + 10, t)
        val snap = WheelPulseOdometer.peekCalibration()
        assertEquals(0.15f, snap.confidence, 0.001f)
        assertEquals(5_000f / 210_000f, snap.metersPerPulse, 0.0002f)
    }

    @Test
    fun tripDistanceFormat_roundsToOneDecimal() {
        assertEquals(12.3f, TripDistanceFormat.roundKm(12.34f), 0.001f)
        assertEquals(12.4f, TripDistanceFormat.addKm(12.3f, 0.05f), 0.001f)
    }

    @Test
    fun tripHybrid_odoPlusFraction() {
        assertEquals(
            2.5f,
            TripPulseDistance.hybridKm(
                odoStartKm = 100u,
                odoNowKm = 102u,
                pulseSinceLastOdoM = 500f,
            ),
            0.001f,
        )
    }

    @Test
    fun tripHybrid_resolveFallsBackToClassicOdo() {
        val next = TripPulseDistance.resolveDistanceKm(
            currentDistanceKm = 3.0f,
            odoStartKm = 10u,
            odoNowKm = 12u,
            lastOdoKm = 11u,
            pulseSinceLastOdoM = 400f,
            hybridEnabled = false,
        )
        assertEquals(4.0f, next, 0.001f)
    }

    private fun sample(pulse: Int, elapsed: Long, speed: Float = 80f) {
        WheelPulseOdometer.onWheelSample(
            counters = mask(pulse),
            reverse = false,
            steerDeg = 0f,
            speedKmh = speed,
            nowElapsedMs = elapsed,
        )
    }

    private fun mask(pulse: Int): WheelCounters {
        val v = pulse and (COUNTER_MOD - 1)
        return WheelCounters(v, v, v, v)
    }

    companion object {
        private const val TEST_K = 0.025f
        private const val COUNTER_MOD = 8192
        private const val PULSES_PER_5KM = 210_000
        private const val CALIB_STEPS = 80
        private const val CALIB_STEP = PULSES_PER_5KM / CALIB_STEPS
    }
}
