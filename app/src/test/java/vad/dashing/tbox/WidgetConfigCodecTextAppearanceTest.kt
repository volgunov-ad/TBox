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
class WidgetConfigCodecTextAppearanceTest {

    @Test
    fun roundTrip_textAlign_fontWeight_titlePosition() {
        val configs = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = "fuelLevelPercentageFiltered",
                textAlign = WIDGET_TEXT_ALIGN_START,
                fontWeight = WIDGET_FONT_WEIGHT_SEMI_BOLD,
                titlePosition = WIDGET_TITLE_POSITION_BOTTOM,
            ),
            FloatingDashboardWidgetConfig(
                dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
                launcherAppPackage = "com.example.app",
                titlePosition = WIDGET_TITLE_POSITION_TOP,
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(configs))
        assertEquals(WIDGET_TEXT_ALIGN_START, parsed[0].textAlign)
        assertEquals(WIDGET_FONT_WEIGHT_SEMI_BOLD, parsed[0].fontWeight)
        assertEquals(WIDGET_TITLE_POSITION_BOTTOM, parsed[0].titlePosition)
        assertEquals(WIDGET_TITLE_POSITION_TOP, parsed[1].titlePosition)
    }

    @Test
    fun parse_legacyJson_omitsAppearanceFields_usesDefaults() {
        val json = JSONArray()
            .put(org.json.JSONObject().put("dataKey", "netWidget"))
            .toString()
        val parsed = parseWidgetConfigsFromString(json)
        assertEquals(DEFAULT_WIDGET_TEXT_ALIGN, parsed.single().textAlign)
        assertEquals(DEFAULT_WIDGET_FONT_WEIGHT, parsed.single().fontWeight)
        assertEquals(WIDGET_TITLE_POSITION_TOP, parsed.single().titlePosition)
    }

    @Test
    fun parse_appLauncherWithoutTitlePosition_defaultsToBottom() {
        val json = JSONArray()
            .put(
                org.json.JSONObject()
                    .put("dataKey", APP_LAUNCHER_WIDGET_DATA_KEY)
                    .put("launcherAppPackage", "com.example.app")
            )
            .toString()
        val parsed = parseWidgetConfigsFromString(json)
        assertEquals(WIDGET_TITLE_POSITION_BOTTOM, parsed.single().titlePosition)
    }

    @Test
    fun serialize_omitsDefaultAppearanceFields() {
        val configs = listOf(
            FloatingDashboardWidgetConfig(dataKey = "netWidget"),
            FloatingDashboardWidgetConfig(
                dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
                launcherAppPackage = "com.example.app",
                titlePosition = WIDGET_TITLE_POSITION_BOTTOM,
            ),
        )
        val array = serializeWidgetConfigsToJsonArray(configs)
        assertFalse(array.getJSONObject(0).has("textAlign"))
        assertFalse(array.getJSONObject(0).has("fontWeight"))
        assertFalse(array.getJSONObject(0).has("titlePosition"))
        assertFalse(array.getJSONObject(1).has("titlePosition"))
        assertFalse(array.getJSONObject(0).has("paddingTopPercent"))
        assertFalse(array.getJSONObject(0).has("paddingBottomPercent"))
        assertFalse(array.getJSONObject(0).has("paddingStartPercent"))
        assertFalse(array.getJSONObject(0).has("paddingEndPercent"))
    }

    @Test
    fun roundTrip_paddingPercents() {
        val configs = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = "netWidget",
                paddingTopPercent = 10,
                paddingBottomPercent = 20,
                paddingStartPercent = 5,
                paddingEndPercent = 50,
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(configs))
        assertEquals(10, parsed.single().paddingTopPercent)
        assertEquals(20, parsed.single().paddingBottomPercent)
        assertEquals(5, parsed.single().paddingStartPercent)
        assertEquals(50, parsed.single().paddingEndPercent)
    }

    @Test
    fun parse_paddingOutOfRange_isClamped() {
        val json = JSONArray()
            .put(
                org.json.JSONObject()
                    .put("dataKey", "netWidget")
                    .put("paddingTopPercent", 99)
                    .put("paddingEndPercent", -3)
            )
            .toString()
        val parsed = parseWidgetConfigsFromString(json)
        assertEquals(MAX_WIDGET_PADDING_PERCENT, parsed.single().paddingTopPercent)
        assertEquals(DEFAULT_WIDGET_PADDING_PERCENT, parsed.single().paddingEndPercent)
    }
}
