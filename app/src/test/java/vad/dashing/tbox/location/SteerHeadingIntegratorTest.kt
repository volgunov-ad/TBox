package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class SteerHeadingIntegratorTest {

    @Before
    fun reset() {
        SteerHeadingIntegrator.reset()
        SteerCalibrationStore.reset()
    }

    @Test
    fun heldWheelWhileMovingTurnsThenCenterStops() {
        // scale=1/15, L=2.72, v=10 m/s, wheel=+150° → road=10°
        // ψ̇_deg = (v/L)*tan(roadRad)*180/π
        SteerCalibrationStore.update(
            SteerCalibrationOffsets(scale = 1f / 15f, sign = 1),
        )
        SteerHeadingIntegrator.onSpeedKmh(36f) // 10 m/s
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_200L) // hold 0.2 s
        val turned = SteerHeadingIntegrator.consumeDeltaDeg()
        assertTrue("expected left turn (negative nav), got $turned", turned < -0.5f)

        // Return wheel to center: the transition sample still uses the previous
        // held angle for that dt — discard it, then hold center.
        SteerHeadingIntegrator.onCenteredSample(0f, 1_400L)
        SteerHeadingIntegrator.consumeDeltaDeg()
        SteerHeadingIntegrator.onCenteredSample(0f, 1_600L)
        assertEquals(0f, SteerHeadingIntegrator.consumeDeltaDeg(), 0.05f)
    }

    @Test
    fun standstillWheelMoveDoesNotChangeHeading() {
        SteerHeadingIntegrator.onSpeedKmh(0f)
        SteerHeadingIntegrator.onCenteredSample(0f, 1_000L)
        SteerHeadingIntegrator.onCenteredSample(90f, 1_200L)
        assertEquals(0f, SteerHeadingIntegrator.consumeDeltaDeg(), 0f)
    }

    @Test
    fun appliesZeroThenIntegrates() {
        SteerCalibrationStore.update(
            SteerCalibrationOffsets(zeroDeg = 5f, scale = 1f / 15f, sign = 1),
        )
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.onRawSample(5f, 1_000L) // centered 0
        SteerHeadingIntegrator.onRawSample(5f + 150f, 1_200L)
        // First interval used centered=0 → no turn; need another hold sample
        SteerHeadingIntegrator.onRawSample(5f + 150f, 1_400L)
        val d = SteerHeadingIntegrator.consumeDeltaDeg()
        assertTrue(d < -0.5f)
    }

    @Test
    fun skipsLargeGapWithoutIntegrating() {
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        SteerHeadingIntegrator.onCenteredSample(150f, 3_000L) // 2 s > MAX
        assertEquals(0f, SteerHeadingIntegrator.consumeDeltaDeg(), 0f)
    }

    @Test
    fun discardClearsPending() {
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_100L)
        SteerHeadingIntegrator.discard()
        assertEquals(0f, SteerHeadingIntegrator.consumeDeltaDeg(), 0f)
    }

    @Test
    fun tickFlushesHeldAngle() {
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        SteerHeadingIntegrator.tick(1_200L)
        val d = SteerHeadingIntegrator.consumeDeltaDeg()
        assertTrue(d < -0.5f)
    }
}

class SteerCalibrationMathTest {

    @Test
    fun estimateSingleScaleFromLeftAndRightSegments() {
        // pathIntegral such that scale = −gnss/path = 0.08
        val segs = listOf(
            SteerCalibrationMath.SteerSegmentResult(pathIntegralDeg = 250f, gnssDeltaDeg = -20f),
            SteerCalibrationMath.SteerSegmentResult(pathIntegralDeg = 200f, gnssDeltaDeg = -16f),
            SteerCalibrationMath.SteerSegmentResult(pathIntegralDeg = -225f, gnssDeltaDeg = 18f),
            SteerCalibrationMath.SteerSegmentResult(pathIntegralDeg = -175f, gnssDeltaDeg = 14f),
        )
        val est = SteerCalibrationMath.estimateSteerScaleAndSign(segs)
        assertNotNull(est)
        assertEquals(1, est!!.sign)
        assertEquals(0.08f, est.scale, 0.01f)
    }

    @Test
    fun estimateSteerRequiresTwoArcsPerSide() {
        val oneSided = listOf(
            SteerCalibrationMath.SteerSegmentResult(250f, -20f),
            SteerCalibrationMath.SteerSegmentResult(200f, -16f),
            SteerCalibrationMath.SteerSegmentResult(-225f, 18f),
        )
        assertNull(SteerCalibrationMath.estimateSteerScaleAndSign(oneSided))
    }

    @Test
    fun steerFillReachesOneWithTwoPerSide() {
        assertEquals(0f, SteerCalibrationMath.steerFill(0, 0), 0f)
        assertEquals(0.5f, SteerCalibrationMath.steerFill(2, 0), 0.01f)
        assertEquals(1f, SteerCalibrationMath.steerFill(2, 2), 0f)
    }

    @Test
    fun collectSegmentsNeedsMotionAndHeldSteer() {
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        var t = 1_000L
        // Held +90° wheel at 40 km/h while GNSS course decreases (left turn)
        // Hold wheel; GNSS course rate chosen so |gnss|/|path| ≈ 0.08 (plausible scale).
        for (i in 0..50) {
            samples.add(
                SteerCalibrationMath.SteerSample(
                    centeredSteerDeg = 30f,
                    bearingDeg = 10f - i * 1.0f,
                    speedKmh = 40f,
                    elapsedMs = t,
                ),
            )
            t += 100L
        }
        val (segs, _) = SteerCalibrationMath.collectSteerSegments(samples)
        assertTrue(segs.isNotEmpty())
        assertTrue(segs.first().pathIntegralDeg > 0f)
        assertTrue(segs.first().gnssDeltaDeg < 0f)
    }

    @Test
    fun mergeReplacesSingleScale() {
        val prev = SteerCalibrationOffsets(scale = 0.05f, sign = 1)
        val est = SteerCalibrationMath.SteerScaleEstimate(
            sign = 1,
            scale = 0.08f,
            segmentCount = 4,
        )
        val merged = SteerCalibrationMath.mergeWithPrevious(est, prev, 99L)
        assertEquals(0.08f, merged.scale, 1e-4f)
        assertEquals(99L, merged.calibratedAtEpochMs)
    }

    @Test
    fun migrateLegacyDeltaScaleResetsToDefault() {
        assertEquals(
            SteerHeadingIntegrator.DEFAULT_SCALE,
            SteerCalibrationMath.migrateScale(1.0f),
            1e-4f,
        )
        assertEquals(0.1f, SteerCalibrationMath.migrateScale(0.1f), 1e-4f)
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

    @Test
    fun yawDeltaMatchesExpectedOrder() {
        val d = SteerHeadingIntegrator.yawDeltaDeg(
            centeredWheelDeg = 150f,
            speedMps = 10f,
            dtSec = 1.0,
            scale = 1f / 15f,
            sign = 1,
        )
        // (10/2.72)*tan(10°) * 180/π ≈ 3.67*0.1763*57.3/57.3 wait:
        // yawRateRad = (10/2.72)*tan(10°≈0.1763) ≈ 3.676*0.1763 ≈ 0.648 rad/s
        // yawDeg/s ≈ 37.1 → for 1s ≈ 37°, nav −37
        assertTrue(abs(d + 37.1f) < 2f)
    }
}
