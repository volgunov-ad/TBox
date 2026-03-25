package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFuelAccountingTest {

    private val tank = 57f

    @Test
    fun firstSample_noConsumption() {
        val r = TripFuelAccounting.applyFuelPercentStep(0f, null, 50f, tank)
        assertEquals(0f, r.consumedLiters, 1e-4f)
        assertEquals(50f, r.baselinePercent, 1e-4f)
        assertFalse(r.refuelDetected)
    }

    @Test
    fun refuel_largeRise_noConsumption_flagged() {
        val r = TripFuelAccounting.applyFuelPercentStep(1f, 30f, 80f, tank)
        assertEquals(1f, r.consumedLiters, 1e-4f)
        assertEquals(80f, r.baselinePercent, 1e-4f)
        assertTrue(r.refuelDetected)
    }

    @Test
    fun consume_dropAccumulates() {
        val r = TripFuelAccounting.applyFuelPercentStep(0f, 50f, 45f, tank)
        assertEquals(2.85f, r.consumedLiters, 1e-3f)
        assertFalse(r.refuelDetected)
    }

    @Test
    fun noise_smallDropIgnored() {
        val r = TripFuelAccounting.applyFuelPercentStep(0f, 50f, 49.9f, tank)
        assertEquals(0f, r.consumedLiters, 1e-4f)
        assertFalse(r.refuelDetected)
    }
}
