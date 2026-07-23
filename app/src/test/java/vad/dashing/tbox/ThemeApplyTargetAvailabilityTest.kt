package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeApplyTargetAvailabilityTest {

    @Test
    fun detectAvailable_readsSectionsAssetsAndWidgetReferences() {
        val themeJson = """
            {
              "formatVersion": 1,
              "type": "tbox_theme",
              "sections": ["mainScreen", "floatingPanels", "appIcons"],
              "mainScreen": {
                "panels": [{
                  "id": "p1",
                  "widgets": [{
                    "tileBackgroundImageRelPathLight": "tile_backgrounds/p1/0_light"
                  }]
                }],
                "wallpaperSelectionByPage": { "light": {}, "dark": {} }
              },
              "floatingPanels": { "panels": [{ "id": "f1" }] },
              "appIcons": { "packages": ["com.example.app"], "httpRequestIconKeys": [] }
            }
        """.trimIndent()
        val parsed = ThemeBundleExport.ParsedThemeBundle(
            themeJson = themeJson,
            icons = mapOf("com.example.app" to byteArrayOf(1)),
            httpRequestIcons = emptyMap(),
            tileBackgrounds = mapOf("p1/0_light" to byteArrayOf(2)),
            lightWallpapers = mapOf("a.jpg" to byteArrayOf(3)),
            darkWallpapers = emptyMap(),
        )

        val available = ThemeApplyTargetAvailability.detectAvailable(parsed)

        assertTrue(ThemeApplyTarget.MAIN_SCREEN_PANELS in available)
        assertTrue(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS in available)
        assertTrue(ThemeApplyTarget.TILE_BACKGROUNDS in available)
        assertTrue(ThemeApplyTarget.FLOATING_PANELS in available)
        assertTrue(ThemeApplyTarget.APP_ICONS in available)
    }

    @Test
    fun detectAvailable_omitsMissingElements() {
        val themeJson = """
            {
              "formatVersion": 1,
              "type": "tbox_theme",
              "sections": ["appIcons"],
              "appIcons": { "packages": ["com.example.app"], "httpRequestIconKeys": [] }
            }
        """.trimIndent()
        val parsed = ThemeBundleExport.ParsedThemeBundle(
            themeJson = themeJson,
            icons = mapOf("com.example.app" to byteArrayOf(1)),
            httpRequestIcons = emptyMap(),
            tileBackgrounds = emptyMap(),
            lightWallpapers = emptyMap(),
            darkWallpapers = emptyMap(),
        )

        val available = ThemeApplyTargetAvailability.detectAvailable(parsed)

        assertEquals(setOf(ThemeApplyTarget.APP_ICONS), available)
        assertFalse(ThemeApplyTarget.MAIN_SCREEN_PANELS in available)
        assertFalse(ThemeApplyTarget.TILE_BACKGROUNDS in available)
    }
}
