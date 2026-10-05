package vad.dashing.tbox

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.trip.ActiveTripCustomWidgetField
import vad.dashing.tbox.trip.TripMetricFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecTripMetricTest {

    @Test
    fun omitsDefaultFieldAndSource() {
        val tile = FloatingDashboardWidgetConfig(
            dataKey = TRIP_METRIC_WIDGET_DATA_KEY,
        )
        val json = serializeWidgetConfigs(listOf(tile))
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("tripMetricFieldId"))
        assertFalse(obj.has("tripWidgetSource"))
    }

    @Test
    fun serializesNonDefaultFieldAndSourceOnlyForTripMetricTile() {
        val metric = FloatingDashboardWidgetConfig(
            dataKey = TRIP_METRIC_WIDGET_DATA_KEY,
            tripWidgetSource = TRIP_WIDGET_SOURCE_PERSISTENT,
            tripMetricFieldId = ActiveTripCustomWidgetField.AVG_SPEED_MOVING.id,
        )
        val other = FloatingDashboardWidgetConfig(
            dataKey = "carSpeed",
            tripWidgetSource = TRIP_WIDGET_SOURCE_PERSISTENT,
            tripMetricFieldId = ActiveTripCustomWidgetField.AVG_SPEED_MOVING.id,
        )
        val json = serializeWidgetConfigs(listOf(metric, other))
        val arr = JSONArray(json)
        assertEquals(
            TRIP_WIDGET_SOURCE_PERSISTENT,
            arr.getJSONObject(0).getInt("tripWidgetSource"),
        )
        assertEquals(
            ActiveTripCustomWidgetField.AVG_SPEED_MOVING.id,
            arr.getJSONObject(0).getString("tripMetricFieldId"),
        )
        assertFalse(arr.getJSONObject(1).has("tripWidgetSource"))
        assertFalse(arr.getJSONObject(1).has("tripMetricFieldId"))
    }

    @Test
    fun roundTripsAndNormalizesInvalidField() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = TRIP_METRIC_WIDGET_DATA_KEY,
                tripWidgetSource = TRIP_WIDGET_SOURCE_PERSISTENT,
                tripMetricFieldId = ActiveTripCustomWidgetField.FUEL_CONSUMPTION.id,
            ),
            FloatingDashboardWidgetConfig(
                dataKey = TRIP_METRIC_WIDGET_DATA_KEY,
                tripMetricFieldId = "not_a_real_field",
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals(TRIP_WIDGET_SOURCE_PERSISTENT, parsed[0].tripWidgetSource)
        assertEquals(ActiveTripCustomWidgetField.FUEL_CONSUMPTION.id, parsed[0].tripMetricFieldId)
        assertEquals(ActiveTripCustomWidgetField.DISTANCE.id, parsed[1].tripMetricFieldId)
        assertEquals(
            ActiveTripCustomWidgetField.DISTANCE.id,
            TripMetricFormatter.normalizeFieldId("bogus"),
        )
    }

    @Test
    fun activeTripWidgetStillSerializesSource() {
        val tile = FloatingDashboardWidgetConfig(
            dataKey = ACTIVE_TRIP_WIDGET_DATA_KEY,
            tripWidgetSource = TRIP_WIDGET_SOURCE_PERSISTENT,
        )
        val json = serializeWidgetConfigs(listOf(tile))
        assertEquals(
            TRIP_WIDGET_SOURCE_PERSISTENT,
            JSONArray(json).getJSONObject(0).getInt("tripWidgetSource"),
        )
        assertTrue(usesTripWidgetSource(ACTIVE_TRIP_WIDGET_DATA_KEY))
        assertTrue(usesTripWidgetSource(TRIP_METRIC_WIDGET_DATA_KEY))
        assertFalse(usesTripWidgetSource("carSpeed"))
    }
}
