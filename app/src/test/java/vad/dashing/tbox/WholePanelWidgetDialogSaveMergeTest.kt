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
        showTboxDisconnectIndicator = false
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
            clickAction = true
        )
        val merged = mergeMainScreenPanelForWidgetDialogSave(baseMain, newWidgets, draft)
        assertEquals(newWidgets, merged.widgetsConfig)
        assertEquals("New", merged.name)
        assertEquals(SettingsManager.DASHBOARD_PANEL_MAX_GRID_DIMENSION, merged.rows)
        assertEquals(1, merged.cols)
        assertEquals(true, merged.showTboxDisconnectIndicator)
        assertEquals(true, merged.clickAction)
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
            clickAction = false
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
    }
}
