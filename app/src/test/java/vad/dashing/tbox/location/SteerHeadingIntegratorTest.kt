package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            SteerCalibrationOffsets(
                scaleProfile = SteerScaleProfile.uniform(SteerHeadingIntegrator.DEFAULT_SCALE),
                sign = 1,
                deadzoneDeg = 2f,
            ),
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
    fun longerWheelbaseReducesYawForSameSteer() {
        val short = SteerHeadingIntegrator.yawDeltaDeg(
            centeredWheelDeg = 90f,
            speedMps = 10f,
            dtSec = 1.0,
            scale = SteerHeadingIntegrator.DEFAULT_SCALE,
            sign = 1,
            applyInternalDeadzone = true,
            deadzoneDeg = 2f,
            wheelbaseM = 2.5f,
        )
        val long = SteerHeadingIntegrator.yawDeltaDeg(
            centeredWheelDeg = 90f,
            speedMps = 10f,
            dtSec = 1.0,
            scale = SteerHeadingIntegrator.DEFAULT_SCALE,
            sign = 1,
            applyInternalDeadzone = true,
            deadzoneDeg = 2f,
            wheelbaseM = 3.2f,
        )
        assertTrue(abs(short) > abs(long))
        assertEquals(abs(short) * 2.5f, abs(long) * 3.2f, 0.05f)
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

    @Test
    fun staleHeldAngleStopsIntegratingAfterMaxAge() {
        SteerCalibrationStore.update(
            SteerCalibrationOffsets(
                scaleProfile = SteerScaleProfile.uniform(SteerHeadingIntegrator.DEFAULT_SCALE),
                sign = 1,
                deadzoneDeg = 2f,
            ),
        )
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        assertTrue(SteerHeadingIntegrator.isAngleFresh(1_500L))
        SteerHeadingIntegrator.tick(1_000L + SteerHeadingIntegrator.MAX_ANGLE_SAMPLE_AGE_MS)
        val withinWindow = SteerHeadingIntegrator.consumeDeltaDeg()
        assertTrue("expected turn within freshness window, got $withinWindow", withinWindow < -0.5f)

        // Far beyond freshness (field: ~30 s mbCAN poll) — must not keep turning.
        assertFalse(SteerHeadingIntegrator.isAngleFresh(40_000L))
        SteerHeadingIntegrator.tick(40_000L)
        assertEquals(0f, SteerHeadingIntegrator.consumeDeltaDeg(), 0.05f)
        assertFalse(SteerHeadingIntegrator.isAngleFresh(40_000L))
    }

    @Test
    fun tickFlushesHeldAngleAcrossOneSecondMockPeriod() {
        // Default mock period is 1 s > MAX_SAMPLE_DT_SEC (0.5). Chunked tick must
        // still integrate a held wheel; previously dt>0.5 skipped the whole turn.
        SteerCalibrationStore.update(
            SteerCalibrationOffsets(
                scaleProfile = SteerScaleProfile.uniform(SteerHeadingIntegrator.DEFAULT_SCALE),
                sign = 1,
                deadzoneDeg = 2f,
            ),
        )
        SteerHeadingIntegrator.onSpeedKmh(36f) // 10 m/s
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        SteerHeadingIntegrator.tick(2_000L)
        val d = SteerHeadingIntegrator.consumeDeltaDeg()
        val expected = SteerHeadingIntegrator.yawDeltaDeg(
            centeredWheelDeg = 150f,
            speedMps = 10f,
            dtSec = 1.0,
            scale = SteerHeadingIntegrator.DEFAULT_SCALE,
            sign = 1,
            applyInternalDeadzone = true,
            deadzoneDeg = 2f,
        )
        assertEquals(expected, d, 0.15f)
        assertTrue("expected ~1 s held turn, got $d", abs(d) > 2f)
    }

    @Test
    fun onSpeedKmhWithElapsedAdvancesHeldWheel() {
        SteerCalibrationStore.update(
            SteerCalibrationOffsets(
                scaleProfile = SteerScaleProfile.uniform(SteerHeadingIntegrator.DEFAULT_SCALE),
                sign = 1,
                deadzoneDeg = 2f,
            ),
        )
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        // Speed changes without a new angle emit — still integrate.
        SteerHeadingIntegrator.onSpeedKmh(36f, 1_500L)
        SteerHeadingIntegrator.onSpeedKmh(18f, 2_000L)
        val d = SteerHeadingIntegrator.consumeDeltaDeg()
        assertTrue("speed-driven hold should turn, got $d", d < -1f)
    }

    @Test
    fun discardThroughPreventsLiveGapBackfill() {
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        SteerHeadingIntegrator.tick(1_200L)
        // Live gap >> MAX_ANGLE_SAMPLE_AGE_MS clears the held angle (stale).
        SteerHeadingIntegrator.discardThrough(5_000L)
        assertEquals(0f, SteerHeadingIntegrator.consumeDeltaDeg(), 0f)
        assertFalse(SteerHeadingIntegrator.isAngleFresh(5_000L))
        // Fresh sample at resume — only the small post-gap slice integrates.
        SteerHeadingIntegrator.onCenteredSample(150f, 5_000L)
        SteerHeadingIntegrator.tick(5_200L)
        val d = SteerHeadingIntegrator.consumeDeltaDeg()
        val expected = SteerHeadingIntegrator.yawDeltaDeg(
            centeredWheelDeg = 150f,
            speedMps = 10f,
            dtSec = 0.2,
            scale = SteerCalibrationStore.offsets.scaleProfile.scaleAt(36f),
            sign = 1,
            applyInternalDeadzone = true,
            deadzoneDeg = 2f,
        )
        assertEquals(expected, d, 0.1f)
    }

    @Test
    fun reverseLeftSteerSendsPathLeftBackward() {
        // Nose east (90°). Left wheel + reverse: bicycle ψ̇ = (v/L)tan(δ) with v<0
        // yaws the nose clockwise (nav +), travel = nose+180 goes west with a
        // north component — rear moves left while backing (parking-lot rule).
        SteerCalibrationStore.update(
            SteerCalibrationOffsets(
                scaleProfile = SteerScaleProfile.uniform(SteerHeadingIntegrator.DEFAULT_SCALE),
                sign = 1,
                deadzoneDeg = 2f,
            ),
        )
        val nose0 = 90f
        SteerHeadingIntegrator.onSpeedKmh(-36f) // reverse 10 m/s
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        SteerHeadingIntegrator.tick(2_000L)
        val dNose = SteerHeadingIntegrator.consumeDeltaDeg()
        assertTrue("reverse+left should increase nav nose (CW), got $dNose", dNose > 2f)
        val nose1 = MockLocationJob.applyYawDeltaToBearing(nose0, dNose)
        val midNose = MockLocationJob.averageBearingDeg(nose0, nose1)
        val travel = ConstantDrMath.travelBearingFromNoseHeading(midNose, reverse = true)
        val (lat1, lon1) = ConstantDrMath.extrapolateLatLon(
            lat = 55.0,
            lon = 37.0,
            bearingDeg = travel,
            distanceM = 10.0,
        )
        // West of start (lon decreases in northern hemisphere approx for west) and north.
        assertTrue("expected north displacement, dLat=${lat1 - 55.0}", lat1 > 55.0)
        assertTrue("expected west displacement, dLon=${lon1 - 37.0}", lon1 < 37.0)
        // Forward + same wheel must yaw the opposite way (nav −).
        SteerHeadingIntegrator.reset()
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.onCenteredSample(150f, 1_000L)
        SteerHeadingIntegrator.tick(2_000L)
        val dFwd = SteerHeadingIntegrator.consumeDeltaDeg()
        assertTrue(dFwd < -2f)
        assertEquals(-dFwd, dNose, 0.2f)
    }

    @Test
    fun gyroAndSteerSameIntervalMatchSpeedFlushPattern() {
        // Mirrors MockLocationJob: discardThrough while "live", then one DR tick.
        // Refresh the angle each second so freshness holds across the live gap
        // (without samples, discardThrough past MAX_ANGLE_SAMPLE_AGE_MS clears hold).
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.onCenteredSample(90f, 1_000L)
        var t = 1_000L
        repeat(5) {
            t += 1_000L
            SteerHeadingIntegrator.onCenteredSample(90f, t)
            SteerHeadingIntegrator.discardThrough(t)
        }
        // Fix loss: one mock period of held turn (sample at t stays fresh through t+1s).
        SteerHeadingIntegrator.onSpeedKmh(36f)
        SteerHeadingIntegrator.tick(t + 1_000L)
        val d = SteerHeadingIntegrator.consumeDeltaDeg()
        val expected = SteerHeadingIntegrator.yawDeltaDeg(
            centeredWheelDeg = 90f,
            speedMps = 10f,
            dtSec = 1.0,
            scale = SteerCalibrationStore.offsets.scaleProfile.scaleAt(36f),
            sign = 1,
            applyInternalDeadzone = true,
            deadzoneDeg = SteerCalibrationStore.offsets.deadzoneDeg,
        )
        assertEquals(expected, d, 0.15f)
    }

    @Test
    fun speedProfileInterpolatesAndClampsEndpoints() {
        val profile = SteerScaleProfile(
            at20Kmh = 0.10f,
            at40Kmh = 0.08f,
            at60Kmh = 0.06f,
            at80Kmh = 0.04f,
        )
        assertEquals(0.10f, profile.scaleAt(5f), 1e-5f)
        assertEquals(0.09f, profile.scaleAt(30f), 1e-5f)
        assertEquals(0.07f, profile.scaleAt(50f), 1e-5f)
        assertEquals(0.04f, profile.scaleAt(120f), 1e-5f)
        assertEquals(0.07f, profile.scaleAt(-50f), 1e-5f)
    }

    @Test
    fun runtimeUsesSpeedDependentScale() {
        SteerCalibrationStore.update(
            SteerCalibrationOffsets(
                scaleProfile = SteerScaleProfile(
                    at20Kmh = 0.10f,
                    at40Kmh = 0.08f,
                    at60Kmh = 0.06f,
                    at80Kmh = 0.04f,
                ),
                deadzoneDeg = 0f,
            ),
        )
        val lowSpeedDelta = abs(SteerCalibrationStore.yawDeltaDeg(90f, 20f / 3.6f, 1.0))
        val highSpeedProfileDelta = abs(SteerCalibrationStore.yawDeltaDeg(90f, 80f / 3.6f, 1.0))
        val highSpeedUniformDelta = abs(
            SteerHeadingIntegrator.yawDeltaDeg(
                centeredWheelDeg = 90f,
                speedMps = 80f / 3.6f,
                dtSec = 1.0,
                scale = 0.10f,
                sign = 1,
                applyInternalDeadzone = false,
            ),
        )
        assertTrue(highSpeedProfileDelta > lowSpeedDelta)
        assertTrue(highSpeedProfileDelta < highSpeedUniformDelta * 0.5f)
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
            val speed = listOf(20f, 40f, 60f, 80f, 40f)[idx]
            val arc = syntheticArc(
                wheelDeg = 90f,
                speedKmh = speed,
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
            val speed = listOf(20f, 40f, 60f, 80f, 60f)[idx]
            val arc = syntheticArc(
                wheelDeg = -90f,
                speedKmh = speed,
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
    fun estimateSpeedDependentProfileFromVariedTurns() {
        SteerCalibrationStore.update(SteerCalibrationOffsets(deadzoneDeg = 2f))
        val speedScalePairs = listOf(
            20f to 0.10f,
            40f to 0.085f,
            60f to 0.07f,
            80f to 0.055f,
            40f to 0.085f,
        )
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        var t = 1_000L
        for ((index, pair) in speedScalePairs.withIndex()) {
            val arc = syntheticArc(
                wheelDeg = 90f,
                speedKmh = pair.first,
                scale = pair.second,
                sign = 1,
                gnssTargetDeg = -35f,
                startBearing = 90f - index * 40f,
                startMs = t,
            )
            samples.addAll(arc)
            t = arc.last().elapsedMs + 2_000L
        }
        for ((index, pair) in speedScalePairs.withIndex()) {
            val arc = syntheticArc(
                wheelDeg = -90f,
                speedKmh = pair.first,
                scale = pair.second,
                sign = 1,
                gnssTargetDeg = 35f,
                startBearing = -90f + index * 40f,
                startMs = t,
            )
            samples.addAll(arc)
            t = arc.last().elapsedMs + 2_000L
        }
        val (segments, _) = SteerCalibrationMath.collectSteerSegments(samples)
        val attempt = SteerCalibrationMath.attemptSteerScaleAndSign(segments, deadzoneDeg = 2f)
        assertNotNull("failure=${attempt.failure}", attempt.estimate)
        assertEquals(4, attempt.profileSpeedBuckets)
        assertTrue(attempt.profileBucketCounts.all { it >= 2 })
        val profile = attempt.estimate!!.scaleProfile
        assertEquals(0.10f, profile.at20Kmh, 0.02f)
        assertEquals(0.085f, profile.at40Kmh, 0.02f)
        assertEquals(0.07f, profile.at60Kmh, 0.02f)
        assertEquals(0.055f, profile.at80Kmh, 0.02f)
    }

    @Test
    fun estimateProfileRequiresTurnsAcrossThreeSpeedBands() {
        val steps = List(5) {
            SteerCalibrationMath.PathStep(90f, 40f / 3.6f, 0.1f)
        }
        val delta = SteerCalibrationMath.predictGnssDelta(steps, 0.08f, 1, 2f)
        val segments = List(5) {
            SteerCalibrationMath.SteerSegmentResult(steps, delta, 100f)
        } + List(5) {
            SteerCalibrationMath.SteerSegmentResult(
                steps.map { it.copy(centeredSteerDeg = -90f) },
                -delta,
                -100f,
            )
        }
        val attempt = SteerCalibrationMath.attemptSteerScaleAndSign(segments, deadzoneDeg = 2f)
        assertNull(attempt.estimate)
        assertEquals(SteerCalibrationMath.SteerEstimateFailure.NEED_SPEED_RANGE, attempt.failure)
        assertEquals(1, attempt.profileSpeedBuckets)
        assertEquals(listOf(0, 10, 0, 0), attempt.profileBucketCounts)
    }

    @Test
    fun attemptReportsSpeedProgressBeforeEnoughTotalArcs() {
        fun segment(speedKmh: Float, wheelDeg: Float): SteerCalibrationMath.SteerSegmentResult {
            val steps = List(20) {
                SteerCalibrationMath.PathStep(wheelDeg, speedKmh / 3.6f, 0.1f)
            }
            return SteerCalibrationMath.SteerSegmentResult(
                steps = steps,
                gnssDeltaDeg = SteerCalibrationMath.predictGnssDelta(
                    steps = steps,
                    scale = 0.08f,
                    sign = 1,
                    deadzoneDeg = 2f,
                ),
                pathIntegralDeg = if (wheelDeg > 0f) 100f else -100f,
            )
        }
        val attempt = SteerCalibrationMath.attemptSteerScaleAndSign(
            segments = listOf(
                segment(20f, 90f),
                segment(20f, -90f),
                segment(40f, 90f),
                segment(40f, -90f),
            ),
            deadzoneDeg = 2f,
        )
        assertNull(attempt.estimate)
        assertEquals(SteerCalibrationMath.SteerEstimateFailure.NEED_MORE_ARCS, attempt.failure)
        assertEquals(listOf(2, 2, 0, 0), attempt.profileBucketCounts)
        assertEquals(2, attempt.profileSpeedBuckets)
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
            val speed = listOf(20f, 40f, 60f, 80f, 40f)[idx]
            val arc = syntheticArc(
                wheelDeg = 90f,
                speedKmh = speed,
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
            val speed = listOf(20f, 40f, 60f, 80f, 60f)[idx]
            val arc = syntheticArc(
                wheelDeg = -90f,
                speedKmh = speed,
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
    fun straightWithSlightSteerDoesNotInflateRejected() {
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        // ~40 s nearly straight, wheel held ~12° (above start gate) but GNSS course flat.
        for (i in 0..400) {
            samples.add(
                SteerCalibrationMath.SteerSample(
                    centeredSteerDeg = 12f,
                    bearingDeg = 90f + i * 0.005f,
                    speedKmh = 50f,
                    elapsedMs = i * 100L,
                ),
            )
        }
        val (segs, rejected) = SteerCalibrationMath.collectSteerSegments(samples)
        assertTrue(segs.isEmpty())
        assertEquals(0, rejected)
    }

    @Test
    fun estimateAllowsMildLeftRightMedianGap() {
        // Each side tight around its median; combined (max−min)/median would exceed 0.40.
        val leftScale = 0.070f
        val rightScale = 0.095f
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        var t = 0L
        repeat(5) { idx ->
            val speed = listOf(20f, 40f, 60f, 80f, 40f)[idx]
            val arc = syntheticArc(
                wheelDeg = 90f,
                speedKmh = speed,
                scale = leftScale,
                sign = 1,
                gnssTargetDeg = -35f,
                startBearing = 90f - idx * 40f,
                startMs = t,
            )
            samples.addAll(arc)
            t = arc.last().elapsedMs + 2_000L
        }
        repeat(5) { idx ->
            val speed = listOf(20f, 40f, 60f, 80f, 60f)[idx]
            val arc = syntheticArc(
                wheelDeg = -90f,
                speedKmh = speed,
                scale = rightScale,
                sign = 1,
                gnssTargetDeg = 35f,
                startBearing = -90f + idx * 40f,
                startMs = t,
            )
            samples.addAll(arc)
            t = arc.last().elapsedMs + 2_000L
        }
        val (segs, _) = SteerCalibrationMath.collectSteerSegments(samples)
        val attempt = SteerCalibrationMath.attemptSteerScaleAndSign(segs, deadzoneDeg = 2f)
        assertNotNull(
            "expected estimate with per-side spread; fail=${attempt.failure} " +
                "L/R=${attempt.fittedLeft}/${attempt.fittedRight}",
            attempt.estimate,
        )
        val expected = (leftScale + rightScale) * 0.5f
        assertEquals(expected, attempt.estimate!!.scale, 0.02f)
    }

    @Test
    fun nearCenterWheelOnStraightIsSkipped() {
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        for (i in 0..200) {
            samples.add(
                SteerCalibrationMath.SteerSample(
                    centeredSteerDeg = if (i % 2 == 0) 3f else -2f,
                    bearingDeg = 45f,
                    speedKmh = 40f,
                    elapsedMs = i * 100L,
                ),
            )
        }
        val (segs, rejected) = SteerCalibrationMath.collectSteerSegments(samples)
        assertTrue(segs.isEmpty())
        assertEquals(0, rejected)
    }

    @Test
    fun realTurnStillAcceptedAfterStraightPadding() {
        val trueScale = 1f / 14f
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        // Straight padding first.
        for (i in 0..100) {
            samples.add(
                SteerCalibrationMath.SteerSample(
                    centeredSteerDeg = 2f,
                    bearingDeg = 90f,
                    speedKmh = 40f,
                    elapsedMs = i * 100L,
                ),
            )
        }
        val t = samples.last().elapsedMs + 2_000L
        samples.addAll(
            syntheticArc(
                wheelDeg = 90f,
                speedKmh = 40f,
                scale = trueScale,
                sign = 1,
                gnssTargetDeg = -35f,
                startBearing = 90f,
                startMs = t,
            ),
        )
        val (segs, rejected) = SteerCalibrationMath.collectSteerSegments(samples)
        assertTrue("segs=${segs.size}", segs.isNotEmpty())
        assertEquals(0, rejected)
    }

    @Test
    fun mergeReplacesSingleScale() {
        val prev = SteerCalibrationOffsets(
            scaleProfile = SteerScaleProfile.uniform(0.05f),
            sign = 1,
            deadzoneDeg = 2f,
            wheelbaseM = 2.9f,
        )
        val est = SteerCalibrationMath.SteerScaleEstimate(
            sign = 1,
            scaleProfile = SteerScaleProfile(
                at20Kmh = 0.09f,
                at40Kmh = 0.08f,
                at60Kmh = 0.07f,
                at80Kmh = 0.06f,
            ),
            segmentCount = 10,
        )
        val merged = SteerCalibrationMath.mergeWithPrevious(est, prev, 99L)
        assertEquals(0.08f, merged.scale, 1e-4f)
        assertEquals(0.09f, merged.scaleProfile.at20Kmh, 1e-4f)
        assertEquals(0.06f, merged.scaleProfile.at80Kmh, 1e-4f)
        assertEquals(2f, merged.deadzoneDeg, 0f)
        assertEquals(2.9f, merged.wheelbaseM, 0f)
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
        assertEquals(
            SteerHeadingIntegrator.DEFAULT_WHEELBASE_M,
            SteerCalibrationMath.migrateWheelbase(null),
            0f,
        )
        assertEquals(2.9f, SteerCalibrationMath.migrateWheelbase(2.9f), 0f)
        assertEquals(
            SteerCalibrationOffsets.WHEELBASE_EDIT_MAX,
            SteerCalibrationMath.migrateWheelbase(9f),
            0f,
        )
    }

    @Test
    fun migrateScaleProfileIgnoresMissingKnotsWithDefaults() {
        val missing = SteerCalibrationMath.migrateScaleProfile(null, null, null, null)
        assertEquals(SteerScaleProfile.DEFAULT, missing)
        assertEquals(0.072f, missing.at20Kmh, 1e-4f)
        assertEquals(0.072f, missing.at40Kmh, 1e-4f)
        assertEquals(0.042f, missing.at60Kmh, 1e-4f)
        assertEquals(0.033f, missing.at80Kmh, 1e-4f)

        val partial = SteerCalibrationMath.migrateScaleProfile(
            at20Kmh = 0.09f,
            at40Kmh = null,
            at60Kmh = 0.05f,
            at80Kmh = null,
        )
        assertEquals(0.09f, partial.at20Kmh, 1e-4f)
        assertEquals(SteerScaleProfile.DEFAULT_SCALE_40_KMH, partial.at40Kmh, 1e-4f)
        assertEquals(0.05f, partial.at60Kmh, 1e-4f)
        assertEquals(SteerScaleProfile.DEFAULT_SCALE_80_KMH, partial.at80Kmh, 1e-4f)
    }

    @Test
    fun defaultProfileUsesGnssFitAndInterpolates() {
        val profile = SteerScaleProfile.DEFAULT
        assertEquals(0.072f, profile.scaleAt(20f), 1e-5f)
        assertEquals(0.072f, profile.scaleAt(40f), 1e-5f)
        assertEquals(0.057f, profile.scaleAt(50f), 1e-5f)
        assertEquals(0.033f, profile.scaleAt(100f), 1e-5f)
    }

    @Test
    fun fromStorageDefaultsToGyro() {
        assertEquals(MockHeadingSource.GYRO, MockHeadingSource.fromStorage(null))
        assertEquals(MockHeadingSource.STEER, MockHeadingSource.fromStorage("STEER"))
        assertEquals(MockHeadingSource.GYRO_STEER, MockHeadingSource.fromStorage("GYRO_STEER"))
        assertTrue(MockHeadingSource.GYRO_STEER.usesGyro)
        assertTrue(MockHeadingSource.GYRO_STEER.usesSteer)
        assertTrue(MockHeadingSource.GYRO.usesGyro)
        assertFalse(MockHeadingSource.GYRO.usesSteer)
        assertFalse(MockHeadingSource.STEER.usesGyro)
        assertTrue(MockHeadingSource.STEER.usesSteer)
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
