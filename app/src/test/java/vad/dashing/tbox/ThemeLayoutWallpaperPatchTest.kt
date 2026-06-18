package vad.dashing.tbox

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeLayoutWallpaperPatchTest {

    @Test
    fun patchMainScreenWallpaperSelection_updatesOnlyRequestedSide() {
        val original = """
            {
              "sections": ["mainScreen"],
              "mainScreen": {
                "wallpaperLightSelectedFile": "a.jpg",
                "wallpaperDarkSelectedFile": "b.jpg"
              }
            }
        """.trimIndent()
        val patched = ThemeLayoutExport.patchMainScreenWallpaperSelection(
            themeJson = original,
            lightSelectedFile = "c.jpg",
        ).getOrThrow()
        val section = JSONObject(patched).getJSONObject("mainScreen")
        assertEquals("c.jpg", section.getString("wallpaperLightSelectedFile"))
        assertEquals("b.jpg", section.getString("wallpaperDarkSelectedFile"))
    }

    @Test
    fun patchMainScreenWallpaperSelection_noOpWithoutMainScreenSection() {
        val original = """{"sections":["appIcons"]}"""
        val patched = ThemeLayoutExport.patchMainScreenWallpaperSelection(
            themeJson = original,
            darkSelectedFile = "night.jpg",
        ).getOrThrow()
        assertEquals(original, patched)
    }

    @Test
    fun patchMainScreenWallpaperSelection_createsMainScreenObjectWhenMissing() {
        val original = """{"sections":["mainScreen"]}"""
        val patched = ThemeLayoutExport.patchMainScreenWallpaperSelection(
            themeJson = original,
            lightSelectedFile = "hero.jpg",
        ).getOrThrow()
        assertEquals("hero.jpg", JSONObject(patched).getJSONObject("mainScreen").getString("wallpaperLightSelectedFile"))
    }

    @Test
    fun patchMainScreenWallpaperSelection_returnsSameJsonWhenNoFields() {
        val original = """{"sections":["mainScreen"],"mainScreen":{}}"""
        val result = ThemeLayoutExport.patchMainScreenWallpaperSelection(themeJson = original)
        assertTrue(result.isSuccess)
        assertEquals(original, result.getOrNull())
    }

    @Test
    fun patchMainScreenCurrentPage_updatesCurrentPageField() {
        val original = """
            {
              "sections": ["mainScreen"],
              "mainScreen": {
                "pageCount": 3,
                "currentPage": 1
              }
            }
        """.trimIndent()
        val patched = ThemeLayoutExport.patchMainScreenCurrentPage(
            themeJson = original,
            currentPage = 2,
        ).getOrThrow()
        assertEquals(2, JSONObject(patched).getJSONObject("mainScreen").getInt("currentPage"))
        assertEquals(3, JSONObject(patched).getJSONObject("mainScreen").getInt("pageCount"))
    }

    @Test
    fun patchMainScreenCurrentPage_noOpWithoutMainScreenSection() {
        val original = """{"sections":["appIcons"]}"""
        val patched = ThemeLayoutExport.patchMainScreenCurrentPage(
            themeJson = original,
            currentPage = 2,
        ).getOrThrow()
        assertEquals(original, patched)
    }
}
