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
class WidgetConfigCodecHvacTempStepTest {

    @Test
    fun serializesWholeStepOnlyForTempTiles() {
        val temp = FloatingDashboardWidgetConfig(
            dataKey = HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY,
            hvacTempStepTenths = HVAC_TEMP_WIDGET_STEP_WHOLE_TENTHS,
        )
        val other = FloatingDashboardWidgetConfig(
            dataKey = HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY,
            hvacTempStepTenths = HVAC_TEMP_WIDGET_STEP_WHOLE_TENTHS,
        )
        val json = serializeWidgetConfigs(listOf(temp, other))
        val arr = JSONArray(json)
        assertEquals(
            HVAC_TEMP_WIDGET_STEP_WHOLE_TENTHS,
            arr.getJSONObject(0).getInt("hvacTempStepTenths"),
        )
        assertFalse(arr.getJSONObject(1).has("hvacTempStepTenths"))
    }

    @Test
    fun omitsDefaultHalfStep() {
        val temp = FloatingDashboardWidgetConfig(
            dataKey = HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY,
        )
        val json = serializeWidgetConfigs(listOf(temp))
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("hvacTempStepTenths"))
    }

    @Test
    fun roundTripsWholeStepAndDefaultsHalf() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY,
                hvacTempStepTenths = HVAC_TEMP_WIDGET_STEP_WHOLE_TENTHS,
            ),
            FloatingDashboardWidgetConfig(
                dataKey = HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY,
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals(HVAC_TEMP_WIDGET_STEP_WHOLE_TENTHS, parsed[0].hvacTempStepTenths)
        assertEquals(HVAC_TEMP_WIDGET_STEP_TENTHS_DEFAULT, parsed[1].hvacTempStepTenths)
    }

    @Test
    fun ignoresUnknownStepValue() {
        val raw = """[{"dataKey":"$HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY","hvacTempStepTenths":7}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertEquals(HVAC_TEMP_WIDGET_STEP_TENTHS_DEFAULT, parsed[0].hvacTempStepTenths)
    }
}
