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
        assertFalse(cfg.mediaFollowPlayback)
        assertFalse(cfg.mediaShowLikeButton)
        assertEquals(
            MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT,
            cfg.mediaAlbumArtColumnWidthPercent,
        )
        assertEquals(
            MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_SIDE,
            cfg.mediaAlbumArtSide,
        )
        assertTrue(cfg.mediaShowPlayerHeaderIcon)
        assertTrue(cfg.mediaShowTrackInfo)
        assertEquals(null, cfg.mediaControlsHeightPercent)
    }

    @Test
    fun resolveControlsHeightPercent_usesTypeDefaults() {
        assertEquals(
            MusicWidgetControlsDisplay.DEFAULT_STANDARD_CONTROLS_HEIGHT_PERCENT,
            MusicWidgetControlsDisplay.resolveControlsHeightPercent(MUSIC_WIDGET_DATA_KEY, null),
        )
        assertEquals(
            MusicWidgetControlsDisplay.DEFAULT_COVER_CONTROLS_HEIGHT_PERCENT,
            MusicWidgetControlsDisplay.resolveControlsHeightPercent(
                MUSIC_COVER_WIDGET_DATA_KEY,
                null,
            ),
        )
    }

    @Test
    fun normalizeControlsHeightPercent_clampsToRange() {
        assertEquals(5, MusicWidgetControlsDisplay.normalizeControlsHeightPercent(1))
        assertEquals(25, MusicWidgetControlsDisplay.normalizeControlsHeightPercent(25))
        assertEquals(50, MusicWidgetControlsDisplay.normalizeControlsHeightPercent(99))
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
                mediaFollowPlayback = true,
                mediaShowLikeButton = true,
                mediaShowAlbumArt = true,
                mediaAlbumArtColumnWidthPercent = 40,
                mediaAlbumArtSide = MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT,
                mediaShowPlayerHeaderIcon = false,
                mediaControlsHeightPercent = 40,
            ),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals(1, parsed.size)
        val cfg = parsed[0]
        assertTrue(cfg.mediaFollowPlayback)
        assertTrue(cfg.mediaShowLikeButton)
        assertTrue(cfg.mediaShowAlbumArt)
        assertEquals(40, cfg.mediaAlbumArtColumnWidthPercent)
        assertEquals(MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT, cfg.mediaAlbumArtSide)
        assertFalse(cfg.mediaShowPlayerHeaderIcon)
        assertEquals(40, cfg.mediaControlsHeightPercent)
    }

    @Test
    fun encode_omitsAlbumArtDefaults() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = MUSIC_WIDGET_DATA_KEY,
                    mediaPlayers = listOf("ru.yandex.music"),
                    mediaFollowPlayback = false,
                    mediaShowLikeButton = false,
                    mediaShowAlbumArt = false,
                    mediaAlbumArtColumnWidthPercent =
                        MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT,
                    mediaAlbumArtSide = MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_SIDE,
                    mediaShowPlayerHeaderIcon = true,
                    mediaControlsHeightPercent =
                        MusicWidgetControlsDisplay.DEFAULT_STANDARD_CONTROLS_HEIGHT_PERCENT,
                ),
            ),
        )
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("mediaFollowPlayback"))
        assertFalse(obj.has("mediaShowLikeButton"))
        assertFalse(obj.has("mediaShowAlbumArt"))
        assertFalse(obj.has("mediaAlbumArtColumnWidthPercent"))
        assertFalse(obj.has("mediaAlbumArtSide"))
        assertFalse(obj.has("mediaShowPlayerHeaderIcon"))
        assertFalse(obj.has("mediaControlsHeightPercent"))
    }

    @Test
    fun encode_omitsAlbumArtForButtonsOnlyWidget() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
                    mediaPlayers = listOf("ru.yandex.music"),
                    mediaFollowPlayback = true,
                    mediaShowAlbumArt = true,
                    mediaAlbumArtColumnWidthPercent = 50,
                    mediaAlbumArtSide = MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT,
                    mediaShowPlayerHeaderIcon = false,
                    mediaShowTrackInfo = false,
                    mediaControlsHeightPercent = 40,
                ),
            ),
        )
        val obj = JSONArray(json).getJSONObject(0)
        assertTrue(obj.optBoolean("mediaFollowPlayback"))
        assertFalse(obj.has("mediaShowAlbumArt"))
        assertFalse(obj.has("mediaAlbumArtColumnWidthPercent"))
        assertFalse(obj.has("mediaAlbumArtSide"))
        assertFalse(obj.has("mediaShowPlayerHeaderIcon"))
        assertFalse(obj.has("mediaShowTrackInfo"))
        assertFalse(obj.has("mediaControlsHeightPercent"))
    }

    @Test
    fun roundTrip_coverWidget_keepsHeaderIconButIgnoresColumnSettings() {
        val original = FloatingDashboardWidgetConfig(
            dataKey = MUSIC_COVER_WIDGET_DATA_KEY,
            mediaPlayers = listOf("ru.yandex.music"),
            mediaFollowPlayback = true,
            mediaShowAlbumArt = true,
            mediaAlbumArtColumnWidthPercent = 50,
            mediaAlbumArtSide = MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT,
            mediaShowPlayerHeaderIcon = false,
            mediaShowTrackInfo = false,
            mediaControlsHeightPercent = 20,
        )

        val parsed = parseWidgetConfigsFromString(
            serializeWidgetConfigs(listOf(original))
        ).single()

        assertTrue(parsed.mediaFollowPlayback)
        assertFalse(parsed.mediaShowAlbumArt)
        assertEquals(
            MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT,
            parsed.mediaAlbumArtColumnWidthPercent,
        )
        assertEquals(
            MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_SIDE,
            parsed.mediaAlbumArtSide,
        )
        assertFalse(parsed.mediaShowPlayerHeaderIcon)
        assertFalse(parsed.mediaShowTrackInfo)
        assertEquals(20, parsed.mediaControlsHeightPercent)
    }

    @Test
    fun encode_omitsCoverTrackInfoDefault() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = MUSIC_COVER_WIDGET_DATA_KEY,
                    mediaPlayers = listOf("ru.yandex.music"),
                    mediaShowTrackInfo = true,
                ),
            ),
        )
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("mediaShowTrackInfo"))
    }

    @Test
    fun encode_omitsCoverControlsHeightDefault() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = MUSIC_COVER_WIDGET_DATA_KEY,
                    mediaPlayers = listOf("ru.yandex.music"),
                    mediaControlsHeightPercent =
                        MusicWidgetControlsDisplay.DEFAULT_COVER_CONTROLS_HEIGHT_PERCENT,
                ),
            ),
        )
        val obj = JSONArray(json).getJSONObject(0)
        assertFalse(obj.has("mediaControlsHeightPercent"))
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
    fun decode_clampsControlsHeightPercent() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("dataKey", MUSIC_WIDGET_DATA_KEY)
                    .put("mediaControlsHeightPercent", 99),
            )
            .toString()
        val cfg = parseWidgetConfigsFromString(json).single()
        assertEquals(
            MusicWidgetControlsDisplay.MAX_CONTROLS_HEIGHT_PERCENT,
            cfg.mediaControlsHeightPercent,
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
                    .put("mediaShowPlayerHeaderIcon", false)
                    .put("mediaControlsHeightPercent", 40),
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
        assertEquals(null, cfg.mediaControlsHeightPercent)
    }
}
