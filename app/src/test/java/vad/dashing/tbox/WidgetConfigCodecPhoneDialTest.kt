package vad.dashing.tbox

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecPhoneDialTest {

    @Test
    fun roundTrip_phoneDialFields() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = PHONE_DIAL_WIDGET_DATA_KEY,
                dialPhoneNumber = "+7 (920) 028-76-93",
                dialShowNumber = false,
                dialConfirmBeforeCall = true,
                showTitle = true,
                customTitle = "Мама",
            ),
        )
        val json = serializeWidgetConfigs(original)
        val obj = JSONArray(json).getJSONObject(0)
        assertEquals(PHONE_DIAL_WIDGET_DATA_KEY, obj.getString("dataKey"))
        assertEquals("+7 (920) 028-76-93", obj.getString("dialPhoneNumber"))
        assertFalse(obj.getBoolean("dialShowNumber"))
        assertTrue(obj.getBoolean("dialConfirmBeforeCall"))
        val cfg = parseWidgetConfigsFromString(json).single()
        assertEquals("+7 (920) 028-76-93", cfg.dialPhoneNumber)
        assertFalse(cfg.dialShowNumber)
        assertTrue(cfg.dialConfirmBeforeCall)
        assertEquals("Мама", cfg.customTitle)
    }

    @Test
    fun encode_omitsDefaultShowNumberAndConfirm() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = PHONE_DIAL_WIDGET_DATA_KEY,
                dialPhoneNumber = "900",
                dialShowNumber = true,
                dialConfirmBeforeCall = false,
            ),
        )
        val obj = JSONArray(serializeWidgetConfigs(original)).getJSONObject(0)
        assertEquals("900", obj.getString("dialPhoneNumber"))
        assertFalse(obj.has("dialShowNumber"))
        assertFalse(obj.has("dialConfirmBeforeCall"))
    }

    @Test
    fun decode_defaultsShowNumberTrue() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("dataKey", PHONE_DIAL_WIDGET_DATA_KEY)
                    .put("dialPhoneNumber", "123"),
            )
            .toString()
        val cfg = parseWidgetConfigsFromString(json).single()
        assertEquals("123", cfg.dialPhoneNumber)
        assertTrue(cfg.dialShowNumber)
        assertFalse(cfg.dialConfirmBeforeCall)
    }

    @Test
    fun decode_ignoresDialFieldsForOtherWidgets() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("dataKey", HTTP_REQUEST_WIDGET_DATA_KEY)
                    .put("httpRequestYaml", "url: \"http://example.com\"")
                    .put("dialPhoneNumber", "+7900")
                    .put("dialShowNumber", false)
                    .put("dialConfirmBeforeCall", true),
            )
            .toString()
        val cfg = parseWidgetConfigsFromString(json).single()
        assertEquals("", cfg.dialPhoneNumber)
        assertTrue(cfg.dialShowNumber)
        assertFalse(cfg.dialConfirmBeforeCall)
    }
}
