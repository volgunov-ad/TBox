package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.AUTOMATION_TRIGGER_WIDGET_TOGGLE_INT

class AutomationTriggerWidgetCommandTest {

    @Test
    fun legacySlots_mapByBoolValue() {
        assertEquals(
            AutomationTriggerWidgetCommand.ACTIVATE,
            builtinActionTriggerWidgetCommand(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET,
                    boolValue = true,
                ),
            ),
        )
        assertEquals(
            AutomationTriggerWidgetCommand.DEACTIVATE,
            builtinActionTriggerWidgetCommand(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET,
                ),
            ),
        )
    }

    @Test
    fun toggleIntValue_overridesBoolValue() {
        assertEquals(
            AutomationTriggerWidgetCommand.TOGGLE,
            builtinActionTriggerWidgetCommand(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET,
                    intValue = AUTOMATION_TRIGGER_WIDGET_TOGGLE_INT,
                    boolValue = true,
                ),
            ),
        )
        assertEquals(
            AutomationTriggerWidgetCommand.TOGGLE,
            builtinActionTriggerWidgetCommand(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET,
                    intValue = AUTOMATION_TRIGGER_WIDGET_TOGGLE_INT,
                ),
            ),
        )
    }

    @Test
    fun nonToggleIntValue_fallsBackToBoolValue() {
        assertEquals(
            AutomationTriggerWidgetCommand.DEACTIVATE,
            builtinActionTriggerWidgetCommand(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET,
                    intValue = 1,
                ),
            ),
        )
    }
}