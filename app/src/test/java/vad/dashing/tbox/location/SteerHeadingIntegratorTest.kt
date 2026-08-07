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
        SteerCalibrationStore.update(
            SteerCalibrationOffsets(scale = 1f / 15f, sign = 1, deadzoneDeg = 2f),
        )
        SteerHeadingIntegrator.onSpeedKmh(36f) // 10 m/s
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_200L)
        val turned = SteerHeadingIntegrator.consumeDeltaDeg()
        assertTrue("expected left turn (negative nav), got $turned", turned < -0.5f)

        SteerHeadingIntegrator.onCenteredSample(0f, 1_400L)
        SteerHeadingIntegrator.consumeDeltaDeg()
        SteerHeadingIntegrator.onCenteredSample(0f, 1_600L)
        assertEquals(0f, SteerHeadingIntegrator.consumeDeltaDeg(), 0.05f)
    }

    @Test
    fun softDeadzoneRemovesSmallAngles() {
        assertEquals(0f, SteerCalibrationStore.softDeadzone(1.5f, 2f), 0f)
        assertEquals(1f, SteerCalibrationStore.softDeadzone(3f, 2f), 1e-4f)
        assertEquals(-1f, SteerCalibrationStore.softDeadzone(-3f, 2f), 1e-4f)
    }

    @Test
    fun standstillWheelMoveDoesNotChangeHeading() {
        SteerHeadingIntegrator.onSpeedKmh(0f)
        SteerHeadingIntegrator.onCenteredSample(0f, 1_000L)
        SteerHeadingIntegrator.onCenteredSample(90f, 1_200L)
        assertEquals(0f, SteerHeadingIntegrator.consumeDeltaDeg(), 0f)
    }

    @Test
    fun skipsLargeGapWithoutIntegrating() {
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        SteerHeadingIntegrator.onCenteredSample(150f, 3_000L)
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

    private fun syntheticArc(
        wheelDeg: Float,
        speedKmh: Float,
        scale: Float,
        sign: Int,
        gnssTargetDeg: Float,
        startBearing: Float,
        startMs: Long,
    ): List<SteerCalibrationMath.SteerSample> {
        // Build steps then set bearings from tan prediction so fit recovers [scale].
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        var t = startMs
        var bearing = startBearing
        samples.add(
            SteerCalibrationMath.SteerSample(wheelDeg, bearing, speedKmh, t),
        )
        val dtMs = 100L
        val steps = 40
        for (i in 1..steps) {
            t += dtMs
            val v = speedKmh / 3.6f
            val dPsi = SteerHeadingIntegrator.yawDeltaDeg(
                centeredWheelDeg = wheelDeg,
                speedMps = v,
                dtSec = dtMs / 1000.0,
                scale = scale,
                sign = sign,
                applyInternalDeadzone = true,
                deadzoneDeg = 2f,
            )
            bearing += dPsi
            samples.add(
                SteerCalibrationMath.SteerSample(wheelDeg, bearing, speedKmh, t),
            )
        }
        // Sanity: total should be near gnssTargetDeg
        val total = SteerCalibrationMath.wrapDeltaDeg(startBearing, bearing)
        assertTrue(abs(total - gnssTargetDeg) < abs(gnssTargetDeg) * 0.2f + 2f || abs(total) > 15f)
        return samples
    }

    @Test
    fun estimateScaleFromTanConsistentArcs() {
        SteerCalibrationStore.update(SteerCalibrationOffsets(deadzoneDeg = 2f))
        val trueScale = 0.08f
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        var t = 1_000L
        // 5 left + 5 right held-wheel arcs
        repeat(5) { idx ->
            val arc = syntheticArc(
                wheelDeg = 90f,
                speedKmh = 40f,
                scale = trueScale,
                sign = 1,
                gnssTargetDeg = -35f,
                startBearing = 90f - idx * 40f,
                startMs = t,
            )
            samples.addAll(arc)
            t = arc.last().elapsedMs + 2_000L
        }
        repeat(5) { idx ->
            val arc = syntheticArc(
                wheelDeg = -90f,
                speedKmh = 40f,
                scale = trueScale,
                sign = 1,
                gnssTargetDeg = 35f,
                startBearing = -90f + idx * 40f,
                startMs = t,
            )
            samples.addAll(arc)
            t = arc.last().elapsedMs + 2_000L
        }
        val (segs, _) = SteerCalibrationMath.collectSteerSegments(samples)
        assertTrue("segs=${segs.size}", segs.size >= 10)
        val est = SteerCalibrationMath.estimateSteerScaleAndSign(segs, deadzoneDeg = 2f)
        assertNotNull(est)
        assertEquals(1, est!!.sign)
        assertEquals(trueScale, est.scale, 0.02f)
        assertTrue(est.leftCount >= 5)
        assertTrue(est.rightCount >= 5)
    }

    @Test
    fun estimateSteerRequiresFiveArcsPerSide() {
        val steps = listOf(
            SteerCalibrationMath.PathStep(90f, 10f, 0.1f),
        )
        val few = List(4) {
            SteerCalibrationMath.SteerSegmentResult(steps, -20f, 100f)
        } + List(4) {
            SteerCalibrationMath.SteerSegmentResult(steps, 20f, -100f)
        }
        // Not enough per side for MIN=5
        assertNull(SteerCalibrationMath.estimateSteerScaleAndSign(few, deadzoneDeg = 2f))
    }

    @Test
    fun steerFillReachesOneWithFivePerSide() {
        assertEquals(0f, SteerCalibrationMath.steerFill(0, 0), 0f)
        assertEquals(0.5f, SteerCalibrationMath.steerFill(5, 0), 0.01f)
        assertEquals(1f, SteerCalibrationMath.steerFill(5, 5), 0f)
    }

    @Test
    fun attemptUsesFittedCountsAndSurvivesOneOutlier() {
        val trueScale = 1f / 14f
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        var t = 0L
        repeat(5) { idx ->
            val arc = syntheticArc(
                wheelDeg = 90f,
                speedKmh = 40f,
                scale = trueScale,
                sign = 1,
                gnssTargetDeg = -35f,
                startBearing = 90f - idx * 40f,
                startMs = t,
            )
            samples.addAll(arc)
            t = arc.last().elapsedMs + 2_000L
        }
        repeat(5) { idx ->
            val arc = syntheticArc(
                wheelDeg = -90f,
                speedKmh = 40f,
                scale = trueScale,
                sign = 1,
                gnssTargetDeg = 35f,
                startBearing = -90f + idx * 40f,
                startMs = t,
            )
            samples.addAll(arc)
            t = arc.last().elapsedMs + 2_000L
        }
        // Extra wild arc (GNSS jump) that would inflate untrimmed max−min spread.
        val bad = syntheticArc(
            wheelDeg = 90f,
            speedKmh = 40f,
            scale = trueScale * 2.2f,
            sign = 1,
            gnssTargetDeg = -55f,
            startBearing = 180f,
            startMs = t,
        )
        samples.addAll(bad)
        val (segs, _) = SteerCalibrationMath.collectSteerSegments(samples)
        val attempt = SteerCalibrationMath.attemptSteerScaleAndSign(segs, deadzoneDeg = 2f)
        assertNotNull("estimate blocked; fitted L/R=${attempt.fittedLeft}/${attempt.fittedRight} fail=${attempt.failure}", attempt.estimate)
        assertTrue(attempt.fittedLeft >= 5)
        assertTrue(attempt.fittedRight >= 5)
        assertEquals(trueScale, attempt.estimate!!.scale, 0.03f)
    }

    @Test
    fun mergeReplacesSingleScale() {
        val prev = SteerCalibrationOffsets(scale = 0.05f, sign = 1, deadzoneDeg = 2f)
        val est = SteerCalibrationMath.SteerScaleEstimate(
            sign = 1,
            scale = 0.08f,
            segmentCount = 10,
        )
        val merged = SteerCalibrationMath.mergeWithPrevious(est, prev, 99L)
        assertEquals(0.08f, merged.scale, 1e-4f)
        assertEquals(2f, merged.deadzoneDeg, 0f)
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
        assertEquals(2f, SteerCalibrationMath.migrateDeadzone(null), 0f)
        assertEquals(3f, SteerCalibrationMath.migrateDeadzone(3f), 0f)
    }

    @Test
    fun fromStorageDefaultsToGyro() {
        assertEquals(MockHeadingSource.GYRO, MockHeadingSource.fromStorage(null))
        assertEquals(MockHeadingSource.STEER, MockHeadingSource.fromStorage("STEER"))
    }

    @Test
    fun tooFewSegmentsReturnsNull() {
        assertNull(
            SteerCalibrationMath.estimateSteerScaleAndSign(
                listOf(
                    SteerCalibrationMath.SteerSegmentResult(
                        steps = emptyList(),
                        gnssDeltaDeg = -10f,
                        pathIntegralDeg = 50f,
                    ),
                ),
            ),
        )
    }
}
