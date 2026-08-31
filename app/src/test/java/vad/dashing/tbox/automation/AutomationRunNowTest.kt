package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationRunNowTest {
    @Test
    fun triggerId_usesFirstTrigger() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.SystemEvent(
                    id = "2",
                    event = AutomationSystemEvent.MENU_OPENED,
                ),
                AutomationTrigger.SystemEvent(
                    id = "1",
                    event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                ),
            ),
        )
        assertEquals("2", AutomationRunNow.triggerId(definition))
    }

    @Test
    fun triggerId_fallsBackWhenBlank() {
        val definition = validDefinition().copy(
            triggers = listOf(
                AutomationTrigger.SystemEvent(
                    id = "   ",
                    event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                ),
            ),
        )
        assertEquals(AutomationRunNow.FALLBACK_TRIGGER_ID, AutomationRunNow.triggerId(definition))
    }

    @Test
    fun rejection_missingDefinition() {
        assertEquals(
            "Автоматизация не найдена. Сначала сохраните правило.",
            AutomationRunNow.rejection(null),
        )
    }

    @Test
    fun rejection_invalidDefinition() {
        val reason = AutomationRunNow.rejection(validDefinition().copy(name = ""))
        assertNotNull(reason)
        assert(requireNotNull(reason).contains("название"))
    }

    @Test
    fun rejection_validDefinition() {
        assertNull(AutomationRunNow.rejection(validDefinition()))
    }

    private fun validDefinition(
        triggers: List<AutomationTrigger> = listOf(
            AutomationTrigger.SystemEvent(
                id = "service",
                event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
            ),
        ),
    ): AutomationDefinition = AutomationDefinition(
        id = "automation",
        name = "Test",
        enabled = false,
        triggers = triggers,
        actions = listOf(AutomationAction.Delay(0L)),
    )
}
