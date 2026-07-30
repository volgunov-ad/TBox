package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeLayoutExportIconPackagesTest {

    @Test
    fun collectMediaPlayerPackages_includesMusicWidgetPlayers() {
        val widgets = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = MUSIC_WIDGET_DATA_KEY,
                mediaPlayers = listOf("ru.yandex.music", "com.spotify.music"),
                mediaSelectedPlayer = "ru.yandex.music",
            ),
            FloatingDashboardWidgetConfig(
                dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
                launcherAppPackage = "com.example.launcher",
            ),
        )

        assertEquals(
            setOf("ru.yandex.music", "com.spotify.music"),
            ThemeLayoutExport.collectMediaPlayerPackages(widgets),
        )
        assertEquals(
            setOf("com.example.launcher"),
            ThemeLayoutExport.collectLauncherPackages(widgets),
        )
    }

    @Test
    fun collectMediaPlayerPackages_includesMusicButtonsVariants() {
        val widgets = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
                mediaPlayers = listOf("com.apple.android.music"),
            ),
            FloatingDashboardWidgetConfig(
                dataKey = MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY,
                mediaPlayers = listOf("com.google.android.apps.youtube.music"),
                mediaSelectedPlayer = "com.google.android.apps.youtube.music",
            ),
        )

        assertEquals(
            setOf("com.apple.android.music", "com.google.android.apps.youtube.music"),
            ThemeLayoutExport.collectMediaPlayerPackages(widgets),
        )
    }

    @Test
    fun collectMediaPlayerPackages_includesSelectedPlayerEvenIfMissingFromList() {
        val widgets = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = MUSIC_WIDGET_DATA_KEY,
                mediaPlayers = emptyList(),
                mediaSelectedPlayer = "ru.yandex.music",
            ),
        )

        assertEquals(
            setOf("ru.yandex.music"),
            ThemeLayoutExport.collectMediaPlayerPackages(widgets),
        )
    }

    @Test
    fun collectMediaPlayerPackages_ignoresNonMusicWidgets() {
        val widgets = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
                mediaPlayers = listOf("ru.yandex.music"),
                mediaSelectedPlayer = "ru.yandex.music",
            ),
        )

        assertTrue(ThemeLayoutExport.collectMediaPlayerPackages(widgets).isEmpty())
    }
}
