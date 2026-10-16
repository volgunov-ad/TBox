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
class WidgetConfigCodecFuelLitersDisplayTest {

    @Test
    fun omitsDefaultRemainingMode() {
        val tile = FloatingDashboardWidgetConfig(
            dataKey = FUEL_LEVEL_LITERS_WIDGET_DATA_KEY,
        )
        val json = serializeWidgetConfigs(listOf(tile))
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("fuelLitersDisplayMode"))
    }

    @Test
    fun serializesToFullOnlyForLitersTiles() {
        val liters = FloatingDashboardWidgetConfig(
            dataKey = FUEL_LEVEL_LITERS_WIDGET_DATA_KEY,
            fuelLitersDisplayMode = FUEL_LITERS_DISPLAY_TO_FULL,
        )
        val combined = FloatingDashboardWidgetConfig(
            dataKey = FUEL_LEVEL_WIDGET_DATA_KEY,
            fuelLitersDisplayMode = FUEL_LITERS_DISPLAY_TO_FULL,
        )
        val percent = FloatingDashboardWidgetConfig(
            dataKey = FUEL_LEVEL_PERCENTAGE_WIDGET_DATA_KEY,
            fuelLitersDisplayMode = FUEL_LITERS_DISPLAY_TO_FULL,
        )
        val json = serializeWidgetConfigs(listOf(liters, combined, percent))
        val arr = JSONArray(json)
        assertEquals(FUEL_LITERS_DISPLAY_TO_FULL, arr.getJSONObject(0).getInt("fuelLitersDisplayMode"))
        assertEquals(FUEL_LITERS_DISPLAY_TO_FULL, arr.getJSONObject(1).getInt("fuelLitersDisplayMode"))
        assertFalse(arr.getJSONObject(2).has("fuelLitersDisplayMode"))
    }

    @Test
    fun roundTripsAndNormalizesInvalidMode() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = FUEL_LEVEL_LITERS_ACTUAL_WIDGET_DATA_KEY,
                fuelLitersDisplayMode = FUEL_LITERS_DISPLAY_TO_FULL,
            ),
            FloatingDashboardWidgetConfig(
                dataKey = FUEL_LEVEL_LITERS_WIDGET_DATA_KEY,
                fuelLitersDisplayMode = 99,
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals(FUEL_LITERS_DISPLAY_TO_FULL, parsed[0].fuelLitersDisplayMode)
        assertEquals(FUEL_LITERS_DISPLAY_REMAINING, parsed[1].fuelLitersDisplayMode)
    }
}
