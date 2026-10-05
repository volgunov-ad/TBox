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
import vad.dashing.tbox.fuel.RefuelPriceRefresh
import vad.dashing.tbox.fuel.RefuelRecord

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

    @Test
    fun tripFuelCostDeltaRub_addsWhenPreviousWasNull() {
        assertEquals(1115f, FuelCostAccounting.tripFuelCostDeltaRub(null, 1115f), 0.001f)
    }

    @Test
    fun tripFuelCostDeltaRub_appliesDifferenceWhenBothSet() {
        assertEquals(100f, FuelCostAccounting.tripFuelCostDeltaRub(1000f, 1100f), 0.001f)
        assertEquals(-50f, FuelCostAccounting.tripFuelCostDeltaRub(200f, 150f), 0.001f)
    }

    @Test
    fun missingPriceCandidates_keepsOnlyNullPriceRows() {
        val withPrice = RefuelRecord(
            timeEpochMs = 1L,
            actualLiters = 10f,
            pricePerLiterRub = 55f,
            latitude = 55.0,
            longitude = 37.0,
        )
        val missing = RefuelRecord(
            timeEpochMs = 2L,
            actualLiters = 12f,
            pricePerLiterRub = null,
            latitude = 55.1,
            longitude = 37.1,
        )
        val missingNoCoords = RefuelRecord(
            timeEpochMs = 3L,
            actualLiters = 8f,
            pricePerLiterRub = null,
        )
        assertEquals(
            listOf(missing, missingNoCoords),
            RefuelPriceRefresh.missingPriceCandidates(listOf(withPrice, missing, missingNoCoords)),
        )
    }

    @Test
    fun coordinatesOf_requiresNonZeroLatLng() {
        assertNull(RefuelPriceRefresh.coordinatesOf(RefuelRecord(timeEpochMs = 1L)))
        assertNull(
            RefuelPriceRefresh.coordinatesOf(
                RefuelRecord(timeEpochMs = 1L, latitude = 0.0, longitude = 0.0),
            ),
        )
        val coords = RefuelPriceRefresh.coordinatesOf(
            RefuelRecord(timeEpochMs = 1L, latitude = 55.75, longitude = 37.62),
        )
        assertEquals(55.75, coords!!.latitude, 0.0001)
        assertEquals(37.62, coords.longitude, 0.0001)
    }
}
