package vad.dashing.tbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeAppIconsTest {

    @Test
    fun resolveStoredIconFile_prefersPackageNameWithoutExtension() {
        val dir = createTempDir().apply {
            File(this, "com.example.app").writeBytes(byteArrayOf(1, 2, 3))
            File(this, "com.example.app.png").writeBytes(byteArrayOf(9))
        }
        val resolved = LauncherAppIconPaths.resolveStoredIconFile(dir, "com.example.app")
        assertNotNull(resolved)
        assertEquals("com.example.app", resolved!!.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), resolved.readBytes())
        dir.deleteRecursively()
    }

    @Test
    fun listStoredPackageNames_includesExtensionlessFiles() {
        val dir = createTempDir().apply {
            File(this, "com.foo.bar").writeBytes(byteArrayOf(1))
            File(this, "readme.txt").writeBytes(byteArrayOf(2))
        }
        assertEquals(setOf("com.foo.bar"), LauncherAppIconPaths.listStoredPackageNames(dir))
        dir.deleteRecursively()
    }

    @Test
    fun resolveIconFile_prefersThemeCacheOverSharedOverride() {
        val root = createTempDir()
        val shared = LauncherAppIconPaths.sharedIconsDir(root).apply { mkdirs() }
        val themeIcons = LauncherAppIconPaths.themeIconsDir(root, "my_theme").apply { mkdirs() }
        File(shared, "com.example.app").writeBytes(byteArrayOf(1))
        File(themeIcons, "com.example.app").writeBytes(byteArrayOf(2))
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "my_theme",
            activeThemeSections = setOf(ThemeSection.APP_ICONS),
        )
        val resolved = LauncherAppIconPaths.resolveIconFile(root, "com.example.app", lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(2), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun resolveIconFile_usesThemeCacheWhenNoSharedOverride() {
        val root = createTempDir()
        val themeIcons = LauncherAppIconPaths.themeIconsDir(root, "my_theme").apply { mkdirs() }
        File(themeIcons, "ru.yandex.music").writeBytes(byteArrayOf(5, 6))
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "my_theme",
            activeThemeSections = setOf(ThemeSection.APP_ICONS),
        )
        val resolved = LauncherAppIconPaths.resolveIconFile(root, "ru.yandex.music", lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(5, 6), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun resolveIconFile_usesSharedOverrideWhenThemeCacheMissing() {
        val root = createTempDir()
        val shared = LauncherAppIconPaths.sharedIconsDir(root).apply { mkdirs() }
        File(shared, "com.example.app").writeBytes(byteArrayOf(9))
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "my_theme",
            activeThemeSections = setOf(ThemeSection.APP_ICONS),
        )
        val resolved = LauncherAppIconPaths.resolveIconFile(root, "com.example.app", lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(9), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun resolveIconFile_skipsThemeCacheWhenSectionNotIncluded() {
        val root = createTempDir()
        val themeIcons = LauncherAppIconPaths.themeIconsDir(root, "my_theme").apply { mkdirs() }
        File(themeIcons, "com.example.app").writeBytes(byteArrayOf(2))
        val shared = LauncherAppIconPaths.sharedIconsDir(root).apply { mkdirs() }
        File(shared, "com.example.app").writeBytes(byteArrayOf(1))
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "my_theme",
            activeThemeSections = setOf(ThemeSection.MAIN_SCREEN),
        )
        val resolved = LauncherAppIconPaths.resolveIconFile(root, "com.example.app", lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(1), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun clearCustomLauncherAppIcon_deletesThemeCacheBeforeShared() {
        val root = createTempDir()
        val shared = LauncherAppIconPaths.sharedIconsDir(root).apply { mkdirs() }
        val themeIcons = LauncherAppIconPaths.themeIconsDir(root, "my_theme").apply { mkdirs() }
        File(shared, "com.example.app").writeBytes(byteArrayOf(1))
        File(themeIcons, "com.example.app").writeBytes(byteArrayOf(2))
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "my_theme",
            activeThemeSections = setOf(ThemeSection.APP_ICONS),
        )
        assertTrue(LauncherAppIconPaths.deleteThemeCacheIcon(root, "com.example.app", lookup))
        assertArrayEquals(byteArrayOf(1), LauncherAppIconPaths.resolveIconFile(root, "com.example.app", lookup)!!.readBytes())
        assertTrue(LauncherAppIconPaths.deleteSharedIcon(root, "com.example.app"))
        assertNull(LauncherAppIconPaths.resolveIconFile(root, "com.example.app", lookup))
        root.deleteRecursively()
    }

    @Test
    fun hasSharedOverride_onlyChecksSharedFolder() {
        val root = createTempDir()
        val themeIcons = LauncherAppIconPaths.themeIconsDir(root, "my_theme").apply { mkdirs() }
        File(themeIcons, "com.example.app").writeBytes(byteArrayOf(2))
        assertTrue(!LauncherAppIconPaths.hasSharedOverride(root, "com.example.app"))
        val shared = LauncherAppIconPaths.sharedIconsDir(root).apply { mkdirs() }
        File(shared, "com.example.app").writeBytes(byteArrayOf(1))
        assertTrue(LauncherAppIconPaths.hasSharedOverride(root, "com.example.app"))
        root.deleteRecursively()
    }
}
