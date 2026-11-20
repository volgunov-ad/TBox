package vad.dashing.tbox

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.dcl.DclLauncherApiContract
import vad.dashing.tbox.dcl.DclLauncherEnvironmentRouting
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecMusicLaunchModeTest {

    @Test
    fun musicWidget_defaultLaunchMode_isFullscreenAndOmittedFromJson() {
        val cfg = FloatingDashboardWidgetConfig(
            dataKey = MUSIC_WIDGET_DATA_KEY,
            mediaPlayers = listOf("ru.yandex.music"),
        )
        assertEquals(AppLauncherLaunchMode.DEFAULT, cfg.launcherLaunchMode)
        assertTrue(usesAppLauncherLaunchModeFields(cfg.dataKey))

        val obj = JSONArray(serializeWidgetConfigs(listOf(cfg))).getJSONObject(0)
        assertFalse(obj.has("launcherLaunchMode"))
        assertFalse(obj.has("launcherFreeformEnabled"))
    }

    @Test
    fun musicWidget_roundTrip_dclFullscreen() {
        val original = FloatingDashboardWidgetConfig(
            dataKey = MUSIC_COVER_WIDGET_DATA_KEY,
            mediaPlayers = listOf("ru.yandex.music"),
            launcherLaunchMode = AppLauncherLaunchMode.DCL_FULLSCREEN,
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(listOf(original))).single()
        assertEquals(AppLauncherLaunchMode.DCL_FULLSCREEN, parsed.launcherLaunchMode)

        val obj = JSONArray(serializeWidgetConfigs(listOf(original))).getJSONObject(0)
        assertEquals("dcl_fullscreen", obj.getString("launcherLaunchMode"))
    }

    @Test
    fun musicWidget_roundTrip_dclFullWidth() {
        val original = FloatingDashboardWidgetConfig(
            dataKey = MUSIC_COVER_WIDGET_DATA_KEY,
            mediaPlayers = listOf("ru.yandex.music"),
            launcherLaunchMode = AppLauncherLaunchMode.DCL_FULL_WIDTH,
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(listOf(original))).single()
        assertEquals(AppLauncherLaunchMode.DCL_FULL_WIDTH, parsed.launcherLaunchMode)

        val obj = JSONArray(serializeWidgetConfigs(listOf(original))).getJSONObject(0)
        assertEquals("dcl_full_width", obj.getString("launcherLaunchMode"))
    }

    @Test
    fun musicWidget_roundTrip_freeformFields() {
        val original = FloatingDashboardWidgetConfig(
            dataKey = MUSIC_SQUARE_WIDGET_DATA_KEY,
            mediaPlayers = listOf("com.spotify.music"),
            launcherLaunchMode = AppLauncherLaunchMode.FREEFORM,
            launcherFreeformEnabled = true,
            launcherFreeformSide = FreeformLaunchSide.RIGHT,
            launcherFreeformPercent = 40,
            launcherFreeformOverlayPage = 2,
            launcherFreeformOverlayCrop = true,
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(listOf(original))).single()
        assertEquals(AppLauncherLaunchMode.FREEFORM, parsed.launcherLaunchMode)
        assertTrue(parsed.launcherFreeformEnabled)
        assertEquals(FreeformLaunchSide.RIGHT, parsed.launcherFreeformSide)
        assertEquals(40, parsed.launcherFreeformPercent)
        assertEquals(2, parsed.launcherFreeformOverlayPage)
        assertTrue(parsed.launcherFreeformOverlayCrop)
    }

    @Test
    fun musicButtonsWidget_roundTrip_virtualDisplay() {
        val original = FloatingDashboardWidgetConfig(
            dataKey = MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
            mediaPlayers = listOf("ru.yandex.music"),
            launcherLaunchMode = AppLauncherLaunchMode.VIRTUAL_DISPLAY,
            launcherVirtualDisplayId = 7,
            launcherVirtualDisplayWidthPx = 1280,
            launcherVirtualDisplayHeightPx = 720,
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(listOf(original))).single()
        assertEquals(AppLauncherLaunchMode.VIRTUAL_DISPLAY, parsed.launcherLaunchMode)
        assertEquals(7, parsed.launcherVirtualDisplayId)
        assertEquals(1280, parsed.launcherVirtualDisplayWidthPx)
        assertEquals(720, parsed.launcherVirtualDisplayHeightPx)
    }

    @Test
    fun musicWidget_legacyMissingLaunchMode_defaultsFullscreen() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("dataKey", MUSIC_WIDGET_DATA_KEY)
                    .put("mediaPlayers", JSONArray().put("ru.yandex.music")),
            )
            .toString()
        val cfg = parseWidgetConfigsFromString(json).single()
        assertEquals(AppLauncherLaunchMode.FULLSCREEN, cfg.launcherLaunchMode)
        assertFalse(cfg.launcherFreeformEnabled)
        assertNull(cfg.launcherVirtualDisplayId)
    }

    @Test
    fun dclRouting_musicOrdinary_mapsToAppAreaWhenEnvOn() {
        assertEquals(
            DclLauncherApiContract.MODE_APP_AREA,
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = true,
                launchMode = AppLauncherLaunchMode.FULLSCREEN,
            ),
        )
        assertEquals(
            DclLauncherApiContract.MODE_FULL_WIDTH,
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = true,
                launchMode = AppLauncherLaunchMode.DCL_FULL_WIDTH,
            ),
        )
        assertEquals(
            DclLauncherApiContract.MODE_FULLSCREEN,
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = true,
                launchMode = AppLauncherLaunchMode.DCL_FULLSCREEN,
            ),
        )
        assertNull(
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = false,
                launchMode = AppLauncherLaunchMode.FULLSCREEN,
            ),
        )
    }

    @Test
    fun bulkRemap_includesMusicWidgetFreeform() {
        val music = FloatingDashboardWidgetConfig(
            dataKey = MUSIC_WIDGET_DATA_KEY,
            mediaPlayers = listOf("ru.yandex.music"),
            launcherLaunchMode = AppLauncherLaunchMode.FREEFORM,
            launcherFreeformEnabled = true,
            launcherFreeformSide = FreeformLaunchSide.LEFT,
            launcherFreeformPercent = FreeformLaunchBounds.DEFAULT_PERCENT,
        )
        val (next, count) = DclLauncherEnvironmentRouting.remapWidgetListToOrdinary(listOf(music))
        assertEquals(1, count)
        assertEquals(AppLauncherLaunchMode.FULLSCREEN, next.single().launcherLaunchMode)
        assertFalse(next.single().launcherFreeformEnabled)
    }
}
