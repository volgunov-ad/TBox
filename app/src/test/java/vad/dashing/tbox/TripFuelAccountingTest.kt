package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class TripFuelAccountingTest {

    private val tank = 57f

    @Test
    fun firstSample_noConsumption() {
        val (c, p) = TripFuelAccounting.applyFuelPercentStep(0f, null, 50f, tank)
        assertEquals(0f, c, 1e-4f)
        assertEquals(50f, p, 1e-4f)
    }

    @Test
    fun refuel_largeRise_noConsumption() {
        val (c, p) = TripFuelAccounting.applyFuelPercentStep(1f, 30f, 80f, tank)
        assertEquals(1f, c, 1e-4f)
        assertEquals(80f, p, 1e-4f)
    }

    @Test
    fun consume_dropAccumulates() {
        val (c1, _) = TripFuelAccounting.applyFuelPercentStep(0f, 50f, 45f, tank)
        assertEquals(2.85f, c1, 1e-3f)
    }

    @Test
    fun noise_smallDropIgnored() {
        val (c, _) = TripFuelAccounting.applyFuelPercentStep(0f, 50f, 49.9f, tank)
        assertEquals(0f, c, 1e-4f)
    }
}
