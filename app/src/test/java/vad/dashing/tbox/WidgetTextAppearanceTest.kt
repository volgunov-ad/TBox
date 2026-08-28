package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTextAppearanceTest {

    @Test
    fun normalizeWidgetTextAlign_clampsToValidRange() {
        assertEquals(WIDGET_TEXT_ALIGN_CENTER, normalizeWidgetTextAlign(-1))
        assertEquals(WIDGET_TEXT_ALIGN_START, normalizeWidgetTextAlign(1))
        assertEquals(WIDGET_TEXT_ALIGN_END, normalizeWidgetTextAlign(2))
        assertEquals(WIDGET_TEXT_ALIGN_END, normalizeWidgetTextAlign(99))
    }

    @Test
    fun normalizeWidgetFontWeight_defaultsToMediumConstant() {
        assertEquals(WIDGET_FONT_WEIGHT_MEDIUM, DEFAULT_WIDGET_FONT_WEIGHT)
        assertEquals(WIDGET_FONT_WEIGHT_NORMAL, normalizeWidgetFontWeight(0))
        assertEquals(WIDGET_FONT_WEIGHT_MEDIUM, normalizeWidgetFontWeight(1))
        assertEquals(WIDGET_FONT_WEIGHT_SEMI_BOLD, normalizeWidgetFontWeight(2))
        assertEquals(WIDGET_FONT_WEIGHT_SEMI_BOLD, normalizeWidgetFontWeight(99))
    }

    @Test
    fun resolveDefaultTitlePosition_appLauncherBottomOthersTop() {
        assertEquals(WIDGET_TITLE_POSITION_BOTTOM, resolveDefaultTitlePositionForDataKey(APP_LAUNCHER_WIDGET_DATA_KEY))
        assertEquals(WIDGET_TITLE_POSITION_TOP, resolveDefaultTitlePositionForDataKey("fuelLevelPercentageFiltered"))
    }

    @Test
    fun normalizePanelGridSpacingDp_clampsRange() {
        assertEquals(0, normalizePanelGridSpacingDp(-5))
        assertEquals(8, normalizePanelGridSpacingDp(8))
        assertEquals(MAX_PANEL_GRID_SPACING_DP, normalizePanelGridSpacingDp(100))
    }

    @Test
    fun normalizeWidgetPaddingPercent_clampsRange() {
        assertEquals(0, normalizeWidgetPaddingPercent(-1))
        assertEquals(25, normalizeWidgetPaddingPercent(25))
        assertEquals(MAX_WIDGET_PADDING_PERCENT, normalizeWidgetPaddingPercent(99))
    }

    @Test
    fun normalizePanelLayoutSnapDp_clampsRange() {
        assertEquals(MIN_PANEL_LAYOUT_SNAP_DP, normalizePanelLayoutSnapDp(0))
        assertEquals(10, normalizePanelLayoutSnapDp(10))
        assertEquals(MAX_PANEL_LAYOUT_SNAP_DP, normalizePanelLayoutSnapDp(100))
    }

    @Test
    fun snapToGrid_roundsToNearestStep() {
        assertEquals(0f, snapToGrid(0.4f, 1f), 0.001f)
        assertEquals(10f, snapToGrid(12f, 10f), 0.001f)
        assertEquals(20f, snapToGrid(15f, 10f), 0.001f)
        assertEquals(8f, snapToGrid(7.6f, 1f), 0.001f)
    }

    @Test
    fun maybeSnapToGrid_skipsWhenStepBelowOne() {
        assertEquals(12.3f, maybeSnapToGrid(12.3f, 0f), 0.001f)
        assertEquals(12.3f, maybeSnapToGrid(12.3f, 0.5f), 0.001f)
        assertEquals(10f, maybeSnapToGrid(12f, 10f), 0.001f)
    }

    @Test
    fun mainScreenPanelRelMinPercent_matchesFraction() {
        assertEquals(2, MIN_MAIN_SCREEN_PANEL_REL_PERCENT)
        assertEquals(0.02f, MIN_MAIN_SCREEN_PANEL_REL_FRACTION, 0.0001f)
        assertEquals(5, MAIN_SCREEN_LAYOUT_GRID_MIN_SNAP_DP_EXCLUSIVE)
    }
}
