package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstantDrMathTest {

    @Test
    fun distanceNearZeroForSamePoint() {
        val d = ConstantDrMath.distanceMeters(55.75, 37.62, 55.75, 37.62)
        assertEquals(0.0, d, 1e-6)
    }

    @Test
    fun distanceRoughlyOneDegreeLat() {
        val d = ConstantDrMath.distanceMeters(55.0, 37.0, 56.0, 37.0)
        assertEquals(111_320.0, d, 50.0)
    }

    @Test
    fun adaptiveThresholdFloorAndSpeed() {
        val slow = ConstantDrMath.mismatchThresholdM(speedKmh = 10f, horizontalAccuracyM = 5f)
        assertEquals(25.0, slow, 0.1)
        val fast = ConstantDrMath.mismatchThresholdM(speedKmh = 100f, horizontalAccuracyM = 5f)
        // 0.5 * (100/3.6)*1 + 2*5 ≈ 13.9 + 10 → floor 25
        assertEquals(25.0, fast, 0.1)
        val fastLong = ConstantDrMath.mismatchThresholdM(
            speedKmh = 100f,
            intervalSec = 5.0,
            horizontalAccuracyM = 5f,
        )
        assertTrue(fastLong > 70.0)
        assertTrue(fastLong < 90.0)
    }

    @Test
    fun largeMismatchUsesThreshold() {
        assertFalse(ConstantDrMath.isLargeMismatch(20.0, 40.0))
        assertTrue(ConstantDrMath.isLargeMismatch(40.0, 40.0))
    }

    @Test
    fun mismatchNotCountedAtLowSpeed() {
        assertFalse(ConstantDrMath.shouldCountMismatch(5f))
        assertTrue(ConstantDrMath.shouldCountMismatch(10f))
    }

    @Test
    fun freshCalibRequiresLongerStreak() {
        val now = 2_000_000_000_000L
        assertEquals(
            ConstantDrMath.MISMATCH_STREAK_TO_CALIBRATE,
            ConstantDrMath.requiredMismatchStreak(now, 0L),
        )
        assertEquals(
            ConstantDrMath.MISMATCH_STREAK_WHEN_FRESH,
            ConstantDrMath.requiredMismatchStreak(now, now - 30 * 60_000L),
        )
        assertEquals(
            ConstantDrMath.MISMATCH_STREAK_TO_CALIBRATE,
            ConstantDrMath.requiredMismatchStreak(now, now - 2 * 60 * 60_000L),
        )
    }

    @Test
    fun mismatchStreakAndCalibRequest() {
        assertEquals(1, ConstantDrMath.nextMismatchStreak(0, true))
        assertEquals(0, ConstantDrMath.nextMismatchStreak(2, false))
        assertFalse(ConstantDrMath.shouldRequestCalibration(9, 10))
        assertTrue(ConstantDrMath.shouldRequestCalibration(10, 10))
        assertFalse(ConstantDrMath.shouldRequestCalibration(19, 20))
        assertTrue(ConstantDrMath.shouldRequestCalibration(20, 20))
    }

    @Test
    fun hardResyncAlignsWithSoftBlendZero() {
        // thr=25 → soft blend zeros at 37.5 m; no 80 m floor dead band.
        assertFalse(ConstantDrMath.shouldHardResync(30.0, 25.0))
        assertTrue(ConstantDrMath.shouldHardResync(50.0, 25.0))
        assertTrue(ConstantDrMath.shouldHardResync(70.0, 25.0))
        // thr=40 → soft zero at 60 m
        assertFalse(ConstantDrMath.shouldHardResync(50.0, 40.0))
        assertTrue(ConstantDrMath.shouldHardResync(60.0, 40.0))
        // Invalid threshold → fallback floor
        assertFalse(ConstantDrMath.shouldHardResync(50.0, Double.NaN))
        assertTrue(ConstantDrMath.shouldHardResync(80.0, Double.NaN))
        // Invariant: when mismatchScale is fully off, hard resync is allowed.
        val thr = 25.0
        for (d in listOf(20.0, 30.0, 37.5, 40.0, 60.0, 100.0)) {
            if (ConstantDrMath.mismatchScale(d, thr) == 0f) {
                assertTrue(ConstantDrMath.shouldHardResync(d, thr))
            }
        }
    }

    @Test
    fun hardResyncSpeedAgreement() {
        assertTrue(ConstantDrMath.gnssSpeedAgreesForHardResync(80f, 78f))
        assertFalse(ConstantDrMath.gnssSpeedAgreesForHardResync(80f, 40f))
        assertFalse(ConstantDrMath.gnssSpeedAgreesForHardResync(0.5f, 80f))
        assertTrue(ConstantDrMath.gnssSpeedAgreesForHardResync(60f, null))
    }

    @Test
    fun confidenceAndPositionWeights() {
        assertEquals(1.0f, ConstantDrMath.confidenceFromAccuracyM(2f), 1e-3f)
        assertEquals(0.0f, ConstantDrMath.confidenceFromAccuracyM(50f), 1e-3f)
        assertEquals(0.8f, ConstantDrMath.positionWeightFromConfidence(0.98f), 1e-3f)
        assertEquals(0f, ConstantDrMath.positionWeightFromConfidence(0.4f), 1e-3f)
    }

    @Test
    fun courseWeightNeedsGoodConfidenceAndSmallResidual() {
        assertEquals(1.0f, ConstantDrMath.courseWeightFromConfidence(0.95f, 2f), 1e-3f)
        assertEquals(0f, ConstantDrMath.courseWeightFromConfidence(0.7f, 2f), 1e-3f)
    }

    @Test
    fun mismatchScaleDropsOnLargeResidual() {
        assertEquals(1f, ConstantDrMath.mismatchScale(5.0, 40.0), 1e-3f)
        assertEquals(0f, ConstantDrMath.mismatchScale(80.0, 40.0), 1e-3f)
        assertEquals(0.15f, ConstantDrMath.mismatchScale(40.0, 40.0), 1e-3f)
    }

    @Test
    fun speedScaleForGnssCourseGatesStationary() {
        assertEquals(0f, ConstantDrMath.speedScaleForGnssCourse(0.2f), 1e-3f)
        assertEquals(0.3f, ConstantDrMath.speedScaleForGnssCourse(1.0f), 1e-3f)
        assertEquals(1f, ConstantDrMath.speedScaleForGnssCourse(5f), 1e-3f)
    }

    @Test
    fun blendLatLonAndBearing() {
        val (lat, lon) = ConstantDrMath.blendLatLon(55.0, 37.0, 56.0, 38.0, 0.5f)
        assertEquals(55.5, lat, 1e-6)
        assertEquals(37.5, lon, 1e-6)
        assertEquals(95f, ConstantDrMath.blendBearingDeg(90f, 100f, 0.5f), 1e-2f)
    }

    @Test
    fun reverseNoseAndTravelArePlus180() {
        assertEquals(270f, ConstantDrMath.noseHeadingFromCourseOverGround(90f, reverse = true), 1e-3f)
        assertEquals(90f, ConstantDrMath.noseHeadingFromCourseOverGround(90f, reverse = false), 1e-3f)
        assertEquals(270f, ConstantDrMath.travelBearingFromNoseHeading(90f, reverse = true), 1e-3f)
        assertEquals(90f, ConstantDrMath.travelBearingFromNoseHeading(90f, reverse = false), 1e-3f)
    }

    @Test
    fun reverseStraightTravelMovesOppositeNose() {
        // Nose east → reverse travel west: lat unchanged, lon decreases.
        val (lat, lon) = ConstantDrMath.extrapolateLatLon(
            lat = 55.0,
            lon = 37.0,
            bearingDeg = ConstantDrMath.travelBearingFromNoseHeading(90f, reverse = true),
            distanceM = 111.32,
        )
        assertEquals(55.0, lat, 1e-5)
        assertTrue(lon < 37.0)
    }

    @Test
    fun minMoveSpeedIsHalfMeterPerSec() {
        assertEquals(0.5f, ConstantDrMath.MIN_MOVE_SPEED_MPS, 1e-6f)
        assertEquals(1.8f, ConstantDrMath.MIN_MOVE_SPEED_KMH, 1e-3f)
        assertEquals(MockLocationJob.COURSE_HOLD_MIN_KMH, ConstantDrMath.MIN_MOVE_SPEED_KMH, 1e-3f)
    }

    @Test
    fun extrapolateMovesNorth() {
        val (lat, lon) = ConstantDrMath.extrapolateLatLon(55.0, 37.0, 0f, 111.32)
        assertTrue(lat > 55.0)
        assertEquals(37.0, lon, 1e-5)
    }
}
