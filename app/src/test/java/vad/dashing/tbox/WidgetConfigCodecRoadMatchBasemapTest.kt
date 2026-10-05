package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.location.roadmatch.RoadMatchBasemapOpacity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecRoadMatchBasemapTest {
    @Test
    fun defaultsOmitBasemapFieldsFromJson() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY),
            ),
        )
        assertFalse(json.contains("roadMatchMapKitBasemap"))
        assertFalse(json.contains("roadMatchBasemapTransparencyPercent"))
    }

    @Test
    fun roundTripsBasemapSettings() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY,
                    roadMatchMapKitBasemap = true,
                    roadMatchBasemapTransparencyPercent = 45,
                ),
            ),
        )
        assertTrue(json.contains("\"roadMatchMapKitBasemap\":true"))
        assertTrue(json.contains("\"roadMatchBasemapTransparencyPercent\":45"))
        val parsed = parseWidgetConfigsFromString(json)
        assertTrue(parsed[0].roadMatchMapKitBasemap)
        assertEquals(45, parsed[0].roadMatchBasemapTransparencyPercent)
    }

    @Test
    fun nonRoadMatchWidgetClearsBasemapFlagsOnParse() {
        val raw = """[{"dataKey":"voltage","roadMatchMapKitBasemap":true,"roadMatchBasemapTransparencyPercent":60}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertFalse(parsed[0].roadMatchMapKitBasemap)
        assertEquals(0, parsed[0].roadMatchBasemapTransparencyPercent)
    }

    @Test
    fun transparencyNormalizesOnParse() {
        val raw =
            """[{"dataKey":"roadMatchMapWidget","roadMatchBasemapTransparencyPercent":44}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertEquals(
            RoadMatchBasemapOpacity.normalize(44),
            parsed[0].roadMatchBasemapTransparencyPercent,
        )
    }
}
