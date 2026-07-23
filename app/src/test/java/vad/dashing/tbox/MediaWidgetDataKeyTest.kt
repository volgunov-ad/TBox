package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaWidgetDataKeyTest {

    @Test
    fun isMusicWidgetDataKey_recognizesFullAndButtonsOnlyVariants() {
        assertTrue(isMusicWidgetDataKey(MUSIC_WIDGET_DATA_KEY))
        assertTrue(isMusicWidgetDataKey(MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY))
        assertTrue(isMusicWidgetDataKey(MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY))
        assertFalse(isMusicWidgetDataKey(MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY))
        assertFalse(isMusicWidgetDataKey(""))
    }

    @Test
    fun resolveMediaPlayersForWidget_acceptsButtonsOnlyKeys() {
        val packages = listOf("com.example.player")
        val full = FloatingDashboardWidgetConfig(
            dataKey = MUSIC_WIDGET_DATA_KEY,
            mediaPlayers = packages
        )
        val horizontal = FloatingDashboardWidgetConfig(
            dataKey = MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
            mediaPlayers = packages
        )
        val vertical = FloatingDashboardWidgetConfig(
            dataKey = MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY,
            mediaPlayers = packages
        )
        val other = FloatingDashboardWidgetConfig(
            dataKey = MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY,
            mediaPlayers = packages
        )

        assertEquals(setOf("com.example.player"), resolveMediaPlayersForWidget(full))
        assertEquals(setOf("com.example.player"), resolveMediaPlayersForWidget(horizontal))
        assertEquals(setOf("com.example.player"), resolveMediaPlayersForWidget(vertical))
        assertTrue(resolveMediaPlayersForWidget(other).isEmpty())
    }

    @Test
    fun collectMediaPlayersFromWidgetConfigs_includesButtonsOnlyTiles() {
        val configs = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
                mediaPlayers = listOf("com.example.a")
            ),
            FloatingDashboardWidgetConfig(
                dataKey = MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY,
                mediaPlayers = listOf("com.example.b")
            ),
            FloatingDashboardWidgetConfig(
                dataKey = MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY,
                mediaPlayers = listOf("com.example.ignored")
            )
        )
        assertEquals(setOf("com.example.a", "com.example.b"), collectMediaPlayersFromWidgetConfigs(configs))
    }
}
