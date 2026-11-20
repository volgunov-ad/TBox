package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            collapseTouchZoneThicknessDp = 80,
            collapseStripLengthPercentCollapsed = 40,
            collapseStripLengthPercentExpanded = 999,
            collapseStripPositionPercentCollapsed = 80,
            collapseStripPositionPercentExpanded = 10,
            collapseTouchZoneMatchStrip = true,
            collapseStripColorLight = 0xFF112233.toInt(),
            collapseStripColorDark = 0xFF445566.toInt(),
            collapseStripExpandedColorLight = 0x33112233.toInt(),
            collapseStripExpandedColorDark = 0x33445566.toInt(),
            collapseOnStripTap = true,
            collapseOnStripDoubleTap = true,
            collapseOnTileTap = true,
            collapseOnTileTapDelaySec = 7,
            panelBackgroundColorLight = 0x40AABBCC.toInt(),
            panelBackgroundColorDark = null,
            panelBackgroundImageRelPathLight = "panel_backgrounds/p1_light",
            panelBackgroundImageRelPathDark = null,
            panelShape = 16,
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
        assertEquals(80, merged.collapseTouchZoneThicknessDp)
        assertEquals(40, merged.collapseStripLengthPercentCollapsed)
        assertEquals(MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT, merged.collapseStripLengthPercentExpanded)
        assertEquals(60, merged.collapseStripPositionPercentCollapsed) // min(80, 100-40)
        assertEquals(0, merged.collapseStripPositionPercentExpanded) // length 100 → position 0
        assertEquals(true, merged.collapseTouchZoneMatchStrip)
        assertEquals(0xFF112233.toInt(), merged.collapseStripColorLight)
        assertEquals(0xFF445566.toInt(), merged.collapseStripColorDark)
        assertEquals(0x33112233.toInt(), merged.collapseStripExpandedColorLight)
        assertEquals(0x33445566.toInt(), merged.collapseStripExpandedColorDark)
        assertEquals(true, merged.collapseOnStripTap)
        assertEquals(true, merged.collapseOnStripDoubleTap)
        assertEquals(true, merged.collapseOnTileTap)
        assertEquals(7, merged.collapseOnTileTapDelaySec)
        assertEquals(0x40AABBCC.toInt(), merged.panelBackgroundColorLight)
        assertNull(merged.panelBackgroundColorDark)
        assertEquals("panel_backgrounds/p1_light", merged.panelBackgroundImageRelPathLight)
        assertEquals(16, merged.panelShape)
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
            collapseTouchZoneThicknessDp = 48,
            collapseStripLengthPercentCollapsed = 50,
            collapseStripLengthPercentExpanded = 30,
            collapseStripPositionPercentCollapsed = 10,
            collapseStripPositionPercentExpanded = 20,
            collapseTouchZoneMatchStrip = false,
            collapseStripColorLight = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT,
            collapseStripColorDark = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK,
            collapseStripExpandedColorLight = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT,
            collapseStripExpandedColorDark = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK,
            collapseOnStripTap = false,
            collapseOnStripDoubleTap = false,
            collapseOnTileTap = false,
            collapseOnTileTapDelaySec = 99,
            panelBackgroundColorLight = null,
            panelBackgroundColorDark = 0x22000000,
            panelBackgroundImageRelPathLight = null,
            panelBackgroundImageRelPathDark = "panel_backgrounds/f1_dark",
            panelShape = 50,
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
        assertEquals(48, merged.collapseTouchZoneThicknessDp)
        assertEquals(50, merged.collapseStripLengthPercentCollapsed)
        assertEquals(30, merged.collapseStripLengthPercentExpanded)
        assertEquals(10, merged.collapseStripPositionPercentCollapsed)
        assertEquals(20, merged.collapseStripPositionPercentExpanded)
        assertEquals(false, merged.collapseTouchZoneMatchStrip)
        assertEquals(DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT, merged.collapseStripExpandedColorLight)
        assertEquals(DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK, merged.collapseStripExpandedColorDark)
        assertEquals(MAX_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC, merged.collapseOnTileTapDelaySec)
        assertEquals(false, merged.collapseOnStripDoubleTap)
        assertNull(merged.panelBackgroundColorLight)
        assertEquals(0x22000000, merged.panelBackgroundColorDark)
        assertEquals("panel_backgrounds/f1_dark", merged.panelBackgroundImageRelPathDark)
        assertEquals(50, merged.panelShape)
    }

    private fun mainDraft() = MainScreenWholePanelFieldsForWidgetDialogSave(
        name = baseMain.name,
        rows = baseMain.rows,
        cols = baseMain.cols,
        showTboxDisconnectIndicator = false,
        clickAction = false,
        pageNumber = 1,
        gridSpacingDp = DEFAULT_PANEL_GRID_SPACING_DP,
        collapseEdge = PanelCollapseEdge.NONE.storageValue,
        collapseStripThicknessDp = DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
        collapseTouchZoneThicknessDp = DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
        collapseStripColorLight = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT,
        collapseStripColorDark = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK,
        collapseStripExpandedColorLight = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT,
        collapseStripExpandedColorDark = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK,
        collapseOnStripTap = false,
        collapseOnStripDoubleTap = false,
        collapseOnTileTap = false,
        collapseOnTileTapDelaySec = 0,
    )

    private fun floatingDraft() = FloatingWholePanelFieldsForWidgetDialogSave(
        name = baseFloat.name,
        rows = baseFloat.rows,
        cols = baseFloat.cols,
        showTboxDisconnectIndicator = true,
        clickAction = true,
        gridSpacingDp = DEFAULT_PANEL_GRID_SPACING_DP,
        collapseEdge = PanelCollapseEdge.NONE.storageValue,
        collapseStripThicknessDp = DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
        collapseTouchZoneThicknessDp = DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
        collapseStripColorLight = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT,
        collapseStripColorDark = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK,
        collapseStripExpandedColorLight = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT,
        collapseStripExpandedColorDark = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK,
        collapseOnStripTap = false,
        collapseOnStripDoubleTap = false,
        collapseOnTileTap = false,
        collapseOnTileTapDelaySec = 0,
    )

    @Test
    fun mergeMainScreen_withPlacement_appliesAndClampsLayout() {
        val draft = mainDraft().copy(relXPercent = 30, relYPercent = 150, relWidthPercent = 1, relHeightPercent = 40)
        val merged = mergeMainScreenPanelForWidgetDialogSave(baseMain, emptyList(), draft)
        assertEquals(0.3f, merged.relX, 0.0001f)
        assertEquals(1f, merged.relY, 0.0001f)
        assertEquals(MIN_MAIN_SCREEN_PANEL_REL_FRACTION, merged.relWidth, 0.0001f)
        assertEquals(0.4f, merged.relHeight, 0.0001f)
    }

    @Test
    fun mergeMainScreen_withoutPlacement_keepsExactLayout() {
        val precise = baseMain.copy(relX = 0.4567f, relWidth = 0.3333f)
        val merged = mergeMainScreenPanelForWidgetDialogSave(precise, emptyList(), mainDraft())
        assertEquals(0.4567f, merged.relX, 0f)
        assertEquals(0.3333f, merged.relWidth, 0f)
    }

    @Test
    fun mergeFloating_withPlacement_appliesAndClampsGeometry() {
        val draft = floatingDraft().copy(width = 5, height = 300, startX = -500, startY = 40)
        val merged = mergeFloatingDashboardForWidgetDialogSave(baseFloat, emptyList(), draft)
        assertEquals(MIN_FLOATING_PANEL_SIZE_PX, merged.width)
        assertEquals(300, merged.height)
        assertEquals(-500, merged.startX)
        assertEquals(40, merged.startY)
    }

    @Test
    fun mergeFloating_withoutPlacement_keepsGeometry() {
        val merged = mergeFloatingDashboardForWidgetDialogSave(baseFloat, emptyList(), floatingDraft())
        assertEquals(baseFloat.width, merged.width)
        assertEquals(baseFloat.height, merged.height)
        assertEquals(baseFloat.startX, merged.startX)
        assertEquals(baseFloat.startY, merged.startY)
    }
}
