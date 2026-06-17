package vad.dashing.tbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TileBackgroundImageStorageTest {

    @Test
    fun resolveFile_prefersSharedOverrideOverThemeCache() {
        val root = createTempDir()
        val rel = TileBackgroundImageStorage.relativePathFor("panel_a", 0, darkTheme = false)
        val sharedFile = File(root, rel.replace('/', File.separatorChar))
        sharedFile.parentFile?.mkdirs()
        sharedFile.writeBytes(byteArrayOf(1))
        val themeDir = TileBackgroundImageStorage.themeCacheDir(root, "my_theme")
        File(themeDir, "panel_a/0_light").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(2))
        }
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "my_theme",
            activeThemeSections = setOf(ThemeSection.MAIN_SCREEN),
        )
        val resolved = TileBackgroundImageStorage.resolveFile(root, rel, lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(1), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun resolveFile_usesThemeCacheWhenNoSharedOverride() {
        val root = createTempDir()
        val rel = TileBackgroundImageStorage.relativePathFor("panel_b", 1, darkTheme = true)
        val themeDir = TileBackgroundImageStorage.themeCacheDir(root, "theme_b")
        File(themeDir, "panel_b/1_dark").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(7, 8))
        }
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "theme_b",
            activeThemeSections = setOf(ThemeSection.FLOATING_PANELS),
        )
        val resolved = TileBackgroundImageStorage.resolveFile(root, rel, lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(7, 8), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun resolveFile_skipsThemeCacheWhenSectionsExcludePanels() {
        val root = createTempDir()
        val rel = TileBackgroundImageStorage.relativePathFor("panel_c", 0, darkTheme = false)
        val themeDir = TileBackgroundImageStorage.themeCacheDir(root, "theme_c")
        File(themeDir, "panel_c/0_light").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(3))
        }
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "theme_c",
            activeThemeSections = setOf(ThemeSection.APP_ICONS),
        )
        assertNull(TileBackgroundImageStorage.resolveFile(root, rel, lookup))
        root.deleteRecursively()
    }
}
