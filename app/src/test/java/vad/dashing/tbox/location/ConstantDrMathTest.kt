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
        // 0.5 * (100/3.6)*5 + 2*5 ≈ 69.4 + 10
        assertTrue(fast > 70.0)
        assertTrue(fast < 90.0)
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
        assertFalse(ConstantDrMath.shouldRequestCalibration(2, 3))
        assertTrue(ConstantDrMath.shouldRequestCalibration(3, 3))
        assertFalse(ConstantDrMath.shouldRequestCalibration(3, 6))
        assertTrue(ConstantDrMath.shouldRequestCalibration(6, 6))
    }

    @Test
    fun shouldSnapInterval() {
        assertTrue(ConstantDrMath.shouldSnapToGnss(0L, 1000L))
        assertFalse(ConstantDrMath.shouldSnapToGnss(1000L, 2000L))
        assertTrue(
            ConstantDrMath.shouldSnapToGnss(
                1000L,
                1000L + ConstantDrMath.GNSS_SNAP_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun blendAlphaAndLatLon() {
        assertEquals(1f, ConstantDrMath.blendAlphaTowardGnss(5.0, 40.0), 1e-3f)
        assertEquals(0f, ConstantDrMath.blendAlphaTowardGnss(40.0, 40.0), 1e-3f)
        val mid = ConstantDrMath.blendAlphaTowardGnss(30.0, 40.0)
        assertTrue(mid > 0.3f && mid < 1f)
        val (lat, lon) = ConstantDrMath.blendLatLon(55.0, 37.0, 56.0, 38.0, 0.5f)
        assertEquals(55.5, lat, 1e-6)
        assertEquals(37.5, lon, 1e-6)
    }

    @Test
    fun adoptGnssCourseGates() {
        assertTrue(ConstantDrMath.shouldAdoptGnssCourse(30f, 90f, null))
        assertTrue(ConstantDrMath.shouldAdoptGnssCourse(30f, 100f, 90f))
        assertFalse(ConstantDrMath.shouldAdoptGnssCourse(30f, 180f, 90f))
        assertTrue(ConstantDrMath.shouldAdoptGnssCourse(50f, 140f, 90f))
        assertFalse(ConstantDrMath.shouldAdoptGnssCourse(1f, 90f, null))
    }
}
