package vad.dashing.tbox.fuellevelcalibration

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelLevelMathTest {

    @Test
    fun linearLiters_fromFilteredPercent_scalesWithTank() {
        assertEquals(54.72f, FuelLevelMath.linearLitersFromFilteredPercent(96f, 57f), 1e-4f)
        assertEquals(28.5f, FuelLevelMath.linearLitersFromFilteredPercent(50f, 57f), 1e-4f)
    }

    @Test
    fun linearLiters_coercesNonPositiveTankToOneLiter() {
        assertEquals(0.48f, FuelLevelMath.linearLitersFromFilteredPercent(48f, 0f), 1e-4f)
    }
}
