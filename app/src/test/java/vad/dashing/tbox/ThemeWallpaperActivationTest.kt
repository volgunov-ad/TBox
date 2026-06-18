package vad.dashing.tbox

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
}
