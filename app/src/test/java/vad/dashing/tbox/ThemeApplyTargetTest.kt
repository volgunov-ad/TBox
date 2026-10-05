package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeApplyTargetTest {

    @Test
    fun fromLegacySections_mapsMainScreenToSubTargets() {
        val targets = ThemeApplyTarget.fromLegacySections(setOf(ThemeSection.MAIN_SCREEN))
        assertTrue(ThemeApplyTarget.MAIN_SCREEN_PANELS in targets)
        assertTrue(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS in targets)
        assertTrue(ThemeApplyTarget.TILE_BACKGROUNDS in targets)
        assertFalse(ThemeApplyTarget.FLOATING_PANELS in targets)
    }

    @Test
    fun resolveActive_prefersExplicitTargets() {
        val resolved = ThemeApplyTarget.resolveActive(
            applyTargets = setOf(ThemeApplyTarget.APP_ICONS),
            legacySections = setOf(ThemeSection.MAIN_SCREEN),
        )
        assertEquals(setOf(ThemeApplyTarget.APP_ICONS), resolved)
    }

    @Test
    fun exportSectionsFromTargets_mapsBackToThemeSections() {
        val sections = ThemeApplyTarget.exportSectionsFromTargets(
            setOf(
                ThemeApplyTarget.MAIN_SCREEN_PANELS,
                ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS,
                ThemeApplyTarget.FLOATING_PANELS,
                ThemeApplyTarget.APP_ICONS,
                ThemeApplyTarget.UI_ICONS,
            ),
        )
        assertEquals(
            setOf(
                ThemeSection.MAIN_SCREEN,
                ThemeSection.FLOATING_PANELS,
                ThemeSection.APP_ICONS,
                ThemeSection.UI_ICONS,
            ),
            sections,
        )
    }

    @Test
    fun uiIconsRemainIndependentFromAppIcons() {
        assertEquals(
            setOf(ThemeApplyTarget.UI_ICONS),
            ThemeApplyTarget.fromLegacySections(setOf(ThemeSection.UI_ICONS)),
        )
        assertFalse(
            ThemeApplyTarget.APP_ICONS in
                ThemeApplyTarget.fromLegacySections(setOf(ThemeSection.UI_ICONS)),
        )
    }
}
