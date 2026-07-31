package vad.dashing.tbox

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecAccCruiseTest {

    @Test
    fun serializesCustomAccFieldsOnlyForAccTile() {
        val acc = FloatingDashboardWidgetConfig(
            dataKey = ACC_CRUISE_WIDGET_DATA_KEY,
            accCruiseTargetKmh = 110,
            accCruiseIncreaseIntervalMs = 200,
            accCruiseDecreaseIntervalMs = 80,
        )
        val other = FloatingDashboardWidgetConfig(
            dataKey = PARKING_RADAR_WIDGET_DATA_KEY,
            accCruiseTargetKmh = 110,
            accCruiseIncreaseIntervalMs = 200,
        )
        val json = serializeWidgetConfigs(listOf(acc, other))
        val arr = JSONArray(json)
        assertEquals(110, arr.getJSONObject(0).getInt("accCruiseTargetKmh"))
        assertEquals(200, arr.getJSONObject(0).getInt("accCruiseIncreaseIntervalMs"))
        assertEquals(80, arr.getJSONObject(0).getInt("accCruiseDecreaseIntervalMs"))
        assertFalse(arr.getJSONObject(1).has("accCruiseTargetKmh"))
        assertFalse(arr.getJSONObject(1).has("accCruiseIncreaseIntervalMs"))
    }

    @Test
    fun omitsDefaultAccFields() {
        val acc = FloatingDashboardWidgetConfig(dataKey = ACC_CRUISE_WIDGET_DATA_KEY)
        val json = serializeWidgetConfigs(listOf(acc))
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("accCruiseTargetKmh"))
        assertFalse(obj.has("accCruiseIncreaseIntervalMs"))
        assertFalse(obj.has("accCruiseDecreaseIntervalMs"))
    }

    @Test
    fun roundTripsAndClamps() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = ACC_CRUISE_WIDGET_DATA_KEY,
                accCruiseTargetKmh = 200,
                accCruiseIncreaseIntervalMs = 10,
                accCruiseDecreaseIntervalMs = 9000,
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals(150, parsed[0].accCruiseTargetKmh)
        assertEquals(50, parsed[0].accCruiseIncreaseIntervalMs)
        assertEquals(1500, parsed[0].accCruiseDecreaseIntervalMs)
    }
}
