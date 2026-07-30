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
class WidgetConfigCodecDriveModeCycleTest {

    @Test
    fun omitsDefaultSelectionAndIgnoresFieldOnOtherWidgets() {
        val cycleDefault = FloatingDashboardWidgetConfig(
            dataKey = DRIVE_MODE_CYCLE_WIDGET_DATA_KEY,
            selectedDriveModes = DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES,
        )
        val other = FloatingDashboardWidgetConfig(
            dataKey = DRIVE_MODE_WIDGET_DATA_KEY,
            selectedDriveModes = listOf(101, 102),
        )
        val json = serializeWidgetConfigs(listOf(cycleDefault, other))
        val arr = JSONArray(json)
        assertFalse(arr.getJSONObject(0).has("selectedDriveModes"))
        assertFalse(arr.getJSONObject(1).has("selectedDriveModes"))
    }

    @Test
    fun roundTripsCustomSelection() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = DRIVE_MODE_CYCLE_WIDGET_DATA_KEY,
                selectedDriveModes = listOf(2, 1),
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals(listOf(2, 1), parsed[0].selectedDriveModes)
    }

    @Test
    fun missingArrayUsesDefault() {
        val raw = """[{"dataKey":"driveModeCycleWidget"}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertEquals(DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES, parsed[0].selectedDriveModes)
    }

    @Test
    fun mixedFamilyNormalizedOnDecode() {
        val raw = """[{"dataKey":"driveModeCycleWidget","selectedDriveModes":[2,101,0]}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertEquals(listOf(2, 0), parsed[0].selectedDriveModes)
    }
}
