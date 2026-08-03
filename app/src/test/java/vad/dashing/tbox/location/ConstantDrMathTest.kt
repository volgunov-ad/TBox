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
    fun largeMismatchThreshold() {
        assertFalse(ConstantDrMath.isLargeMismatch(10.0))
        assertTrue(ConstantDrMath.isLargeMismatch(40.0))
        assertTrue(ConstantDrMath.isLargeMismatch(100.0))
    }

    @Test
    fun mismatchStreakAndCalibRequest() {
        assertEquals(1, ConstantDrMath.nextMismatchStreak(0, true))
        assertEquals(0, ConstantDrMath.nextMismatchStreak(2, false))
        assertEquals(3, ConstantDrMath.nextMismatchStreak(2, true))
        assertFalse(ConstantDrMath.shouldRequestCalibration(2))
        assertTrue(ConstantDrMath.shouldRequestCalibration(3))
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
}
