package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GyroCalibrationMathTest {

    @Test
    fun averageAcceptedWhenStable() {
        val r = GyroCalibrationMath.averageWithRangeCheck(
            listOf(0.4f, 0.5f, 0.45f),
            maxRange = 1.5f,
        )!!
        assertTrue(r.accepted)
        assertEquals(0.45f, r.mean, 0.01f)
    }

    @Test
    fun averageRejectedWhenNoisy() {
        val r = GyroCalibrationMath.averageWithRangeCheck(
            listOf(0f, 3f),
            maxRange = 1.5f,
        )!!
        assertFalse(r.accepted)
    }
}
