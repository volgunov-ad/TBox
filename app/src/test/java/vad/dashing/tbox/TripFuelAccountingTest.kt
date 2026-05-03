package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.trip.TripFuelAccounting

class TripFuelAccountingTest {

    private val tank = 57f

    @Test
    fun firstSample_noConsumption() {
        val litersNow = 50f / 100f * tank
        val r = TripFuelAccounting.applyFuelCalibratedLitersStep(
            currentConsumedLiters = 0f,
            lastCalibratedLiters = null,
            litersNow = litersNow,
            baselinePercentNow = 50f,
            tankLiters = tank,
        )
        assertEquals(0f, r.consumedLiters, 1e-4f)
        assertEquals(litersNow, r.baselineCalibratedLiters, 1e-4f)
        assertEquals(50f, r.baselinePercent, 1e-4f)
        assertFalse(r.refuelDetected)
        assertEquals(0f, r.refueledLitersThisStep, 1e-4f)
    }

    @Test
    fun refuel_largeRise_noConsumption_flagged() {
        val lastL = 30f / 100f * tank
        val nowL = 80f / 100f * tank
        val r = TripFuelAccounting.applyFuelCalibratedLitersStep(
            currentConsumedLiters = 1f,
            lastCalibratedLiters = lastL,
            litersNow = nowL,
            baselinePercentNow = 80f,
            tankLiters = tank,
        )
        assertEquals(1f, r.consumedLiters, 1e-4f)
        assertEquals(nowL, r.baselineCalibratedLiters, 1e-3f)
        assertEquals(80f, r.baselinePercent, 1e-4f)
        assertTrue(r.refuelDetected)
        assertEquals(28.5f, r.refueledLitersThisStep, 1e-3f)
    }

    @Test
    fun consume_dropAccumulates() {
        val lastL = 50f / 100f * tank
        val nowL = 45f / 100f * tank
        val r = TripFuelAccounting.applyFuelCalibratedLitersStep(
            currentConsumedLiters = 0f,
            lastCalibratedLiters = lastL,
            litersNow = nowL,
            baselinePercentNow = 45f,
            tankLiters = tank,
        )
        assertEquals(2.85f, r.consumedLiters, 1e-3f)
        assertFalse(r.refuelDetected)
        assertEquals(0f, r.refueledLitersThisStep, 1e-4f)
    }

    @Test
    fun noise_smallDropIgnored() {
        val lastL = 50f / 100f * tank
        val nowL = 49.9f / 100f * tank
        val r = TripFuelAccounting.applyFuelCalibratedLitersStep(
            currentConsumedLiters = 0f,
            lastCalibratedLiters = lastL,
            litersNow = nowL,
            baselinePercentNow = 49.9f,
            tankLiters = tank,
        )
        assertEquals(0f, r.consumedLiters, 1e-4f)
        assertFalse(r.refuelDetected)
        assertEquals(0f, r.refueledLitersThisStep, 1e-4f)
    }
}
