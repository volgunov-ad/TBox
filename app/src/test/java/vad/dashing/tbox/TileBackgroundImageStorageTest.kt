package vad.dashing.tbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TileBackgroundImageStorageTest {

    @Test
    fun resolveFile_prefersThemeCacheOverSharedOverride() {
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
            activeThemeApplyTargets = setOf(ThemeApplyTarget.TILE_BACKGROUNDS),
        )
        val resolved = TileBackgroundImageStorage.resolveFile(root, rel, lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(2), resolved!!.readBytes())
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
            activeThemeApplyTargets = setOf(ThemeApplyTarget.TILE_BACKGROUNDS, ThemeApplyTarget.FLOATING_PANELS),
        )
        val resolved = TileBackgroundImageStorage.resolveFile(root, rel, lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(7, 8), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun resolveFile_usesSharedOverrideWhenThemeCacheMissing() {
        val root = createTempDir()
        val rel = TileBackgroundImageStorage.relativePathFor("panel_d", 0, darkTheme = false)
        val sharedFile = File(root, rel.replace('/', File.separatorChar))
        sharedFile.parentFile?.mkdirs()
        sharedFile.writeBytes(byteArrayOf(4))
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "theme_d",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.TILE_BACKGROUNDS),
        )
        val resolved = TileBackgroundImageStorage.resolveFile(root, rel, lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(4), resolved!!.readBytes())
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
        val sharedFile = File(root, rel.replace('/', File.separatorChar))
        sharedFile.parentFile?.mkdirs()
        sharedFile.writeBytes(byteArrayOf(5))
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "theme_c",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.APP_ICONS),
        )
        val resolved = TileBackgroundImageStorage.resolveFile(root, rel, lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(5), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun deleteTileBackground_deletesThemeCacheBeforeShared() {
        val root = createTempDir()
        val rel = TileBackgroundImageStorage.relativePathFor("panel_e", 0, darkTheme = false)
        val sharedFile = File(root, rel.replace('/', File.separatorChar))
        sharedFile.parentFile?.mkdirs()
        sharedFile.writeBytes(byteArrayOf(1))
        val themeDir = TileBackgroundImageStorage.themeCacheDir(root, "theme_e")
        File(themeDir, "panel_e/0_light").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(2))
        }
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "theme_e",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.TILE_BACKGROUNDS),
        )
        assertTrue(TileBackgroundImageStorage.hasThemeCacheFile(root, rel, lookup))
        assertTrue(TileBackgroundImageStorage.deleteThemeCacheFile(root, rel, "theme_e"))
        assertArrayEquals(byteArrayOf(1), TileBackgroundImageStorage.resolveFile(root, rel, lookup)!!.readBytes())
        assertTrue(TileBackgroundImageStorage.deleteSharedFile(root, rel))
        assertNull(TileBackgroundImageStorage.resolveFile(root, rel, lookup))
        root.deleteRecursively()
    }

    @Test
    fun destinationFile_writesIntoThemeCacheWhenTileBackgroundsActive() {
        val root = createTempDir()
        val rel = TileBackgroundImageStorage.relativePathFor("panel_w", 0, darkTheme = false)
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "theme_write",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.TILE_BACKGROUNDS),
        )
        val dest = TileBackgroundImageStorage.destinationFile(root, rel, lookup)
        assertNotNull(dest)
        assertTrue(dest!!.absolutePath.contains("themes${File.separator}theme_write${File.separator}tile_backgrounds"))
        dest.parentFile?.mkdirs()
        dest.writeBytes(byteArrayOf(9))
        assertArrayEquals(byteArrayOf(9), TileBackgroundImageStorage.resolveFile(root, rel, lookup)!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun destinationFile_writesIntoSharedWhenThemeHasNoTileBackgrounds() {
        val root = createTempDir()
        val rel = TileBackgroundImageStorage.relativePathFor("panel_s", 0, darkTheme = false)
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "theme_shared",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.APP_ICONS),
        )
        val dest = TileBackgroundImageStorage.destinationFile(root, rel, lookup)
        assertNotNull(dest)
        assertEquals(File(root, rel.replace('/', File.separatorChar)).absolutePath, dest!!.absolutePath)
        root.deleteRecursively()
    }
}
