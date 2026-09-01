package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationFloatingPanelActionTest {
    @Test
    fun legacyDefaultsTargetAllPanelsAndToggle() {
        val action = AutomationAction.Builtin(
            type = AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS,
        )
        assertEquals(AutomationFloatingPanelScope.ALL, action.floatingPanelScope())
        assertNull(action.floatingPanelId())
        assertEquals(
            AutomationFloatingPanelVisibilityOp.TOGGLE,
            action.floatingPanelVisibilityOp(),
        )
        assertEquals(
            AutomationFloatingPanelEnabledOp.TOGGLE,
            action.floatingPanelEnabledOp(),
        )
    }

    @Test
    fun selectedPanelAndExplicitOpsRoundTrip() {
        val hide = AutomationAction.Builtin(
            type = AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS,
            stringValue = "panel-1",
            intValue = floatingPanelVisibilityOpToInt(AutomationFloatingPanelVisibilityOp.HIDE),
        )
        assertEquals(AutomationFloatingPanelScope.SELECTED, hide.floatingPanelScope())
        assertEquals("panel-1", hide.floatingPanelId())
        assertEquals(AutomationFloatingPanelVisibilityOp.HIDE, hide.floatingPanelVisibilityOp())

        val enable = AutomationAction.Builtin(
            type = AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED,
            stringValue = "panel-2",
            intValue = floatingPanelEnabledOpToInt(AutomationFloatingPanelEnabledOp.ENABLE),
        )
        assertEquals(AutomationFloatingPanelEnabledOp.ENABLE, enable.floatingPanelEnabledOp())
    }
}
