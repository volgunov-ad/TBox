package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues
import kotlin.math.abs

class DriveCalibrationMathTest {

    @Test
    fun wrapDeltaHandles360() {
        assertEquals(10f, DriveCalibrationMath.wrapDeltaDeg(350f, 0f), 0.01f)
        assertEquals(-10f, DriveCalibrationMath.wrapDeltaDeg(0f, 350f), 0.01f)
    }

    @Test
    fun medianOddEven() {
        assertEquals(2f, DriveCalibrationMath.median(listOf(1f, 2f, 3f))!!, 0f)
        assertEquals(2.5f, DriveCalibrationMath.median(listOf(1f, 2f, 3f, 4f))!!, 0f)
    }

    @Test
    fun estimateLagPicksDelayedGnss() {
        val lag = 400L
        val buf = ArrayList<DriveCalibrationMath.SpeedSample>()
        for (i in 0..80) {
            val t = i * 100L
            val can = if (t >= 1000L) 60f else 40f
            val gnss = if (t >= 1000L + lag) 60f else 40f
            buf.add(DriveCalibrationMath.SpeedSample(t, gnss, can))
        }
        val estLag = DriveCalibrationMath.estimateLagMs(buf)
        assertTrue("lag=$estLag", abs(estLag - lag) <= 150L)
    }

    @Test
    fun speedRatiosNearOneWhenMatched() {
        val buf = steadySpeedBuf(durationSec = 25, gnss = 50f, can = 50f)
        val result = DriveCalibrationMath.collectSpeedRatios(buf, 0L)
        assertTrue("n=${result.ratios.size}", result.ratios.size >= 8)
        val med = DriveCalibrationMath.median(result.ratios)!!
        assertEquals(1f, med, 0.02f)
        assertEquals(1, result.buckets)
    }

    @Test
    fun speedRatiosDetectScale() {
        val buf = steadySpeedBuf(durationSec = 25, gnss = 52.5f, can = 50f)
        val result = DriveCalibrationMath.collectSpeedRatios(buf, 0L)
        val med = DriveCalibrationMath.median(result.ratios)!!
        assertEquals(1.05f, med, 0.03f)
    }

    @Test
    fun unstableLagSlowsSpeedFill() {
        val full = DriveCalibrationMath.speedFill(40, 3, lagStability = 1f)
        val slow = DriveCalibrationMath.speedFill(40, 3, lagStability = 0.45f)
        assertEquals(1f, full, 0f)
        assertTrue(slow < full)
        assertTrue(slow <= 0.5f)
    }

    @Test
    fun speedFillRequiresBothVolumeAndBuckets() {
        // Many windows at one speed cannot complete the bar.
        assertTrue(DriveCalibrationMath.speedFill(40, 1) < 1f)
        assertTrue(DriveCalibrationMath.speedFill(10, 3) < 1f)
        assertEquals(1f, DriveCalibrationMath.speedFill(40, 3), 0f)
    }

    @Test
    fun singleSpeedCruiseDoesNotEstimateSpeedScale() {
        val buf = steadySpeedBuf(durationSec = 60, gnss = 50f, can = 50f)
        val est = DriveCalibrationMath.buildEstimates(buf, emptyList())
        assertTrue(est.speedSampleCount >= DriveCalibrationMath.MIN_SPEED_FOR_ESTIMATE)
        assertEquals(1, est.speedBuckets)
        assertFalse(est.speedEstimated)
        assertTrue(est.speedFill < 1f)
    }

    @Test
    fun multiSpeedSteadyWindowsEstimateSpeedScale() {
        val buf = multiSpeedBuf(
            listOf(30f to 12, 50f to 12, 70f to 12),
            gnssScale = 1.05f,
        )
        val est = DriveCalibrationMath.buildEstimates(buf, emptyList())
        assertTrue("buckets=${est.speedBuckets}", est.speedBuckets >= 3)
        assertTrue(est.speedEstimated)
        assertEquals(1.05f, est.speedScale, 0.03f)
        assertEquals(1f, est.speedFill, 0.001f)
    }

    @Test
    fun yawSignPositiveMatchesLeftYawNavBearingDecrease() {
        val samples = ArrayList<DriveCalibrationMath.YawSample>()
        // Four left + four right turns — both sides required for estimate
        for (i in 0 until 4) {
            appendLeftTurn(samples, startMs = i * 5_000L, startBearing = 90f - i * 5f)
        }
        for (i in 0 until 4) {
            appendRightTurn(samples, startMs = 20_000L + i * 5_000L, startBearing = 30f + i * 5f)
        }
        val (segs, rejected) = DriveCalibrationMath.collectYawSegments(samples, 0L)
        assertTrue("segs=${segs.size} rej=$rejected", segs.size >= 8)
        val est = DriveCalibrationMath.estimateYawScaleAndSign(segs)!!
        assertEquals(1, est.second)
        assertEquals(1f, est.first, 0.2f)
    }

    @Test
    fun yawRejectsMismatchedMagnitude() {
        val samples = ArrayList<DriveCalibrationMath.YawSample>()
        var bearing = 90f
        for (i in 0..40) {
            val t = i * 100L
            // Large gyro integral but tiny course change → skip (not a quality reject).
            samples.add(
                DriveCalibrationMath.YawSample(
                    elapsedMs = t,
                    yawRateDegPerSec = 20f,
                    bearingDeg = bearing,
                    speedKmh = 40f,
                ),
            )
            bearing -= 0.1f
        }
        val (segs, rejected) = DriveCalibrationMath.collectYawSegments(samples, 0L)
        assertTrue(segs.isEmpty())
        assertEquals(0, rejected)
    }

    @Test
    fun yawRejectsBadMagnitudeRatio() {
        val samples = ArrayList<DriveCalibrationMath.YawSample>()
        var bearing = 90f
        // Gyro ~30° but GNSS ~80° → magnitude ratio > 2.2 → quality reject.
        for (i in 0..25) {
            val t = i * 100L
            samples.add(
                DriveCalibrationMath.YawSample(
                    elapsedMs = t,
                    yawRateDegPerSec = 12f,
                    bearingDeg = bearing,
                    speedKmh = 40f,
                ),
            )
            bearing -= 3.2f
        }
        val (segs, rejected) = DriveCalibrationMath.collectYawSegments(samples, 0L)
        assertTrue(segs.isEmpty())
        assertTrue(rejected >= 1)
    }

    @Test
    fun slightYawOnStraightDoesNotInflateRejected() {
        val samples = ArrayList<DriveCalibrationMath.YawSample>()
        for (i in 0..400) {
            samples.add(
                DriveCalibrationMath.YawSample(
                    elapsedMs = i * 100L,
                    yawRateDegPerSec = 2.5f,
                    bearingDeg = 90f + i * 0.01f,
                    speedKmh = 50f,
                ),
            )
        }
        val (segs, rejected) = DriveCalibrationMath.collectYawSegments(samples, 0L)
        assertTrue(segs.isEmpty())
        assertEquals(0, rejected)
    }

    @Test
    fun fillReachesOneAtTargets() {
        assertEquals(1f, DriveCalibrationMath.speedFill(40, 3), 0f)
        assertEquals(1f, DriveCalibrationMath.yawFill(4, 4), 0f)
        assertTrue(DriveCalibrationMath.speedFill(10, 1) < 1f)
        assertTrue(DriveCalibrationMath.yawFill(4, 0) < 1f)
    }

    @Test
    fun hintWaitFixWhenPaused() {
        assertEquals(
            DriveCalibrationMath.Hint.WAIT_FIX,
            DriveCalibrationMath.hint(
                DriveCalibrationMath.Estimates(),
                DriveCalibrationMath.PauseKind.BAD_FIX,
                true,
            ),
        )
        assertEquals(
            DriveCalibrationMath.Hint.WAIT_FIX_JUNK,
            DriveCalibrationMath.hint(
                DriveCalibrationMath.Estimates(),
                DriveCalibrationMath.PauseKind.BAD_FIX_JUNK,
                true,
            ),
        )
        assertEquals(
            DriveCalibrationMath.Hint.WAIT_FIX_ACCURACY,
            DriveCalibrationMath.hint(
                DriveCalibrationMath.Estimates(),
                DriveCalibrationMath.PauseKind.BAD_FIX_ACCURACY,
                true,
            ),
        )
        assertEquals(
            DriveCalibrationMath.Hint.NO_CAN,
            DriveCalibrationMath.hint(
                DriveCalibrationMath.Estimates(),
                DriveCalibrationMath.PauseKind.NO_CAN,
                true,
            ),
        )
    }

    @Test
    fun courseJumpDetected() {
        val prev = DriveCalibrationMath.YawSample(0L, 1f, 90f, 40f)
        val cur = DriveCalibrationMath.YawSample(200L, 1f, 130f, 40f)
        assertTrue(DriveCalibrationMath.isCourseJump(prev, cur))
        val smooth = DriveCalibrationMath.YawSample(200L, 15f, 100f, 40f)
        assertFalse(DriveCalibrationMath.isCourseJump(prev, smooth))
    }

    @Test
    fun mergeKeepsPreviousWhenNotEstimated() {
        val prev = DriveCalibrationOffsets(
            speedScale = 1.08f,
            yawScaleLeft = 0.95f,
            yawScaleRight = 0.95f,
            yawSign = -1,
            calibratedAtEpochMs = 1L,
            speedEstimated = true,
            yawEstimated = true,
        )
        val est = DriveCalibrationMath.Estimates(
            speedScale = 1.2f,
            yawScaleLeft = 1.1f,
            yawScaleRight = 1.1f,
            yawSign = 1,
            speedEstimated = false,
            yawEstimated = false,
        )
        val merged = DriveCalibrationMath.mergeWithPrevious(est, prev, 99L)
        assertEquals(1.08f, merged.speedScale, 0f)
        assertEquals(0.95f, merged.yawScaleLeft, 0f)
        assertEquals(0.95f, merged.yawScaleRight, 0f)
        assertEquals(-1, merged.yawSign)
        assertFalse(merged.speedEstimated)
        assertFalse(merged.yawEstimated)
    }

    @Test
    fun sessionPausesOnBadFix() {
        val session = DriveCalibrationSession()
        session.start(0L)
        val accepted = session.onTick(
            elapsedMs = 1000L,
            liveUsable = false,
            live = LocValues(locateStatus = true, speed = 40f, trueDirection = 90f),
            canKmh = 40f,
            yawDebiasedDegPerSec = 0f,
            horizontalAccuracyM = 5f,
            gyroAvailable = true,
        )
        assertFalse(accepted)
        assertEquals(DriveCalibrationSession.Phase.PAUSED_BAD_FIX, session.uiState().phase)
        // No coords → generic WAIT_FIX (not junk — coordinates missing).
        assertEquals(DriveCalibrationMath.Hint.WAIT_FIX, session.uiState().hint)
    }

    @Test
    fun sessionPausesOnJunkGnssWithCoords() {
        val session = DriveCalibrationSession()
        session.start(0L)
        assertFalse(
            session.onTick(
                elapsedMs = 1000L,
                liveUsable = false,
                live = LocValues(
                    locateStatus = true,
                    latitude = 55.0,
                    longitude = 37.0,
                    speed = 40f,
                    trueDirection = 90f,
                ),
                canKmh = 40f,
                yawDebiasedDegPerSec = 0f,
                horizontalAccuracyM = 5f,
                gyroAvailable = true,
            ),
        )
        assertEquals(DriveCalibrationMath.Hint.WAIT_FIX_JUNK, session.uiState().hint)
    }

    @Test
    fun sessionPausesOnPoorAccuracy() {
        val session = DriveCalibrationSession()
        session.start(0L)
        assertFalse(
            session.onTick(
                elapsedMs = 1000L,
                liveUsable = true,
                live = LocValues(
                    locateStatus = true,
                    latitude = 55.0,
                    longitude = 37.0,
                    speed = 40f,
                    trueDirection = 90f,
                ),
                canKmh = 40f,
                yawDebiasedDegPerSec = 0f,
                horizontalAccuracyM = 40f,
                gyroAvailable = true,
            ),
        )
        assertEquals(DriveCalibrationMath.Hint.WAIT_FIX_ACCURACY, session.uiState().hint)
    }

    @Test
    fun sessionPausesWithoutCan() {
        val session = DriveCalibrationSession()
        session.start(0L)
        assertFalse(
            session.onTick(
                elapsedMs = 1000L,
                liveUsable = true,
                live = LocValues(locateStatus = true, speed = 40f, trueDirection = 90f),
                canKmh = null,
                yawDebiasedDegPerSec = 0f,
                horizontalAccuracyM = 5f,
                gyroAvailable = true,
            ),
        )
        assertEquals(DriveCalibrationMath.Hint.NO_CAN, session.uiState().hint)
    }

    @Test
    fun enoughWithoutDataBlocksReliableSave() {
        val session = DriveCalibrationSession()
        session.start(0L)
        val preview = session.finishToPreview(1_000L, DriveCalibrationOffsets.DEFAULT)
        assertNotNull(preview)
        assertFalse(preview!!.speedEstimated)
        assertFalse(preview.yawEstimated)
        assertTrue(session.uiState().previewLowQuality)
        assertNull(
            // Repository gate mirrors this: nothing estimated → no save payload
            preview.takeIf { it.speedEstimated || it.yawEstimated },
        )
    }

    @Test
    fun storeAppliesSpeedAndYaw() {
        DriveCalibrationStore.update(
            DriveCalibrationOffsets(
                speedScale = 1.1f,
                yawScaleLeft = 0.9f,
                yawScaleRight = 0.9f,
                yawSign = -1,
            ),
        )
        assertEquals(55f, DriveCalibrationStore.applyCanSpeed(50f), 0.01f)
        assertEquals(-9f, DriveCalibrationStore.applyYawRate(10f), 0.01f)
        DriveCalibrationStore.reset()
        assertEquals(50f, DriveCalibrationStore.applyCanSpeed(50f), 0f)
        assertEquals(10f, DriveCalibrationStore.applyYawRate(10f), 0f)
    }

    @Test
    fun storeAppliesDualYawScaleBySign() {
        DriveCalibrationStore.update(
            DriveCalibrationOffsets(yawScaleLeft = 1.2f, yawScaleRight = 0.8f, yawSign = 1),
        )
        assertEquals(12f, DriveCalibrationStore.applyYawRate(10f), 0.01f) // left
        assertEquals(-8f, DriveCalibrationStore.applyYawRate(-10f), 0.01f) // right
    }

    @Test
    fun estimateYawScalesSeparatesLeftAndRight() {
        val samples = ArrayList<DriveCalibrationMath.YawSample>()
        for (i in 0 until 4) {
            appendLeftTurn(samples, startMs = i * 5_000L, startBearing = 90f - i * 5f)
        }
        for (i in 0 until 4) {
            appendRightTurn(samples, startMs = 20_000L + i * 5_000L, startBearing = 30f + i * 5f)
        }
        val (segs, _) = DriveCalibrationMath.collectYawSegments(samples, 0L)
        assertTrue(segs.size >= 8)
        val est = DriveCalibrationMath.estimateYawScalesAndSign(segs)!!
        assertEquals(1, est.yawSign)
        assertNotNull(est.scaleLeft)
        assertNotNull(est.scaleRight)
        assertTrue(est.hasBothSides)
        assertTrue(est.leftCount >= DriveCalibrationMath.MIN_YAW_PER_SIDE)
        assertTrue(est.rightCount >= DriveCalibrationMath.MIN_YAW_PER_SIDE)
        assertEquals(1f, est.scaleLeft!!, 0.25f)
        assertEquals(1f, est.scaleRight!!, 0.25f)
    }

    @Test
    fun estimateYawScalesRequiresFourArcsPerSide() {
        val onlyFew = listOf(
            DriveCalibrationMath.YawSegmentResult(gyroIntegralDeg = 30f, gnssDeltaDeg = -30f),
            DriveCalibrationMath.YawSegmentResult(gyroIntegralDeg = 28f, gnssDeltaDeg = -28f),
            DriveCalibrationMath.YawSegmentResult(gyroIntegralDeg = 29f, gnssDeltaDeg = -29f),
            DriveCalibrationMath.YawSegmentResult(gyroIntegralDeg = -30f, gnssDeltaDeg = 30f),
            DriveCalibrationMath.YawSegmentResult(gyroIntegralDeg = -28f, gnssDeltaDeg = 28f),
            DriveCalibrationMath.YawSegmentResult(gyroIntegralDeg = -29f, gnssDeltaDeg = 29f),
        )
        assertNull(DriveCalibrationMath.estimateYawScalesAndSign(onlyFew))
    }

    @Test
    fun mergeUpdatesOnlyEstimatedYawSide() {
        val prev = DriveCalibrationOffsets(
            yawScaleLeft = 1.0f,
            yawScaleRight = 1.3f,
            yawSign = 1,
            yawEstimated = true,
        )
        val est = DriveCalibrationMath.Estimates(
            yawScaleLeft = 1.1f,
            yawScaleRight = 0.5f,
            yawSign = 1,
            yawLeftEstimated = true,
            yawRightEstimated = false,
            yawEstimated = true,
        )
        val merged = DriveCalibrationMath.mergeWithPrevious(est, prev, 50L)
        assertEquals(1.1f, merged.yawScaleLeft, 0f)
        assertEquals(1.3f, merged.yawScaleRight, 0f)
    }

    @Test
    fun largeStraightBufferBuildEstimatesStaysFast() {
        // ~10 min at 10 Hz — previously O(n²) lag search froze UI / ticker.
        val buf = steadySpeedBuf(durationSec = 600, gnss = 50f, can = 50f)
        assertTrue(buf.size >= 5_000)
        val t0 = System.nanoTime()
        val est = DriveCalibrationMath.buildEstimates(buf, emptyList())
        val ms = (System.nanoTime() - t0) / 1_000_000L
        assertTrue("buildEstimates took ${ms}ms on n=${buf.size}", ms < 2_500L)
        // Single cruise band → windows ok, but speed scale not estimated.
        assertFalse(est.speedEstimated)
        assertFalse(est.yawEstimated)
        assertTrue(est.ready.not())
    }

    @Test
    fun subsampleCapsLagSearchInput() {
        val buf = steadySpeedBuf(durationSec = 300, gnss = 50f, can = 50f)
        val sub = DriveCalibrationMath.subsampleSpeedForLag(buf)
        assertEquals(DriveCalibrationMath.LAG_SUBSAMPLE_MAX, sub.size)
        assertEquals(buf.first().elapsedMs, sub.first().elapsedMs)
        assertEquals(buf.last().elapsedMs, sub.last().elapsedMs)
    }

    @Test
    fun sessionFinishEnoughSafeUnderConcurrentTicks() {
        val session = DriveCalibrationSession()
        session.start(0L)
        val live = LocValues(
            locateStatus = true,
            speed = 50f,
            trueDirection = 90f,
            latitude = 55.0,
            longitude = 37.0,
        )
        val tickers = (0 until 4).map { threadIdx ->
            Thread {
                var t = threadIdx * 10L
                repeat(400) {
                    session.onTick(
                        elapsedMs = t,
                        liveUsable = true,
                        live = live,
                        canKmh = 50f,
                        yawDebiasedDegPerSec = 0.2f,
                        horizontalAccuracyM = 4f,
                        gyroAvailable = true,
                    )
                    t += 100L
                }
            }.also { it.start() }
        }
        Thread.sleep(50)
        val preview = session.finishToPreview(2_000L, DriveCalibrationOffsets.DEFAULT)
        assertNotNull(preview)
        assertEquals(DriveCalibrationSession.Phase.PREVIEW, session.uiState().phase)
        tickers.forEach { it.join(5_000) }
        // Still readable after concurrent finish + ticks.
        assertEquals(DriveCalibrationSession.Phase.PREVIEW, session.uiState().phase)
    }

    @Test
    fun sessionTrimsOldSamplesAndFinishStaysResponsive() {
        val session = DriveCalibrationSession()
        session.start(0L)
        val live = LocValues(
            locateStatus = true,
            speed = 50f,
            trueDirection = 90f,
            latitude = 55.0,
            longitude = 37.0,
        )
        // Feed ~8 min of straight driving at 10 Hz.
        for (i in 0 until 4_800) {
            session.onTick(
                elapsedMs = i * 100L,
                liveUsable = true,
                live = live,
                canKmh = 50f,
                yawDebiasedDegPerSec = 0.1f,
                horizontalAccuracyM = 4f,
                gyroAvailable = true,
            )
        }
        val t0 = System.nanoTime()
        val preview = session.finishToPreview(9_000L, DriveCalibrationOffsets.DEFAULT)
        val ms = (System.nanoTime() - t0) / 1_000_000L
        assertNotNull(preview)
        assertTrue("finishToPreview took ${ms}ms", ms < 2_500L)
        // One speed band → speed not estimated under multi-bucket gate.
        assertFalse(preview!!.speedEstimated)
        assertFalse(preview.yawEstimated)
    }

    @Test
    fun sessionTimedOutAfterWallClockLimit() {
        val session = DriveCalibrationSession()
        session.start(1_000L)
        assertFalse(session.isTimedOut(1_000L + DriveCalibrationSession.SESSION_TIMEOUT_MS - 1L))
        assertTrue(session.isTimedOut(1_000L + DriveCalibrationSession.SESSION_TIMEOUT_MS))
        // Paused still counts toward timeout.
        session.onTick(
            elapsedMs = 2_000L,
            liveUsable = false,
            live = LocValues(locateStatus = true, speed = 40f, trueDirection = 90f),
            canKmh = 40f,
            yawDebiasedDegPerSec = 0f,
            horizontalAccuracyM = 5f,
            gyroAvailable = true,
        )
        assertEquals(DriveCalibrationSession.Phase.PAUSED_BAD_FIX, session.uiState().phase)
        assertTrue(session.isTimedOut(1_000L + DriveCalibrationSession.SESSION_TIMEOUT_MS))
    }

    @Test
    fun sessionTimeoutFinishGoesToPreview() {
        val session = DriveCalibrationSession()
        session.start(0L)
        assertTrue(session.isTimedOut(DriveCalibrationSession.SESSION_TIMEOUT_MS))
        val preview = session.finishToPreview(9_000L, DriveCalibrationOffsets.DEFAULT)
        assertNotNull(preview)
        assertEquals(DriveCalibrationSession.Phase.PREVIEW, session.uiState().phase)
        assertFalse(session.isTimedOut(DriveCalibrationSession.SESSION_TIMEOUT_MS + 1L))
    }

    @Test
    fun sessionTimeoutCancelReturnsIdle() {
        val session = DriveCalibrationSession()
        session.start(0L)
        assertTrue(session.isTimedOut(DriveCalibrationSession.SESSION_TIMEOUT_MS))
        session.cancel()
        assertEquals(DriveCalibrationSession.Phase.IDLE, session.uiState().phase)
        assertFalse(session.isTimedOut(DriveCalibrationSession.SESSION_TIMEOUT_MS + 1L))
    }

    @Test
    fun yawFillUsesFittedSidesNotRawSegmentTotal() {
        assertEquals(0.5f, DriveCalibrationMath.yawFill(4, 0), 0.01f)
        assertEquals(1f, DriveCalibrationMath.yawFill(4, 4), 0f)
        // Raw total of 8 one-sided arcs must not look "full".
        assertTrue(DriveCalibrationMath.yawFill(8, 0) < 1f)
    }

    @Test
    fun trimmedSpreadIgnoresSingleOutlier() {
        val values = listOf(1.0f, 1.02f, 0.98f, 1.01f, 0.99f, 1.5f)
        assertTrue(DriveCalibrationMath.relativeSpread(values) > 0.4f)
        assertTrue(DriveCalibrationMath.trimmedRelativeSpread(values) < 0.4f)
    }

    private fun steadySpeedBuf(durationSec: Int, gnss: Float, can: Float): List<DriveCalibrationMath.SpeedSample> {
        val buf = ArrayList<DriveCalibrationMath.SpeedSample>()
        val n = durationSec * 10
        for (i in 0..n) {
            buf.add(DriveCalibrationMath.SpeedSample(i * 100L, gnss, can))
        }
        return buf
    }

    /** Several steady cruises at different CAN speeds (each [sec] long). */
    private fun multiSpeedBuf(
        legs: List<Pair<Float, Int>>,
        gnssScale: Float = 1f,
    ): List<DriveCalibrationMath.SpeedSample> {
        val buf = ArrayList<DriveCalibrationMath.SpeedSample>()
        var t = 0L
        for ((can, sec) in legs) {
            val n = sec * 10
            for (i in 0..n) {
                buf.add(DriveCalibrationMath.SpeedSample(t, can * gnssScale, can))
                t += 100L
            }
            // Short gap so windows do not straddle speed changes.
            t += 2_000L
        }
        return buf
    }

    private fun appendLeftTurn(
        samples: ArrayList<DriveCalibrationMath.YawSample>,
        startMs: Long,
        startBearing: Float,
    ) {
        var bearing = startBearing
        for (i in 0..25) {
            val t = startMs + i * 100L
            samples.add(
                DriveCalibrationMath.YawSample(
                    elapsedMs = t,
                    yawRateDegPerSec = 12f,
                    bearingDeg = bearing,
                    speedKmh = 40f,
                ),
            )
            bearing -= 1.2f
        }
    }

    private fun appendRightTurn(
        samples: ArrayList<DriveCalibrationMath.YawSample>,
        startMs: Long,
        startBearing: Float,
    ) {
        var bearing = startBearing
        for (i in 0..25) {
            val t = startMs + i * 100L
            samples.add(
                DriveCalibrationMath.YawSample(
                    elapsedMs = t,
                    yawRateDegPerSec = -12f,
                    bearingDeg = bearing,
                    speedKmh = 40f,
                ),
            )
            bearing += 1.2f
        }
    }
}
