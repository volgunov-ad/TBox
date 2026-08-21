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
            counters = WheelCounters(4, 4, 4, 4),
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

    @Test
    fun crawlStop_doesNotDropReadyConfidence() {
        WheelPulseOdometer.configure(TEST_K, 0.75f)
        sampleCorners(0, 0, 1L, speed = 2f)
        sampleCorners(6, 5, 2L, speed = 2f)
        val snap = WheelPulseOdometer.peekCalibration()
        assertEquals(0.75f, snap.confidence, 0.001f)
        assertTrue(snap.usableForDistance)
        assertTrue(WheelPulseOdometer.peekPulseSinceLastOdoM() > 0f)
    }

    @Test
    fun highwaySlip_dropsConfidenceAndDiscardsFraction() {
        WheelPulseOdometer.configure(TEST_K, 0.71f)
        sampleCorners(0, 0, 1L, speed = 80f)
        sampleCorners(100, 10, 2L, speed = 80f)
        val snap = WheelPulseOdometer.peekCalibration()
        assertEquals(0.69f, snap.confidence, 0.001f)
        assertFalse(snap.usableForDistance)
        assertEquals(0f, WheelPulseOdometer.peekPulseSinceLastOdoM(), 0f)
    }

    @Test
    fun turn_usesMeanFrontAndSkipsSlipGate() {
        WheelPulseOdometer.configure(TEST_K, 0.80f)
        sampleCorners(0, 0, 1L, speed = 40f, steer = 25f)
        sampleCorners(30, 10, 2L, speed = 40f, steer = 25f)
        assertEquals(0.80f, WheelPulseOdometer.peekCalibration().confidence, 0.001f)
        assertEquals(20f * TEST_K, WheelPulseOdometer.flushDrDistanceM(), 0.01f)
    }

    @Test
    fun tripHybrid_growsByPulseWithoutOdoTick() {
        WheelPulseOdometer.configure(TEST_K, 0.80f)
        WheelPulseOdometer.onOdometerKm(100u, 0L)
        var pulse = 0
        var t = 1L
        sample(pulse, t++)
        repeat(80) {
            pulse += 250
            sample(pulse, t++)
        }
        val frac = WheelPulseOdometer.peekPulseSinceLastOdoM()
        assertEquals(500f, frac, 1f)
        assertEquals(
            0.5f,
            TripPulseDistance.hybridKm(100u, 100u, frac),
            0.01f,
        )
    }

    @Test
    fun kmTick_hybridDoesNotJumpWholeKilometer() {
        val next = TripPulseDistance.resolveDistanceKm(
            currentDistanceKm = 0f,
            odoStartKm = 10u,
            odoNowKm = 11u,
            lastOdoKm = 10u,
            pulseSinceLastOdoM = 400f,
            hybridEnabled = true,
        )
        assertEquals(1.4f, next, 0.001f)
    }

    @Test
    fun firstOdoTick_withShortPulse_skipsNudge() {
        WheelPulseOdometer.configure(TEST_K, 0.80f)
        WheelPulseOdometer.onOdometerKm(5_640u, 0L)
        sample(0, 1L)
        sample(4_000, 2L)
        WheelPulseOdometer.onOdometerKm(5_641u, 3L)
        sample(4_010, 4L)
        assertEquals(TEST_K, WheelPulseOdometer.peekCalibration().metersPerPulse, 0.0001f)
        assertTrue(WheelPulseOdometer.peekDebugSnapshot().lastOdoNudgeSkipped)
    }

    private fun sample(pulse: Int, elapsed: Long, speed: Float = 80f) {
        sampleCorners(pulse, pulse, elapsed, speed = speed)
    }

    private fun sampleCorners(
        lhf: Int,
        rhf: Int,
        elapsed: Long,
        speed: Float = 80f,
        steer: Float = 0f,
    ) {
        val mask = COUNTER_MOD - 1
        WheelPulseOdometer.onWheelSample(
            counters = WheelCounters(lhf and mask, rhf and mask, lhf and mask, rhf and mask),
            reverse = false,
            steerDeg = steer,
            speedKmh = speed,
            nowElapsedMs = elapsed,
        )
    }

    companion object {
        private const val TEST_K = 0.025f
        private const val COUNTER_MOD = 8192
        private const val PULSES_PER_5KM = 210_000
        private const val CALIB_STEPS = 80
        private const val CALIB_STEP = PULSES_PER_5KM / CALIB_STEPS
    }
}
