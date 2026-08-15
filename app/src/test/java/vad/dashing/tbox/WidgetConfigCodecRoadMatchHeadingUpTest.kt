package vad.dashing.tbox

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.ui.persistDashboardPanelRoadMatchHeadingUp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecRoadMatchHeadingUpTest {

    @Test
    fun defaultsOffAndOmitsKeyWhenFalse() {
        val map = FloatingDashboardWidgetConfig(dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY)
        val other = FloatingDashboardWidgetConfig(
            dataKey = "espConnected",
            roadMatchHeadingUp = true,
        )
        assertFalse(map.roadMatchHeadingUp)
        val json = serializeWidgetConfigs(listOf(map, other))
        assertFalse(json.contains("roadMatchHeadingUp"))
    }

    @Test
    fun serializesTrueOnlyForMapTile() {
        val map = FloatingDashboardWidgetConfig(
            dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY,
            roadMatchHeadingUp = true,
        )
        val json = serializeWidgetConfigs(listOf(map))
        assertTrue(json.contains("\"roadMatchHeadingUp\":true"))
        val parsed = parseWidgetConfigsFromString(json)
        assertTrue(parsed[0].roadMatchHeadingUp)
    }

    @Test
    fun missingKeyStaysOff() {
        val raw = """[{"dataKey":"roadMatchMapWidget","showTitle":true}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertFalse(parsed[0].roadMatchHeadingUp)
        assertEquals(ROAD_MATCH_MAP_WIDGET_DATA_KEY, parsed[0].dataKey)
    }

    @Test
    fun persistHelperWritesPerInstanceAndIgnoresOtherTiles() {
        val configs = listOf(
            FloatingDashboardWidgetConfig(dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY),
            FloatingDashboardWidgetConfig(
                dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY,
                roadMatchHeadingUp = true,
            ),
            FloatingDashboardWidgetConfig(dataKey = "espConnected"),
        )
        var saved: List<FloatingDashboardWidgetConfig>? = null
        persistDashboardPanelRoadMatchHeadingUp(
            currentWidgetConfigs = configs,
            widgetIndex = 0,
            headingUp = true,
        ) { saved = it }
        assertTrue(saved!![0].roadMatchHeadingUp)
        assertTrue(saved[1].roadMatchHeadingUp)
        assertFalse(saved[2].roadMatchHeadingUp)

        persistDashboardPanelRoadMatchHeadingUp(
            currentWidgetConfigs = saved,
            widgetIndex = 2,
            headingUp = true,
        ) { saved = it }
        assertFalse(saved!![2].roadMatchHeadingUp)
    }

    @Test
    fun unknownJsonArrayStillParsesMapKey() {
        val arr = JSONArray(
            """[{"dataKey":"roadMatchMapWidget","roadMatchHeadingUp":true}]""",
        )
        val parsed = parseWidgetConfigsFromAny(arr)
        assertTrue(parsed[0].roadMatchHeadingUp)
    }
}
