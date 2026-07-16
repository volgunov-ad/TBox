package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeMaterializationApplyTargetsTest {

    @Test
    fun resolveApplyTargetsForActivation_usesManifestForDriveModeWatcher() {
        val manifest = ThemeMaterialization.ThemeManifest(
            cacheKey = "drive_mode_2_eco",
            sourceUri = "content://theme/eco.tboxtheme",
            sourceDisplayName = "eco.tboxtheme",
            materializedAtMillis = 0L,
            fingerprint = "fp",
            sections = setOf(ThemeSection.MAIN_SCREEN, ThemeSection.APP_ICONS),
            applyTargets = setOf(
                ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS,
                ThemeApplyTarget.APP_ICONS,
            ),
        )
        val available = setOf(
            ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS,
            ThemeApplyTarget.MAIN_SCREEN_PANELS,
            ThemeApplyTarget.APP_ICONS,
        )

        val resolved = ThemeMaterialization.resolveApplyTargetsForActivation(
            manifest = manifest,
            themeSections = setOf(ThemeSection.MAIN_SCREEN, ThemeSection.APP_ICONS),
            availableTargets = available,
            requestedTargets = null,
        )

        assertEquals(
            setOf(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS, ThemeApplyTarget.APP_ICONS),
            resolved,
        )
    }

    @Test
    fun resolveApplyTargetsForActivation_fallsBackToLegacySectionsWhenManifestMissingTargets() {
        val manifest = ThemeMaterialization.ThemeManifest(
            cacheKey = "drive_mode_3_sport",
            sourceUri = "content://theme/sport.tboxtheme",
            sourceDisplayName = "sport.tboxtheme",
            materializedAtMillis = 0L,
            fingerprint = "fp",
            sections = setOf(ThemeSection.MAIN_SCREEN, ThemeSection.FLOATING_PANELS),
            applyTargets = emptySet(),
        )
        val available = setOf(
            ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS,
            ThemeApplyTarget.MAIN_SCREEN_PANELS,
            ThemeApplyTarget.TILE_BACKGROUNDS,
            ThemeApplyTarget.FLOATING_PANELS,
        )

        val resolved = ThemeMaterialization.resolveApplyTargetsForActivation(
            manifest = manifest,
            themeSections = setOf(ThemeSection.MAIN_SCREEN, ThemeSection.FLOATING_PANELS),
            availableTargets = available,
            requestedTargets = null,
        )

        assertTrue(ThemeApplyTarget.MAIN_SCREEN_PANELS in resolved)
        assertTrue(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS in resolved)
        assertTrue(ThemeApplyTarget.TILE_BACKGROUNDS in resolved)
        assertTrue(ThemeApplyTarget.FLOATING_PANELS in resolved)
    }

    @Test
    fun resolveApplyTargetsForMaterialize_storesUserSelectionOnFirstAssign() {
        val themeJson = """
            {
              "formatVersion": 1,
              "type": "tbox_theme",
              "sections": ["mainScreen"],
              "mainScreen": {
                "panels": [{ "id": "p1" }],
                "wallpaperSelectionByPage": { "light": {}, "dark": {} }
              }
            }
        """.trimIndent()
        val parsed = ThemeBundleExport.ParsedThemeBundle(
            themeJson = themeJson,
            icons = emptyMap(),
            httpRequestIcons = emptyMap(),
            tileBackgrounds = emptyMap(),
            lightWallpapers = mapOf("a.jpg" to byteArrayOf(1)),
            darkWallpapers = emptyMap(),
        )
        val available = ThemeApplyTargetAvailability.detectAvailable(parsed)
        val requested = setOf(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS)

        val resolved = ThemeMaterialization.resolveApplyTargetsForMaterialize(
            parsed = parsed,
            themeSections = setOf(ThemeSection.MAIN_SCREEN),
            requestedTargets = requested,
            existingManifest = null,
        )

        assertEquals(requested, resolved)
        assertTrue(available.contains(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS))
    }
}
