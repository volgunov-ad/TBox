package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTriggerWidgetStateTest {

    @Test
    fun startsEmpty_andActivatesDeactivatesById() {
        AutomationTriggerWidgetState.resetForTests()
        assertTrue(AutomationTriggerWidgetState.activeIds.value.isEmpty())
        assertFalse(AutomationTriggerWidgetState.isActive("btn1"))

        AutomationTriggerWidgetState.setActive("btn1", true)
        assertTrue(AutomationTriggerWidgetState.isActive("btn1"))
        assertFalse(AutomationTriggerWidgetState.isActive("btn2"))

        AutomationTriggerWidgetState.setActive("btn1", false)
        assertFalse(AutomationTriggerWidgetState.isActive("btn1"))
    }

    @Test
    fun tracksMultipleIdsIndependently() {
        AutomationTriggerWidgetState.resetForTests()
        AutomationTriggerWidgetState.setActive("btn1", true)
        AutomationTriggerWidgetState.setActive("btn2", true)
        AutomationTriggerWidgetState.setActive("btn1", false)
        assertEquals(setOf("btn2"), AutomationTriggerWidgetState.activeIds.value)
    }

    @Test
    fun blankId_isIgnored() {
        AutomationTriggerWidgetState.resetForTests()
        AutomationTriggerWidgetState.setActive("   ", true)
        AutomationTriggerWidgetState.setActive("", true)
        assertTrue(AutomationTriggerWidgetState.activeIds.value.isEmpty())
    }
}
