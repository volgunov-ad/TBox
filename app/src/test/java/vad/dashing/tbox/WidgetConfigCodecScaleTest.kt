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
class WidgetConfigCodecScaleTest {

    @Test
    fun serialize_defaultScales_omitsLegacyAndNewFields() {
        val json = serializeWidgetConfigs(
            listOf(FloatingDashboardWidgetConfig(dataKey = "voltage"))
        )
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("scale"))
        assertFalse(obj.has("titleScale"))
        assertFalse(obj.has("iconScale"))
        assertFalse(obj.has("textScale"))
    }

    @Test
    fun serialize_nonDefaultScales_writesThreeFieldsNotLegacy() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = "voltage",
                    titleScale = 1.2f,
                    iconScale = 1.3f,
                    textScale = 1.4f,
                )
            )
        )
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("scale"))
        assertEquals(1.2, obj.getDouble("titleScale"), 0.001)
        assertEquals(1.3, obj.getDouble("iconScale"), 0.001)
        assertEquals(1.4, obj.getDouble("textScale"), 0.001)
    }

    @Test
    fun parse_legacyScale_appliesToAllThree() {
        val parsed = parseWidgetConfigsFromString(
            """[{"dataKey":"voltage","scale":1.6}]"""
        ).single()
        assertEquals(1.6f, parsed.titleScale, 0.001f)
        assertEquals(1.6f, parsed.iconScale, 0.001f)
        assertEquals(1.6f, parsed.textScale, 0.001f)
    }

    @Test
    fun parse_partialNewFields_fallsBackToLegacyForMissing() {
        val parsed = parseWidgetConfigsFromString(
            """[{"dataKey":"voltage","scale":1.5,"iconScale":1.8}]"""
        ).single()
        assertEquals(1.5f, parsed.titleScale, 0.001f)
        assertEquals(1.8f, parsed.iconScale, 0.001f)
        assertEquals(1.5f, parsed.textScale, 0.001f)
    }

    @Test
    fun roundTrip_preservesIndependentScales() {
        val original = FloatingDashboardWidgetConfig(
            dataKey = "voltage",
            titleScale = 0.8f,
            iconScale = 1.1f,
            textScale = 1.9f,
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(listOf(original))).single()
        assertEquals(original.titleScale, parsed.titleScale, 0.001f)
        assertEquals(original.iconScale, parsed.iconScale, 0.001f)
        assertEquals(original.textScale, parsed.textScale, 0.001f)
    }
}
