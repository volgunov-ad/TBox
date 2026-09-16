package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.obd.ObdPid

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecObdMetricTest {
    @Test
    fun serialize_omitsDefaultRpmPid() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = OBD_METRIC_WIDGET_DATA_KEY,
                    obdPidId = ObdPid.RPM.id,
                ),
            ),
        )
        assertFalse(json.contains("obdPidId"))
    }

    @Test
    fun serialize_andParse_customPid() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = OBD_METRIC_WIDGET_DATA_KEY,
                    obdPidId = ObdPid.COOLANT_TEMP.id,
                ),
            ),
        )
        assertTrue(json.contains("obdPidId"))
        val parsed = parseWidgetConfigsFromString(json)
        assertEquals(1, parsed.size)
        assertEquals(ObdPid.COOLANT_TEMP.id, parsed[0].obdPidId)
    }

    @Test
    fun parse_unknownPid_fallsBackToRpm() {
        val raw = """[{"dataKey":"obdMetricWidget","obdPidId":"not_real"}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertEquals(ObdPid.RPM.id, parsed[0].obdPidId)
    }
}
