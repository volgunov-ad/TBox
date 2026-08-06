package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class OnlineYawCalibEstimatorTest {

    @Before
    fun resetStores() {
        GyroBiasStore.update(GyroBiasOffsets.ZERO)
        DriveCalibrationStore.reset()
    }

    @Test
    fun nextBiasMovesTowardResidual() {
        // debiased = +0.2 → bias increases
        val next = OnlineYawCalibMath.nextBiasDegPerSec(0.1f, 0.2f)
        assertTrue(next > 0.1f)
        assertTrue(next < 0.1f + OnlineYawCalibMath.BIAS_MAX_STEP + 1e-4f)
    }

    @Test
    fun scaleCandidateMatchesDriveCalibSignConvention() {
        // Left turn: positive gyro integral, GNSS nose decreases → scale ≈ 1
        val s = OnlineYawCalibMath.scaleCandidateForSign(
            gyroIntegralDebiased = 30f,
            gnssNoseDeltaDeg = -30f,
            yawSign = 1,
        )
        assertNotNull(s)
        assertEquals(1f, s!!, 0.01f)
    }

    @Test
    fun scaleCandidateRejectsTinyTurn() {
        assertNull(
            OnlineYawCalibMath.scaleCandidateForSign(
                gyroIntegralDebiased = 5f,
                gnssNoseDeltaDeg = -5f,
                yawSign = 1,
            ),
        )
    }

    @Test
    fun nextScaleEmasTowardCandidate() {
        val next = OnlineYawCalibMath.nextScale(1.0f, 1.2f)
        assertTrue(next > 1.0f)
        assertTrue(next < 1.2f)
    }

    @Test
    fun straightGateRequiresSpeedAndLowYaw() {
        assertTrue(
            OnlineYawCalibMath.isStraightCandidate(
                speedKmh = 40f,
                accuracyM = 5f,
                debiasedYawAbs = 0.3f,
                courseRateAbs = 0.5f,
            ),
        )
        assertFalse(
            OnlineYawCalibMath.isStraightCandidate(
                speedKmh = 10f,
                accuracyM = 5f,
                debiasedYawAbs = 0.3f,
                courseRateAbs = 0.5f,
            ),
        )
        assertFalse(
            OnlineYawCalibMath.isStraightCandidate(
                speedKmh = 40f,
                accuracyM = 5f,
                debiasedYawAbs = 2.0f,
                courseRateAbs = 0.5f,
            ),
        )
    }

    @Test
    fun estimatorAppliesBiasAfterStraightHold() {
        val est = OnlineYawCalibEstimator()
        GyroBiasStore.update(GyroBiasOffsets(yawDegPerSec = 0f))
        // raw = 0.25 → debiased 0.25, straight
        var t = 1_000L
        fun tick() {
            est.onTick(
                elapsedMs = t,
                rawYawDegPerSec = 0.25f,
                gnssNoseCourseDeg = 90f,
                speedKmh = 50f,
                accuracyM = 4f,
                reverse = false,
                gnssTruthful = true,
            )
            t += 1_000L
        }
        tick() // establish prev course
        tick() // straight starts
        tick() // hold 1s
        tick() // hold 2s
        val before = GyroBiasStore.offsets.yawDegPerSec
        tick() // hold ≥ 3s → bias step
        val after = GyroBiasStore.offsets.yawDegPerSec
        assertTrue("bias should rise, before=$before after=$after", after > before)
    }

    @Test
    fun estimatorUpdatesScaleAfterTurnSegment() {
        val est = OnlineYawCalibEstimator()
        DriveCalibrationStore.update(DriveCalibrationOffsets(yawScaleLeft = 1.0f, yawScaleRight = 1.0f))
        var t = 1_000L
        var course = 0f
        // Start turn: yaw +10 °/s, course decreases at ~10 °/s (scale 1)
        est.onTick(
            elapsedMs = t,
            rawYawDegPerSec = 10f,
            gnssNoseCourseDeg = course,
            speedKmh = 40f,
            accuracyM = 4f,
            reverse = false,
            gnssTruthful = true,
        )
        t += 1_000L
        // Integrate ~10°/s * 3s = 30° with matching GNSS
        repeat(3) {
            course -= 10f
            if (course < 0f) course += 360f
            est.onTick(
                elapsedMs = t,
                rawYawDegPerSec = 10f,
                gnssNoseCourseDeg = course,
                speedKmh = 40f,
                accuracyM = 4f,
                reverse = false,
                gnssTruthful = true,
            )
            t += 1_000L
        }
        // One more tick with still high yaw to close segment once integral ≥ 25
        // (segment closes when gyroAbs >= 25 inside onTurnTick after accumulate)
        // After 3×1s from start: first tick starts, then 3 accumulate → integral ~30
        // Last of the 3 should have closed. Check scale still near 1 (candidate ~1).
        val scale = DriveCalibrationStore.offsets.yawScale
        assertTrue("scale near 1, got $scale", abs(scale - 1f) < 0.2f)
    }

    @Test
    fun skipsWhenNotTruthful() {
        val est = OnlineYawCalibEstimator()
        GyroBiasStore.update(GyroBiasOffsets(yawDegPerSec = 0.5f))
        val r = est.onTick(
            elapsedMs = 5_000L,
            rawYawDegPerSec = 0.7f,
            gnssNoseCourseDeg = 10f,
            speedKmh = 50f,
            accuracyM = 3f,
            reverse = false,
            gnssTruthful = false,
        )
        assertFalse(r.biasChanged)
        assertEquals(0.5f, GyroBiasStore.offsets.yawDegPerSec, 1e-4f)
    }

    @Test
    fun persistBiasDebounced() {
        assertTrue(
            OnlineYawCalibMath.shouldPersistBias(
                lastPersisted = 0.1f,
                current = 0.15f,
                lastPersistElapsedMs = 0L,
                nowElapsedMs = 1_000L,
            ),
        )
        assertFalse(
            OnlineYawCalibMath.shouldPersistBias(
                lastPersisted = 0.1f,
                current = 0.15f,
                lastPersistElapsedMs = 1_000L,
                nowElapsedMs = 5_000L,
            ),
        )
    }

    @Test
    fun secondBiasStepStaysDirtyUntilDebounceInterval() {
        val est = OnlineYawCalibEstimator()
        GyroBiasStore.update(GyroBiasOffsets(yawDegPerSec = 0f))
        var t = 1_000L
        fun tick(raw: Float): OnlineYawCalibTickResult {
            val r = est.onTick(
                elapsedMs = t,
                rawYawDegPerSec = raw,
                gnssNoseCourseDeg = 90f,
                speedKmh = 50f,
                accuracyM = 4f,
                reverse = false,
                gnssTruthful = true,
            )
            t += 1_000L
            return r
        }
        repeat(5) { tick(0.4f) }
        assertTrue(GyroBiasStore.offsets.yawDegPerSec > 0f)
        var secondPersisted = false
        repeat(4) {
            val r = tick(0.4f)
            if (r.persistBias) secondPersisted = true
        }
        assertFalse("second step should be debounced", secondPersisted)
        assertTrue(est.hasDirtyPersist())
        // After interval elapses, debounce persist succeeds without force flush.
        val later = est.evaluatePersist(t + OnlineYawCalibMath.PERSIST_MIN_INTERVAL_MS)
        assertTrue(later.persistBias)
        assertFalse(est.hasDirtyPersist())
    }

    @Test
    fun biasAlphaFasterWhenTempFarFromCalib() {
        assertEquals(
            OnlineYawCalibMath.BIAS_EMA_ALPHA,
            OnlineYawCalibMath.biasAlpha(25f, 24f),
            0f,
        )
        assertEquals(
            OnlineYawCalibMath.BIAS_EMA_ALPHA_TEMP_DRIFT,
            OnlineYawCalibMath.biasAlpha(35f, 25f),
            0f,
        )
    }

    @Test
    fun scaleBlockedByTempSpan() {
        assertFalse(OnlineYawCalibMath.scaleBlockedByTemp(null, 30f))
        assertFalse(OnlineYawCalibMath.scaleBlockedByTemp(30f, 31f))
        assertTrue(OnlineYawCalibMath.scaleBlockedByTemp(30f, 32f))
    }

    @Test
    fun onlineScaleUpdatesLeftSideOnly() {
        val est = OnlineYawCalibEstimator()
        DriveCalibrationStore.update(
            DriveCalibrationOffsets(yawScaleLeft = 1.0f, yawScaleRight = 1.4f),
        )
        var t = 1_000L
        var course = 0f
        est.onTick(
            elapsedMs = t,
            rawYawDegPerSec = 10f,
            gnssNoseCourseDeg = course,
            speedKmh = 40f,
            accuracyM = 4f,
            reverse = false,
            gnssTruthful = true,
        )
        t += 1_000L
        repeat(3) {
            course -= 10f
            if (course < 0f) course += 360f
            est.onTick(
                elapsedMs = t,
                rawYawDegPerSec = 10f,
                gnssNoseCourseDeg = course,
                speedKmh = 40f,
                accuracyM = 4f,
                reverse = false,
                gnssTruthful = true,
            )
            t += 1_000L
        }
        assertEquals(1.4f, DriveCalibrationStore.offsets.yawScaleRight, 1e-4f)
        assertTrue(
            "left scale near 1, got ${DriveCalibrationStore.offsets.yawScaleLeft}",
            abs(DriveCalibrationStore.offsets.yawScaleLeft - 1f) < 0.2f,
        )
    }
}
