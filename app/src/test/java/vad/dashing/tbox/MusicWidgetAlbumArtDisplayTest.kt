package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject

class MusicWidgetAlbumArtDisplayTest {

    @Test
    fun floatingDashboardWidgetConfig_albumArtDefaults() {
        val cfg = FloatingDashboardWidgetConfig(dataKey = MUSIC_WIDGET_DATA_KEY)
        assertFalse(cfg.mediaShowAlbumArt)
        assertEquals(
            MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT,
            cfg.mediaAlbumArtColumnWidthPercent,
        )
        assertEquals(
            MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_SIDE,
            cfg.mediaAlbumArtSide,
        )
        assertTrue(cfg.mediaShowPlayerHeaderIcon)
    }

    @Test
    fun normalizeAlbumArtColumnWidthPercent_clampsToRange() {
        assertEquals(20, MusicWidgetAlbumArtDisplay.normalizeAlbumArtColumnWidthPercent(5))
        assertEquals(30, MusicWidgetAlbumArtDisplay.normalizeAlbumArtColumnWidthPercent(30))
        assertEquals(80, MusicWidgetAlbumArtDisplay.normalizeAlbumArtColumnWidthPercent(95))
    }

    @Test
    fun normalizeAlbumArtSide_mapsUnknownToLeft() {
        assertEquals(
            MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_LEFT,
            MusicWidgetAlbumArtDisplay.normalizeAlbumArtSide(-1),
        )
        assertEquals(
            MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT,
            MusicWidgetAlbumArtDisplay.normalizeAlbumArtSide(
                MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT
            ),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecAlbumArtTest {

    @Test
    fun roundTrip_albumArtFields_fullMusicWidget() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = MUSIC_WIDGET_DATA_KEY,
                mediaPlayers = listOf("ru.yandex.music"),
                mediaShowAlbumArt = true,
                mediaAlbumArtColumnWidthPercent = 40,
                mediaAlbumArtSide = MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT,
                mediaShowPlayerHeaderIcon = false,
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals(1, parsed.size)
        val cfg = parsed[0]
        assertTrue(cfg.mediaShowAlbumArt)
        assertEquals(40, cfg.mediaAlbumArtColumnWidthPercent)
        assertEquals(MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT, cfg.mediaAlbumArtSide)
        assertFalse(cfg.mediaShowPlayerHeaderIcon)
    }

    @Test
    fun encode_omitsAlbumArtDefaults() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = MUSIC_WIDGET_DATA_KEY,
                    mediaPlayers = listOf("ru.yandex.music"),
                    mediaShowAlbumArt = false,
                    mediaAlbumArtColumnWidthPercent =
                        MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT,
                    mediaAlbumArtSide = MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_SIDE,
                    mediaShowPlayerHeaderIcon = true,
                ),
            ),
        )
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("mediaShowAlbumArt"))
        assertFalse(obj.has("mediaAlbumArtColumnWidthPercent"))
        assertFalse(obj.has("mediaAlbumArtSide"))
        assertFalse(obj.has("mediaShowPlayerHeaderIcon"))
    }

    @Test
    fun encode_omitsAlbumArtForButtonsOnlyWidget() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
                    mediaPlayers = listOf("ru.yandex.music"),
                    mediaShowAlbumArt = true,
                    mediaAlbumArtColumnWidthPercent = 50,
                    mediaAlbumArtSide = MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT,
                    mediaShowPlayerHeaderIcon = false,
                ),
            ),
        )
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("mediaShowAlbumArt"))
        assertFalse(obj.has("mediaAlbumArtColumnWidthPercent"))
        assertFalse(obj.has("mediaAlbumArtSide"))
        assertFalse(obj.has("mediaShowPlayerHeaderIcon"))
    }

    @Test
    fun decode_clampsAlbumArtColumnWidth() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("dataKey", MUSIC_WIDGET_DATA_KEY)
                    .put("mediaShowAlbumArt", true)
                    .put("mediaAlbumArtColumnWidthPercent", 99),
            )
            .toString()
        val cfg = parseWidgetConfigsFromString(json).single()
        assertTrue(cfg.mediaShowAlbumArt)
        assertEquals(
            MusicWidgetAlbumArtDisplay.MAX_ALBUM_ART_COLUMN_WIDTH_PERCENT,
            cfg.mediaAlbumArtColumnWidthPercent,
        )
    }

    @Test
    fun decode_ignoresAlbumArtOnNonFullMusicWidget() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("dataKey", MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY)
                    .put("mediaShowAlbumArt", true)
                    .put("mediaAlbumArtColumnWidthPercent", 50)
                    .put("mediaAlbumArtSide", MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT)
                    .put("mediaShowPlayerHeaderIcon", false),
            )
            .toString()
        val cfg = parseWidgetConfigsFromString(json).single()
        assertFalse(cfg.mediaShowAlbumArt)
        assertEquals(
            MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT,
            cfg.mediaAlbumArtColumnWidthPercent,
        )
        assertEquals(
            MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_SIDE,
            cfg.mediaAlbumArtSide,
        )
        assertTrue(cfg.mediaShowPlayerHeaderIcon)
    }
}
