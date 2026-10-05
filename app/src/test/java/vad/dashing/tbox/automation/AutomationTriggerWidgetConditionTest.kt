package vad.dashing.tbox.automation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.AUTOMATION_TRIGGER_ID_MAX_CHARS

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutomationTriggerWidgetConditionTest {
    @Before
    fun reset() {
        AutomationTriggerWidgetState.resetForTests()
    }

    @After
    fun cleanup() {
        AutomationTriggerWidgetState.resetForTests()
    }

    @Test
    fun evaluation_matchesWidgetActiveState() {
        AutomationTriggerWidgetState.setActive("warmup", true)
        assertTrue(evaluate(AutomationCondition.TriggerWidget(triggerId = "warmup", active = true)))
        assertFalse(evaluate(AutomationCondition.TriggerWidget(triggerId = "warmup", active = false)))

        AutomationTriggerWidgetState.setActive("warmup", false)
        assertFalse(evaluate(AutomationCondition.TriggerWidget(triggerId = "warmup", active = true)))
        assertTrue(evaluate(AutomationCondition.TriggerWidget(triggerId = "warmup", active = false)))
    }

    @Test
    fun evaluation_unknownIdIsNeverActive() {
        assertFalse(evaluate(AutomationCondition.TriggerWidget(triggerId = "missing", active = true)))
        assertTrue(evaluate(AutomationCondition.TriggerWidget(triggerId = "missing", active = false)))
    }

    @Test
    fun codec_roundTripsTriggerWidgetCondition() {
        val definition = definitionWithCondition(
            AutomationCondition.TriggerWidget(triggerId = "warmup", active = false),
        )
        val encoded = AutomationCodec.encodeDefinitionDocument(definition)
        val decoded = AutomationCodec.decodeImport(encoded).getOrThrow().single()
        assertEquals(
            AutomationCondition.TriggerWidget(triggerId = "warmup", active = false),
            decoded.conditions.single(),
        )
    }

    @Test
    fun validator_flagsBlankAndOverlongTriggerId() {
        val blank = definitionWithCondition(
            AutomationCondition.TriggerWidget(triggerId = "   ", active = true),
        )
        assertTrue(
            AutomationValidator.validate(blank).any { it.path.endsWith("triggerId") },
        )

        val overlong = definitionWithCondition(
            AutomationCondition.TriggerWidget(
                triggerId = "x".repeat(AUTOMATION_TRIGGER_ID_MAX_CHARS + 1),
                active = true,
            ),
        )
        assertTrue(
            AutomationValidator.validate(overlong).any { it.path.endsWith("triggerId") },
        )

        val valid = definitionWithCondition(
            AutomationCondition.TriggerWidget(triggerId = "warmup", active = true),
        )
        assertTrue(
            AutomationValidator.validate(valid).none { it.path.endsWith("triggerId") },
        )
    }

    private fun evaluate(condition: AutomationCondition.TriggerWidget): Boolean =
        AutomationEvaluator.evaluateCondition(
            condition = condition,
            context = AutomationTriggerContext("a", "1", 0L),
            snapshot = emptyMap(),
        )

    private fun definitionWithCondition(condition: AutomationCondition): AutomationDefinition =
        AutomationDefinition(
            id = "a1",
            name = "widget",
            description = "",
            enabled = true,
            triggers = listOf(
                AutomationTrigger.SystemEvent(
                    id = "t1",
                    event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                ),
            ),
            conditions = listOf(condition),
            actions = emptyList(),
        )
}