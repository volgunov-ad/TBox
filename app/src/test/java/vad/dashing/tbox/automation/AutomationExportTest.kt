package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationExportTest {
    @Test
    fun withUniqueIds_keepsNewAndRewritesCollisions() {
        val first = AutomationDefinition.newDraft().copy(
            id = "keep-me",
            name = "A",
            actions = listOf(AutomationAction.Delay(0L)),
        )
        val second = first.copy(id = "taken")
        val third = first.copy(id = "also-new")
        val resolved = AutomationExport.withUniqueIds(
            incoming = listOf(first, second, third),
            existingIds = setOf("taken"),
        )
        assertEquals("keep-me", resolved[0].id)
        assertNotEquals("taken", resolved[1].id)
        assertEquals("A", resolved[1].name)
        assertEquals("also-new", resolved[2].id)
        assertEquals(3, resolved.map { it.id }.toSet().size)
    }

    @Test
    fun fileName_sanitizesAndFallsBack() {
        val named = AutomationDefinition.newDraft().copy(name = "Люк 50%/test")
        val fileName = AutomationExport.fileName(named, timestampMs = 0L)
        assertTrue(fileName.startsWith("tbox_automation_Люк_50%_test_"))
        assertTrue(fileName.endsWith(".json"))
        val blank = named.copy(name = "  ???  ")
        assertTrue(AutomationExport.fileName(blank, timestampMs = 0L).startsWith("tbox_automation_automation_"))
        assertEquals("rule", AutomationExport.sanitizeBaseName("rule.json"))
        assertEquals(null, AutomationExport.sanitizeBaseName("***"))
    }
}
