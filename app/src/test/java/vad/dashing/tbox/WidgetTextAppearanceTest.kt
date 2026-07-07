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
}
