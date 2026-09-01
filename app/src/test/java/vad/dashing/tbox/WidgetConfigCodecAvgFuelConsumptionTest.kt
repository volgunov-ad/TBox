package vad.dashing.tbox

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecAvgFuelConsumptionTest {

    @Test
    fun omitsDefaultMbCanSource() {
        val tile = FloatingDashboardWidgetConfig(
            dataKey = AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY,
        )
        val json = serializeWidgetConfigs(listOf(tile))
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("avgFuelConsumptionSource"))
    }

    @Test
    fun serializesNonDefaultSourceOnlyForAvgFuelTile() {
        val avg = FloatingDashboardWidgetConfig(
            dataKey = AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY,
            avgFuelConsumptionSource = AVG_FUEL_CONSUMPTION_SOURCE_DAILY_TRIP,
        )
        val other = FloatingDashboardWidgetConfig(
            dataKey = CURRENT_FUEL_CONSUMPTION_WIDGET_DATA_KEY,
            avgFuelConsumptionSource = AVG_FUEL_CONSUMPTION_SOURCE_DAILY_TRIP,
        )
        val json = serializeWidgetConfigs(listOf(avg, other))
        val arr = JSONArray(json)
        assertEquals(
            AVG_FUEL_CONSUMPTION_SOURCE_DAILY_TRIP,
            arr.getJSONObject(0).getInt("avgFuelConsumptionSource"),
        )
        assertFalse(arr.getJSONObject(1).has("avgFuelConsumptionSource"))
    }

    @Test
    fun roundTripsAndNormalizesInvalidSource() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY,
                avgFuelConsumptionSource = AVG_FUEL_CONSUMPTION_SOURCE_CURRENT_TRIP,
            ),
            FloatingDashboardWidgetConfig(
                dataKey = AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY,
                avgFuelConsumptionSource = 99,
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals(AVG_FUEL_CONSUMPTION_SOURCE_CURRENT_TRIP, parsed[0].avgFuelConsumptionSource)
        assertEquals(AVG_FUEL_CONSUMPTION_SOURCE_MBCAN_VHAL, parsed[1].avgFuelConsumptionSource)
    }

    @Test
    fun canInterestOnlyWhenMbCanSourceSelected() {
        val can = FloatingDashboardWidgetConfig(
            dataKey = AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY,
            avgFuelConsumptionSource = AVG_FUEL_CONSUMPTION_SOURCE_MBCAN_VHAL,
        )
        val trip = can.copy(avgFuelConsumptionSource = AVG_FUEL_CONSUMPTION_SOURCE_CURRENT_TRIP)
        assertTrue(can.isMbCanVhalAverageFuelConsumptionEnabled())
        assertFalse(trip.isMbCanVhalAverageFuelConsumptionEnabled())
    }
}
