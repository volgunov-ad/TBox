package vad.dashing.tbox

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.fuel.FuelCostAccounting
import vad.dashing.tbox.fuel.FuelPriceData
import vad.dashing.tbox.fuel.FuelPriceResolver
import vad.dashing.tbox.fuel.FuelTypes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FuelPriceResolverTest {

    @Test
    fun resolve_prefersExactNearestStationPrice() {
        val data = FuelPriceData(
            listJson = JSONObject(
                """
                {"data":{"list":[{"brand":{"name":"Test Fuel"},"address":"Main st. 1","fuels":[
                  {"fuelIdRaw":11,"fuelId":"АИ-95","fuelPrice":55.75}
                ]}]}}
                """.trimIndent()
            ),
            avgJson = JSONObject("""{"data":{"avgprice":{"11":{"avg":54.10}}}}""")
        )

        val price = FuelPriceResolver.resolve(data, FuelTypes.optionFor(11))

        assertEquals(11, price?.fuelId)
        assertEquals(55.75f, price!!.pricePerLiterRub, 0.001f)
        assertTrue(price.exact)
        assertEquals("Test Fuel, Main st. 1", price.sourceName)
    }

    @Test
    fun resolve_usesAverageWhenExactPriceMissing() {
        val data = FuelPriceData(
            listJson = JSONObject("""{"data":{"list":[{"fuels":[]}]}}"""),
            avgJson = JSONObject("""{"data":{"avgprice":{"11":{"avg":54.10}}}}""")
        )

        val price = FuelPriceResolver.resolve(data, FuelTypes.optionFor(12))

        assertEquals(12, price?.fuelId)
        assertEquals(54.10f, price!!.pricePerLiterRub, 0.001f)
        assertFalse(price.exact)
        assertEquals(FuelPriceResolver.AVERAGE_PRICE_SOURCE_NAME, price.sourceName)
    }

    @Test
    fun resolve_returnsNullWhenNoPriceAvailable() {
        val data = FuelPriceData(
            listJson = JSONObject("""{"data":{"list":[]}}"""),
            avgJson = JSONObject("""{"data":{"avgprice":{}}}""")
        )

        assertNull(FuelPriceResolver.resolve(data, FuelTypes.optionFor(11)))
    }

    @Test
    fun refuelCostRub_multipliesLitersByPrice() {
        assertEquals(1115f, FuelCostAccounting.refuelCostRub(20f, 55.75f), 0.001f)
    }
}
