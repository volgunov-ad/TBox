package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SteerHeadingIntegratorTest {

    @Before
    fun reset() {
        SteerHeadingIntegrator.reset()
        SteerCalibrationStore.reset()
    }

    @Test
    fun accumulatesDeltaWithDefaultScale() {
        // +10° wheel over 0.2 s → bearing −10° (sign=+1, scale=1)
        SteerHeadingIntegrator.onCenteredSample(0f, 1_000L)
        SteerHeadingIntegrator.onCenteredSample(10f, 1_200L)
        assertEquals(-10f, SteerHeadingIntegrator.consumeDeltaDeg(), 1e-3f)
    }

    @Test
    fun appliesZeroAndSingleScaleBothWays() {
        SteerCalibrationStore.update(
            SteerCalibrationOffsets(zeroDeg = 5f, scale = 0.2f, sign = 1),
        )
        // raw 5→15 → centered 0→10 → scale 0.2 → bearing −2°
        SteerHeadingIntegrator.onRawSample(5f, 1_000L)
        SteerHeadingIntegrator.onRawSample(15f, 1_200L)
        assertEquals(-2f, SteerHeadingIntegrator.consumeDeltaDeg(), 1e-3f)

        SteerHeadingIntegrator.reset()
        // raw 5→0 → centered 0→−5 → same scale 0.2 → bearing +1°
        SteerHeadingIntegrator.onRawSample(5f, 2_000L)
        SteerHeadingIntegrator.onRawSample(0f, 2_200L)
        assertEquals(1f, SteerHeadingIntegrator.consumeDeltaDeg(), 1e-3f)
    }

    @Test
    fun skipsLargeGapWithoutIntegrating() {
        SteerHeadingIntegrator.onCenteredSample(0f, 1_000L)
        SteerHeadingIntegrator.onCenteredSample(20f, 3_000L) // 2 s > MAX
        assertEquals(0f, SteerHeadingIntegrator.consumeDeltaDeg(), 0f)
    }

    @Test
    fun discardClearsPending() {
        SteerHeadingIntegrator.onCenteredSample(0f, 1_000L)
        SteerHeadingIntegrator.onCenteredSample(10f, 1_100L)
        SteerHeadingIntegrator.discard()
        assertEquals(0f, SteerHeadingIntegrator.consumeDeltaDeg(), 0f)
    }
}

class SteerCalibrationMathTest {

    @Test
    fun estimateSingleScaleFromLeftAndRightSegments() {
        val segs = listOf(
            SteerCalibrationMath.SteerSegmentResult(steerIntegralDeg = 100f, gnssDeltaDeg = -20f),
            SteerCalibrationMath.SteerSegmentResult(steerIntegralDeg = 80f, gnssDeltaDeg = -16f),
            SteerCalibrationMath.SteerSegmentResult(steerIntegralDeg = -90f, gnssDeltaDeg = 18f),
            SteerCalibrationMath.SteerSegmentResult(steerIntegralDeg = -70f, gnssDeltaDeg = 14f),
        )
        val est = SteerCalibrationMath.estimateSteerScaleAndSign(segs)
        assertNotNull(est)
        assertEquals(1, est!!.sign)
        assertEquals(0.2f, est.scale, 0.02f)
    }

    @Test
    fun estimateSteerRequiresTwoArcsPerSide() {
        val oneSided = listOf(
            SteerCalibrationMath.SteerSegmentResult(100f, -20f),
            SteerCalibrationMath.SteerSegmentResult(80f, -16f),
            SteerCalibrationMath.SteerSegmentResult(-90f, 18f),
        )
        assertNull(SteerCalibrationMath.estimateSteerScaleAndSign(oneSided))
    }

    @Test
    fun collectSegmentsNeedsMotionAndTurn() {
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        var t = 1_000L
        for (i in 0..5) {
            samples.add(
                SteerCalibrationMath.SteerSample(
                    centeredSteerDeg = 0f,
                    bearingDeg = 10f,
                    speedKmh = 40f,
                    elapsedMs = t,
                ),
            )
            t += 100L
        }
        for (i in 1..10) {
            samples.add(
                SteerCalibrationMath.SteerSample(
                    centeredSteerDeg = i * 10f,
                    bearingDeg = 10f - i * 2f,
                    speedKmh = 40f,
                    elapsedMs = t,
                ),
            )
            t += 100L
        }
        val (segs, _) = SteerCalibrationMath.collectSteerSegments(samples)
        assertTrue(segs.isNotEmpty())
        assertTrue(segs.first().steerIntegralDeg > 0f)
        assertTrue(segs.first().gnssDeltaDeg < 0f)
    }

    @Test
    fun mergeReplacesSingleScale() {
        val prev = SteerCalibrationOffsets(scale = 0.15f, sign = 1)
        val est = SteerCalibrationMath.SteerScaleEstimate(
            sign = 1,
            scale = 0.18f,
            segmentCount = 4,
        )
        val merged = SteerCalibrationMath.mergeWithPrevious(est, prev, 99L)
        assertEquals(0.18f, merged.scale, 1e-4f)
        assertEquals(99L, merged.calibratedAtEpochMs)
    }

    @Test
    fun fromStorageDefaultsToGyro() {
        assertEquals(MockHeadingSource.GYRO, MockHeadingSource.fromStorage(null))
        assertEquals(MockHeadingSource.STEER, MockHeadingSource.fromStorage("STEER"))
        assertEquals(MockHeadingSource.GYRO, MockHeadingSource.fromStorage("nope"))
    }

    @Test
    fun tooFewSegmentsReturnsNull() {
        assertNull(
            SteerCalibrationMath.estimateSteerScaleAndSign(
                listOf(
                    SteerCalibrationMath.SteerSegmentResult(50f, -10f),
                ),
            ),
        )
    }
}
