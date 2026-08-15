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
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecFreeformTest {

    @Test
    fun roundTrip_freeformFields() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
                launcherAppPackage = "com.example.maps",
                launcherLaunchMode = AppLauncherLaunchMode.FREEFORM,
                launcherFreeformEnabled = true,
                launcherFreeformSide = FreeformLaunchSide.LEFT,
                launcherFreeformPercent = 40,
                launcherFreeformOverlayPage = 3,
                launcherFreeformOverlayCrop = true,
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals(1, parsed.size)
        val cfg = parsed[0]
        assertEquals("com.example.maps", cfg.launcherAppPackage)
        assertEquals(AppLauncherLaunchMode.FREEFORM, cfg.launcherLaunchMode)
        assertTrue(cfg.launcherFreeformEnabled)
        assertEquals(FreeformLaunchSide.LEFT, cfg.launcherFreeformSide)
        assertEquals(40, cfg.launcherFreeformPercent)
        assertEquals(3, cfg.launcherFreeformOverlayPage)
        assertTrue(cfg.launcherFreeformOverlayCrop)
    }

    @Test
    fun roundTrip_stockWindowMode() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
                launcherAppPackage = "com.example.browser",
                launcherLaunchMode = AppLauncherLaunchMode.STOCK_WINDOW,
            ),
        )
        val json = serializeWidgetConfigs(original)
        val obj = JSONArray(json).getJSONObject(0)
        assertEquals("stock_window", obj.getString("launcherLaunchMode"))
        assertFalse(obj.has("launcherFreeformEnabled"))
        val cfg = parseWidgetConfigsFromString(json).single()
        assertEquals(AppLauncherLaunchMode.STOCK_WINDOW, cfg.launcherLaunchMode)
        assertFalse(cfg.launcherFreeformEnabled)
    }

    @Test
    fun decode_legacyFreeformEnabled_withoutLaunchMode() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("dataKey", APP_LAUNCHER_WIDGET_DATA_KEY)
                    .put("launcherAppPackage", "legacy.app")
                    .put("launcherFreeformEnabled", true)
                    .put("launcherFreeformSide", "right")
                    .put("launcherFreeformPercent", 50),
            )
            .toString()
        val cfg = parseWidgetConfigsFromString(json).single()
        assertEquals(AppLauncherLaunchMode.FREEFORM, cfg.launcherLaunchMode)
        assertTrue(cfg.launcherFreeformEnabled)
        assertEquals(FreeformLaunchSide.RIGHT, cfg.launcherFreeformSide)
        assertEquals(50, cfg.launcherFreeformPercent)
    }

    @Test
    fun encode_omitsFreeformWhenDisabled() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
                    launcherAppPackage = "com.example.app",
                    launcherLaunchMode = AppLauncherLaunchMode.FULLSCREEN,
                    launcherFreeformEnabled = false,
                    launcherFreeformSide = FreeformLaunchSide.TOP,
                    launcherFreeformPercent = 60,
                    launcherFreeformOverlayPage = 2,
                ),
            ),
        )
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("launcherLaunchMode"))
        assertFalse(obj.has("launcherFreeformEnabled"))
        assertFalse(obj.has("launcherFreeformSide"))
        assertFalse(obj.has("launcherFreeformPercent"))
        assertFalse(obj.has("launcherFreeformOverlayPage"))
    }

    @Test
    fun decode_clampsPercent() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("dataKey", APP_LAUNCHER_WIDGET_DATA_KEY)
                    .put("launcherAppPackage", "x")
                    .put("launcherLaunchMode", "freeform")
                    .put("launcherFreeformEnabled", true)
                    .put("launcherFreeformSide", "bottom")
                    .put("launcherFreeformPercent", 99),
            )
            .toString()
        val cfg = parseWidgetConfigsFromString(json).single()
        assertEquals(FreeformLaunchBounds.MAX_PERCENT, cfg.launcherFreeformPercent)
        assertEquals(FreeformLaunchSide.BOTTOM, cfg.launcherFreeformSide)
        assertEquals(AppLauncherLaunchMode.FREEFORM, cfg.launcherLaunchMode)
    }

    @Test
    fun encode_omitsOverlayCropWhenFalse() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
                    launcherAppPackage = "com.example.app",
                    launcherLaunchMode = AppLauncherLaunchMode.FREEFORM,
                    launcherFreeformEnabled = true,
                    launcherFreeformOverlayCrop = false,
                ),
            ),
        )
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("launcherFreeformOverlayCrop"))
    }

    @Test
    fun decode_invalidOverlayPage_keepsCurrentPage() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("dataKey", APP_LAUNCHER_WIDGET_DATA_KEY)
                    .put("launcherLaunchMode", "freeform")
                    .put("launcherFreeformOverlayPage", 0),
            )
            .toString()

        val cfg = parseWidgetConfigsFromString(json).single()

        assertEquals(null, cfg.launcherFreeformOverlayPage)
    }
}
