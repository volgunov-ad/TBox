package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Test

class FragranceCommandRegistryTest {

    @Test
    fun smellAndConcentration_acceptOnlyStockA9Values() {
        listOf(
            MbCanKnownVehiclePropertyId.FRAGRANCE_SMELL,
            MbCanKnownVehiclePropertyId.FRAGRANCE_CONCENTRATION,
        ).forEach { propertyId ->
            val policy = MbCanCommandRegistry.get(propertyId)!!.policy as MbCanCommandPolicy.SetExact
            assertEquals(setOf(1, 2, 3), policy.allowedValues)
        }
    }
}
