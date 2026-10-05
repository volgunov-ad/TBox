package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ThemeWallpaperActivationTest {

    @Test
    fun wallpaperFolderUriFromCacheDir_nullWhenMissingOrEmpty() {
        val emptyDir = createTempDir(prefix = "theme_wp_empty_")
        assertNull(ThemeMaterialization.wallpaperFolderUriFromCacheDir(emptyDir))

        val missing = File(emptyDir, "wallpaper/light")
        assertNull(ThemeMaterialization.wallpaperFolderUriFromCacheDir(missing))
    }

    @Test
    fun wallpaperFolderUriFromCacheDir_nullWhenOnlySubdirectories() {
        val dir = createTempDir(prefix = "theme_wp_dirs_")
        File(dir, "nested").mkdirs()
        assertNull(ThemeMaterialization.wallpaperFolderUriFromCacheDir(dir))
    }

    @Test
    fun uniqueWallpaperFileName_avoidsCollisions() {
        val dir = createTempDir(prefix = "theme_wp_unique_")
        File(dir, "wall.jpg").writeBytes(byteArrayOf(1))
        assertEquals("wall_2.jpg", ThemeMaterialization.uniqueWallpaperFileName(dir, "wall.jpg"))
        File(dir, "wall_2.jpg").writeBytes(byteArrayOf(2))
        assertEquals("wall_3.jpg", ThemeMaterialization.uniqueWallpaperFileName(dir, "wall.jpg"))
        assertEquals("fresh.png", ThemeMaterialization.uniqueWallpaperFileName(dir, "fresh.png"))
        assertEquals("evil_name__.JPG", ThemeMaterialization.uniqueWallpaperFileName(dir, "../evil name!!.JPG"))
    }
}
