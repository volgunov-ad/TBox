package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class WholePanelWidgetDialogSaveMergeTest {

    private val baseMain = MainScreenPanelConfig(
        id = "p1",
        name = "Old",
        enabled = true,
        widgetsConfig = emptyList(),
        rows = 2,
        cols = 2,
        relX = 0f,
        relY = 0f,
        relWidth = 0.5f,
        relHeight = 0.5f,
        background = false,
        clickAction = false,
        showTboxDisconnectIndicator = false,
        pageNumber = 1,
    )

    private val baseFloat = FloatingDashboardConfig(
        id = "f1",
        name = "Float",
        enabled = true,
        widgetsConfig = emptyList(),
        rows = 1,
        cols = 1,
        width = 100,
        height = 100,
        startX = 0,
        startY = 0,
        background = false,
        clickAction = true,
        showTboxDisconnectIndicator = true
    )

    @Test
    fun mergeMainScreen_widgetsOnly_preservesPanelFields() {
        val newWidgets = listOf(FloatingDashboardWidgetConfig(dataKey = "netWidget"))
        val merged = mergeMainScreenPanelForWidgetDialogSave(baseMain, newWidgets, null)
        assertEquals(newWidgets, merged.widgetsConfig)
        assertEquals(baseMain.name, merged.name)
        assertEquals(baseMain.rows, merged.rows)
        assertEquals(baseMain.cols, merged.cols)
        assertEquals(baseMain.clickAction, merged.clickAction)
        assertEquals(baseMain.relWidth, merged.relWidth, 0f)
    }

    @Test
    fun mergeMainScreen_withWholePanel_appliesDraftAndCoercesGrid() {
        val newWidgets = listOf(FloatingDashboardWidgetConfig(dataKey = "x"))
        val draft = MainScreenWholePanelFieldsForWidgetDialogSave(
            name = "New",
            rows = 99,
            cols = 0,
            showTboxDisconnectIndicator = true,
            clickAction = true,
            pageNumber = 2,
            gridSpacingDp = 99,
            collapseEdge = PanelCollapseEdge.BOTTOM.storageValue,
            collapseStripThicknessDp = 99,
            collapseStripColorLight = 0xFF112233.toInt(),
            collapseStripColorDark = 0xFF445566.toInt(),
            collapseStripExpandedColorLight = 0x33112233.toInt(),
            collapseStripExpandedColorDark = 0x33445566.toInt(),
            collapseOnTileTap = true,
            collapseOnTileTapDelaySec = 7,
        )
        val merged = mergeMainScreenPanelForWidgetDialogSave(baseMain, newWidgets, draft)
        assertEquals(newWidgets, merged.widgetsConfig)
        assertEquals("New", merged.name)
        assertEquals(SettingsManager.DASHBOARD_PANEL_MAX_GRID_DIMENSION, merged.rows)
        assertEquals(1, merged.cols)
        assertEquals(true, merged.showTboxDisconnectIndicator)
        assertEquals(true, merged.clickAction)
        assertEquals(2, merged.pageNumber)
        assertEquals(MAX_PANEL_GRID_SPACING_DP, merged.gridSpacingDp)
        assertEquals(PanelCollapseEdge.BOTTOM.storageValue, merged.collapseEdge)
        assertEquals(MAX_PANEL_COLLAPSE_STRIP_THICKNESS_DP, merged.collapseStripThicknessDp)
        assertEquals(0xFF112233.toInt(), merged.collapseStripColorLight)
        assertEquals(0xFF445566.toInt(), merged.collapseStripColorDark)
        assertEquals(0x33112233.toInt(), merged.collapseStripExpandedColorLight)
        assertEquals(0x33445566.toInt(), merged.collapseStripExpandedColorDark)
        assertEquals(true, merged.collapseOnTileTap)
        assertEquals(7, merged.collapseOnTileTapDelaySec)
        assertEquals(baseMain.relWidth, merged.relWidth, 0f)
    }

    @Test
    fun mergeFloating_widgetsOnly_preservesPanelFields() {
        val newWidgets = listOf(FloatingDashboardWidgetConfig(dataKey = "y"))
        val merged = mergeFloatingDashboardForWidgetDialogSave(baseFloat, newWidgets, null)
        assertEquals(newWidgets, merged.widgetsConfig)
        assertEquals(baseFloat.width, merged.width)
        assertEquals(baseFloat.startX, merged.startX)
    }

    @Test
    fun mergeFloating_withWholePanel_appliesDraft() {
        val draft = FloatingWholePanelFieldsForWidgetDialogSave(
            name = "N",
            rows = 3,
            cols = 4,
            showTboxDisconnectIndicator = false,
            clickAction = false,
            gridSpacingDp = 12,
            collapseEdge = PanelCollapseEdge.LEFT.storageValue,
            collapseStripThicknessDp = 24,
            collapseStripColorLight = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT,
            collapseStripColorDark = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK,
            collapseStripExpandedColorLight = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT,
            collapseStripExpandedColorDark = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK,
            collapseOnTileTap = false,
            collapseOnTileTapDelaySec = 99,
        )
        val merged = mergeFloatingDashboardForWidgetDialogSave(
            baseFloat,
            emptyList(),
            draft
        )
        assertEquals("N", merged.name)
        assertEquals(3, merged.rows)
        assertEquals(4, merged.cols)
        assertEquals(false, merged.showTboxDisconnectIndicator)
        assertEquals(false, merged.clickAction)
        assertEquals(12, merged.gridSpacingDp)
        assertEquals(PanelCollapseEdge.LEFT.storageValue, merged.collapseEdge)
        assertEquals(24, merged.collapseStripThicknessDp)
        assertEquals(DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT, merged.collapseStripExpandedColorLight)
        assertEquals(DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK, merged.collapseStripExpandedColorDark)
        assertEquals(MAX_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC, merged.collapseOnTileTapDelaySec)
    }
}
