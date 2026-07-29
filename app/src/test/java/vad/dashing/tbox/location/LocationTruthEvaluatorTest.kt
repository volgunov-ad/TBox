package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTruthEvaluatorTest {

    @Test
    fun startsFalse() {
        val e = LocationTruthEvaluator()
        assertFalse(e.currentTruth())
    }

    @Test
    fun matchUnder5sStaysFalse() {
        val e = LocationTruthEvaluator()
        assertFalse(e.onTick(0L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f))
        assertFalse(e.onTick(4_900L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 52f))
        assertFalse(e.currentTruth())
    }

    @Test
    fun matchAt5sBecomesTrue() {
        val e = LocationTruthEvaluator()
        e.onTick(0L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f)
        assertTrue(e.onTick(5_000L, hasFix = true, navSpeedKmH = 55f, carSpeedKmH = 50f))
    }

    @Test
    fun mismatchAt5sBecomesFalse() {
        val e = LocationTruthEvaluator()
        e.onTick(0L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f)
        assertTrue(e.onTick(5_000L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f))
        assertTrue(e.onTick(5_100L, hasFix = true, navSpeedKmH = 80f, carSpeedKmH = 50f))
        assertFalse(e.onTick(10_100L, hasFix = true, navSpeedKmH = 80f, carSpeedKmH = 50f))
    }

    @Test
    fun nullCarSpeedIsMismatch() {
        val e = LocationTruthEvaluator()
        e.onTick(0L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f)
        assertTrue(e.onTick(5_000L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f))
        e.onTick(5_100L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = null)
        assertFalse(e.onTick(10_100L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = null))
    }

    @Test
    fun noFixIsMismatchDebounced() {
        val e = LocationTruthEvaluator()
        e.onTick(0L, hasFix = true, navSpeedKmH = 40f, carSpeedKmH = 40f)
        assertTrue(e.onTick(5_000L, hasFix = true, navSpeedKmH = 40f, carSpeedKmH = 40f))
        assertTrue(e.onTick(5_500L, hasFix = false, navSpeedKmH = 40f, carSpeedKmH = 40f))
        assertFalse(e.onTick(10_500L, hasFix = false, navSpeedKmH = 40f, carSpeedKmH = 40f))
    }

    @Test
    fun interruptingMatchResetsMatchWindow() {
        val e = LocationTruthEvaluator()
        e.onTick(0L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f)
        e.onTick(3_000L, hasFix = true, navSpeedKmH = 80f, carSpeedKmH = 50f) // break match
        e.onTick(3_100L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f) // match again
        assertFalse(e.onTick(8_000L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f)) // only 4.9s
        assertTrue(e.onTick(8_100L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f))
    }

    @Test
    fun interruptingMismatchResetsMismatchWindow() {
        val e = LocationTruthEvaluator()
        e.onTick(0L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f)
        assertTrue(e.onTick(5_000L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f))
        e.onTick(5_100L, hasFix = true, navSpeedKmH = 80f, carSpeedKmH = 50f)
        e.onTick(8_000L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f) // interrupt mismatch
        assertTrue(e.currentTruth()) // still true; match window restarts, not yet 5s
        assertTrue(e.onTick(13_000L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f))
    }

    @Test
    fun resetClearsToFalse() {
        val e = LocationTruthEvaluator()
        e.onTick(0L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f)
        assertTrue(e.onTick(5_000L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f))
        e.reset()
        assertFalse(e.currentTruth())
        assertFalse(e.onTick(5_001L, hasFix = true, navSpeedKmH = 50f, carSpeedKmH = 50f))
    }

    @Test
    fun hasFixRejectsZeroCoordinates() {
        assertFalse(LocationTruthEvaluator.hasFix(true, 0.0, 0.0))
        assertTrue(LocationTruthEvaluator.hasFix(true, 55.0, 37.0))
        assertFalse(LocationTruthEvaluator.hasFix(false, 55.0, 37.0))
    }

    @Test
    fun isMatchingToleranceInclusive() {
        assertTrue(LocationTruthEvaluator.isMatching(true, 60f, 50f))
        assertTrue(LocationTruthEvaluator.isMatching(true, 40f, 50f))
        assertFalse(LocationTruthEvaluator.isMatching(true, 61f, 50f))
        assertFalse(LocationTruthEvaluator.isMatching(true, 50f, null))
        assertFalse(LocationTruthEvaluator.isMatching(false, 50f, 50f))
    }

    @Test
    fun startFromFalseNeedsFullMatchWindowEvenAfterMismatch() {
        val e = LocationTruthEvaluator()
        assertEquals(false, e.onTick(0L, hasFix = false, navSpeedKmH = 0f, carSpeedKmH = null))
        assertFalse(e.onTick(5_000L, hasFix = false, navSpeedKmH = 0f, carSpeedKmH = null))
        e.onTick(5_100L, hasFix = true, navSpeedKmH = 30f, carSpeedKmH = 30f)
        assertFalse(e.onTick(10_000L, hasFix = true, navSpeedKmH = 30f, carSpeedKmH = 30f))
        assertTrue(e.onTick(10_100L, hasFix = true, navSpeedKmH = 30f, carSpeedKmH = 30f))
    }
}
