package vad.dashing.tbox

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TripSerializationFuelCostTest {

    @Test
    fun tripJson_roundTripsFuelRefueledCost() {
        val trip = TripRecord(
            startTimeEpochMs = 1L,
            fuelRefueledLiters = 20f,
            fuelRefueledCostRub = 1234.56f,
        )

        val restored = TripRecord.fromJson(trip.toJson())

        assertEquals(1234.56f, restored.fuelRefueledCostRub, 0.01f)
    }

    @Test
    fun tripJson_legacyWithoutFuelCostDefaultsToZero() {
        val raw = JSONObject()
            .put("start", 1L)

        val restored = TripRecord.fromJson(raw)

        assertEquals(0f, restored.fuelRefueledCostRub, 0.0001f)
    }

    @Test
    fun tripsListJson_includesFuelRefueledCostKey() {
        val raw = tripsListToJson(
            listOf(
                TripRecord(
                    startTimeEpochMs = 1L,
                    fuelRefueledCostRub = 42f,
                )
            )
        )

        val item = JSONArray(raw).getJSONObject(0)

        assertEquals(42.0, item.optDouble("fuelRefueledCostRub"), 0.0001)
        assertFalse(tripsListFromJson(raw).isEmpty())
    }
}
