package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigCodecShapeTest {

    @Test
    fun normalizeWidgetShape_clampsToAllowedRange() {
        assertEquals(0, normalizeWidgetShape(-5))
        assertEquals(15, normalizeWidgetShape(15))
        assertEquals(30, normalizeWidgetShape(99))
    }

    @Test
    fun parseWidgetConfigs_whenNormalizedPlayersEmptyButSelectedKnown_keepsSinglePlayer() {
        val json =
            """[{"dataKey":"$MUSIC_WIDGET_DATA_KEY","mediaPlayers":["com.unknown.player"],"mediaSelectedPlayer":"com.maxmpz.audioplayer"}]"""
        val configs = parseWidgetConfigsFromString(json)
        assertEquals(1, configs.size)
        val c = configs[0]
        assertEquals(listOf("com.maxmpz.audioplayer"), c.mediaPlayers)
        assertEquals("com.maxmpz.audioplayer", c.mediaSelectedPlayer)
    }

    @Test
    fun parseWidgetConfigs_whenSelectedNotInPlayerList_clearsSelected() {
        val json =
            """[{"dataKey":"$MUSIC_WIDGET_DATA_KEY","mediaPlayers":["ru.yandex.music"],"mediaSelectedPlayer":"com.maxmpz.audioplayer"}]"""
        val configs = parseWidgetConfigsFromString(json)
        assertEquals(1, configs.size)
        val c = configs[0]
        assertEquals(listOf("ru.yandex.music"), c.mediaPlayers)
        assertEquals("", c.mediaSelectedPlayer)
    }
}
