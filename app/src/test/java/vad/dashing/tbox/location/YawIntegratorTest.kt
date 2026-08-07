package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YawIntegratorTest {

    @Before
    fun reset() {
        YawIntegrator.reset()
        GyroBiasStore.update(GyroBiasOffsets.ZERO)
        DriveCalibrationStore.reset()
    }

    @Test
    fun accumulatesFullTurnAcrossManySamples() {
        // 20 °/s left for 1.0 s at 50 Hz → −20° nav delta (not capped to −5°).
        var t = 1_000L
        YawIntegrator.onCalibratedSample(0f, t) // seed timestamp
        repeat(50) {
            t += 20L
            YawIntegrator.onCalibratedSample(20f, t)
        }
        val delta = YawIntegrator.consumeDeltaDeg()
        assertEquals(-20f, delta, 0.15f)
        assertEquals(0f, YawIntegrator.consumeDeltaDeg(), 0f)
    }

    @Test
    fun clampsLargeSampleGap() {
        YawIntegrator.onCalibratedSample(10f, 1_000L)
        // 2 s gap → only 0.25 s counted → −2.5°
        YawIntegrator.onCalibratedSample(10f, 3_000L)
        assertEquals(-2.5f, YawIntegrator.consumeDeltaDeg(), 1e-3f)
    }

    @Test
    fun deadbandSkipsNoise() {
        YawIntegrator.onCalibratedSample(0f, 1_000L)
        YawIntegrator.onCalibratedSample(0.4f, 1_020L)
        assertEquals(0f, YawIntegrator.consumeDeltaDeg(), 0f)
    }

    @Test
    fun discardClearsPending() {
        YawIntegrator.onCalibratedSample(0f, 1_000L)
        YawIntegrator.onCalibratedSample(20f, 1_100L)
        YawIntegrator.discard()
        assertEquals(0f, YawIntegrator.consumeDeltaDeg(), 0f)
    }

    @Test
    fun applyYawDeltaToBearingWraps() {
        assertEquals(
            350f,
            MockLocationJob.applyYawDeltaToBearing(10f, -20f),
            1e-3f,
        )
        assertEquals(
            90f,
            MockLocationJob.applyYawDeltaToBearing(80f, 10f),
            1e-3f,
        )
    }

    @Test
    fun onRawSampleAppliesBiasAndDriveScale() {
        GyroBiasStore.update(GyroBiasOffsets(yawDegPerSec = 2f))
        DriveCalibrationStore.update(
            DriveCalibrationOffsets(yawScaleLeft = 2f, yawScaleRight = 2f, yawSign = 1),
        )
        // raw 12 → debiased 10 → scaled 20 °/s for 0.1 s → −2°
        YawIntegrator.onRawSample(12f, 1_000L)
        YawIntegrator.onRawSample(12f, 1_100L)
        assertEquals(-2f, YawIntegrator.consumeDeltaDeg(), 1e-2f)
    }

    @Test
    fun onRawSampleAppliesDualScale() {
        DriveCalibrationStore.update(
            DriveCalibrationOffsets(yawScaleLeft = 2f, yawScaleRight = 0.5f, yawSign = 1),
        )
        YawIntegrator.onRawSample(10f, 1_000L)
        YawIntegrator.onRawSample(10f, 1_100L)
        assertEquals(-2f, YawIntegrator.consumeDeltaDeg(), 1e-2f)
        YawIntegrator.reset()
        YawIntegrator.onRawSample(-10f, 2_000L)
        YawIntegrator.onRawSample(-10f, 2_100L)
        assertEquals(0.5f, YawIntegrator.consumeDeltaDeg(), 1e-2f)
    }
}

class GnssFreshnessTest {

    @Test
    fun freshWithinWindow() {
        assertTrue(GnssFreshness.isFresh(1_000L, 1_000L + 2_999L))
        assertFalse(GnssFreshness.isFresh(1_000L, 1_000L + 3_000L))
        assertFalse(GnssFreshness.isFresh(null, 1_000L))
        assertFalse(GnssFreshness.isFresh(0L, 1_000L))
        assertFalse(GnssFreshness.isFresh(2_000L, 1_000L)) // future / clock skew → not fresh
    }

    @Test
    fun staleClearMatchesConstant() {
        assertEquals(3_000L, GnssFreshness.STALE_CLEAR_MS)
    }
}
