package vad.dashing.tbox

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeRuntimeStateTest {

    @Test
    fun read_returnsEmptyWhenFileMissing() {
        val dir = createTempDir(prefix = "runtime_missing_")
        val state = ThemeRuntimeState.read(dir)
        assertTrue(state.isEmpty)
    }

    @Test
    fun patch_mergesFieldsIntoRuntimeJson() {
        val dir = createTempDir(prefix = "runtime_patch_")
        ThemeRuntimeState.patch(dir, lightSelectedFile = "light.jpg")
        ThemeRuntimeState.patch(dir, currentPage = 3)

        val file = File(dir, ThemeRuntimeState.RUNTIME_JSON_FILE)
        assertTrue(file.isFile)
        val json = JSONObject(file.readText())
        assertEquals("light.jpg", json.getString(ThemeRuntimeState.KEY_WALLPAPER_LIGHT_SELECTED_FILE))
        assertEquals(3, json.getInt(ThemeRuntimeState.KEY_CURRENT_PAGE))
        assertFalse(json.has(ThemeRuntimeState.KEY_WALLPAPER_DARK_SELECTED_FILE))
    }

    @Test
    fun read_preservesFieldPresenceFlags() {
        val dir = createTempDir(prefix = "runtime_read_")
        ThemeRuntimeState.patch(dir, darkSelectedFile = "night.png")

        val state = ThemeRuntimeState.read(dir)
        assertFalse(state.hasWallpaperLightSelectedFile)
        assertTrue(state.hasWallpaperDarkSelectedFile)
        assertEquals("night.png", state.wallpaperDarkSelectedFile)
        assertFalse(state.hasCurrentPage)
    }

    @Test
    fun write_deletesFileWhenStateEmpty() {
        val dir = createTempDir(prefix = "runtime_clear_")
        ThemeRuntimeState.patch(dir, currentPage = 2)
        assertTrue(File(dir, ThemeRuntimeState.RUNTIME_JSON_FILE).isFile)

        ThemeRuntimeState.write(dir, ThemeRuntimeState.State())
        assertFalse(File(dir, ThemeRuntimeState.RUNTIME_JSON_FILE).exists())
    }
}
