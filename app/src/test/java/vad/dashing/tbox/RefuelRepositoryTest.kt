package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class RefuelRepositoryTest {

    @Test
    fun repository_keepsNewestThirtyRefuels() {
        RefuelRepository.resetForUnitTests()

        repeat(35) { index ->
            RefuelRepository.appendRefuel(RefuelRecord(id = "r$index", timeEpochMs = index.toLong()))
        }

        val refuels = RefuelRepository.refuels.value
        assertEquals(30, refuels.size)
        assertEquals("r5", refuels.first().id)
        assertEquals("r34", refuels.last().id)
    }

    @Test
    fun manualActualLitersAndPriceRecalculateOnlyRefuelCost() {
        RefuelRepository.resetForUnitTests()
        RefuelRepository.appendRefuel(
            RefuelRecord(id = "r1", timeEpochMs = 1L, actualLiters = 10f, pricePerLiterRub = 50f)
        )

        RefuelRepository.updateActualLiters("r1", 12f)
        RefuelRepository.updatePricePerLiter("r1", 55f)

        val updated = RefuelRepository.refuels.value.single()
        assertEquals(12f, updated.actualLiters, 0.0001f)
        assertEquals(55f, updated.pricePerLiterRub!!, 0.0001f)
        assertEquals(660f, updated.costRub!!, 0.0001f)
    }
}
