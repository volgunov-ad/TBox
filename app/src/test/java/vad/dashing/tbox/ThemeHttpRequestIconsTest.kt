package vad.dashing.tbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class ThemeHttpRequestIconsTest {

    @Test
    fun iconKey_usesPanelAndTileIndex() {
        assertEquals("panel_1-3", HttpRequestIconPaths.iconKey("panel 1", 3))
    }

    @Test
    fun resolveIconFile_prefersThemeCacheOverShared() {
        val root = createTempDir()
        val shared = HttpRequestIconPaths.sharedIconsDir(root).apply { mkdirs() }
        val themeIcons = HttpRequestIconPaths.themeIconsDir(root, "theme_a").apply { mkdirs() }
        val key = HttpRequestIconPaths.iconKey("panel", 0)
        File(shared, key).writeBytes(byteArrayOf(1))
        File(themeIcons, key).writeBytes(byteArrayOf(2))

        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "theme_a",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.APP_ICONS),
        )
        val resolved = HttpRequestIconPaths.resolveIconFile(root, key, lookup)

        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(2), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun collectHttpRequestIconKeys_onlyIncludesHttpWidgets() {
        val keys = ThemeLayoutExport.collectHttpRequestIconKeys(
            "panel",
            listOf(
                FloatingDashboardWidgetConfig(dataKey = APP_LAUNCHER_WIDGET_DATA_KEY),
                FloatingDashboardWidgetConfig(dataKey = HTTP_REQUEST_WIDGET_DATA_KEY),
            )
        )

        assertEquals(setOf(HttpRequestIconPaths.iconKey("panel", 1)), keys)
    }
}
