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
        val selections = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(page = 1, forLightTheme = true, fileName = "light.jpg")
        ThemeRuntimeState.patch(dir, wallpaperSelections = selections)
        ThemeRuntimeState.patch(dir, currentPage = 3)

        val file = File(dir, ThemeRuntimeState.RUNTIME_JSON_FILE)
        assertTrue(file.isFile)
        val json = JSONObject(file.readText())
        val byPage = json.getJSONObject(ThemeRuntimeState.KEY_WALLPAPER_SELECTION_BY_PAGE)
        assertEquals("light.jpg", byPage.getJSONObject("light").getString("1"))
        assertEquals(3, json.getInt(ThemeRuntimeState.KEY_CURRENT_PAGE))
        assertFalse(byPage.getJSONObject("light").has("2"))
    }

    @Test
    fun read_preservesFieldPresenceFlags() {
        val dir = createTempDir(prefix = "runtime_read_")
        val selections = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(page = 2, forLightTheme = false, fileName = "night.png")
        ThemeRuntimeState.patch(dir, wallpaperSelections = selections)

        val state = ThemeRuntimeState.read(dir)
        assertTrue(state.hasWallpaperSelections)
        assertEquals("night.png", state.wallpaperSelections?.fileNameFor(2, forLightTheme = false))
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

    @Test
    fun resolveWallpaperSelectionsForActivation_prefersRuntimeOverThemeJson() {
        val dir = createTempDir(prefix = "runtime_resolve_")
        ThemeRuntimeState.patch(
            dir,
            wallpaperSelections = MainScreenWallpaperSelectionsByPage.empty()
                .withFileName(page = 1, forLightTheme = true, fileName = "eco.jpg"),
        )
        val themeJson = """
            {
              "mainScreen": {
                "wallpaperSelectionByPage": {
                  "light": { "1": "nor.jpg" }
                }
              }
            }
        """.trimIndent()

        val resolved = ThemeRuntimeState.resolveWallpaperSelectionsForActivation(dir, themeJson)

        assertEquals("eco.jpg", resolved.fileNameFor(1, forLightTheme = true))
    }

    @Test
    fun resolveWallpaperSelectionsForActivation_returnsEmptyWhenNeitherSourceDefinesWallpaper() {
        val dir = createTempDir(prefix = "runtime_resolve_empty_")
        ThemeRuntimeState.patch(dir, currentPage = 2)
        val themeJson = """{"mainScreen":{"currentPage":2}}"""

        val resolved = ThemeRuntimeState.resolveWallpaperSelectionsForActivation(dir, themeJson)

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun seedFromThemeJsonIfMissing_writesRuntimeJsonFromMainScreenSection() {
        val dir = createTempDir(prefix = "runtime_seed_")
        val themeJson = """
            {
              "mainScreen": {
                "currentPage": 3,
                "wallpaperSelectionByPage": {
                  "light": { "1": "eco.jpg" },
                  "dark": { "1": "eco_night.jpg" }
                }
              }
            }
        """.trimIndent()

        ThemeRuntimeState.seedFromThemeJsonIfMissing(dir, themeJson)

        val state = ThemeRuntimeState.read(dir)
        assertEquals(3, state.currentPage)
        assertTrue(state.hasCurrentPage)
        assertTrue(state.hasWallpaperSelections)
        assertEquals("eco.jpg", state.wallpaperSelections?.fileNameFor(1, forLightTheme = true))
        assertEquals("eco_night.jpg", state.wallpaperSelections?.fileNameFor(1, forLightTheme = false))
    }

    @Test
    fun seedFromThemeJsonIfMissing_doesNotOverwriteExistingRuntimeJson() {
        val dir = createTempDir(prefix = "runtime_seed_keep_")
        ThemeRuntimeState.patch(
            dir,
            wallpaperSelections = MainScreenWallpaperSelectionsByPage.empty()
                .withFileName(page = 1, forLightTheme = true, fileName = "nor.jpg"),
        )
        val themeJson = """
            {
              "mainScreen": {
                "wallpaperSelectionByPage": {
                  "light": { "1": "spt.jpg" }
                }
              }
            }
        """.trimIndent()

        ThemeRuntimeState.seedFromThemeJsonIfMissing(dir, themeJson)

        assertEquals(
            "nor.jpg",
            ThemeRuntimeState.read(dir).wallpaperSelections?.fileNameFor(1, forLightTheme = true),
        )
    }

    @Test
    fun seedFromThemeJsonIfMissing_skipsWhenMainScreenHasNoRuntimeFields() {
        val dir = createTempDir(prefix = "runtime_seed_skip_")
        val themeJson = """{"mainScreen":{"pageCount":2}}"""

        ThemeRuntimeState.seedFromThemeJsonIfMissing(dir, themeJson)

        assertFalse(File(dir, ThemeRuntimeState.RUNTIME_JSON_FILE).exists())
    }
}
