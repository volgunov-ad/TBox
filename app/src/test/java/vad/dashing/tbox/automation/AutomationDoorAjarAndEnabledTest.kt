package vad.dashing.tbox.automation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.mbcan.BcmDoorDomain
import vad.dashing.tbox.mbcan.TurnSignalsDomain

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutomationDoorAjarAndEnabledTest {
    @Before
    fun reset() {
        AutomationEnabledLookup.resetForTests()
    }

    @After
    fun cleanup() {
        AutomationEnabledLookup.resetForTests()
    }

    @Test
    fun doorAjar_catalogOptionsAreClosedOpen() {
        listOf(
            AutomationSignalId.DOOR_FRONT_LEFT,
            AutomationSignalId.DOOR_FRONT_RIGHT,
            AutomationSignalId.DOOR_REAR_LEFT,
            AutomationSignalId.DOOR_REAR_RIGHT,
        ).forEach { id ->
            val descriptor = AutomationSignalCatalog.get(id)
            assertEquals(id.name, listOf("closed", "open"), descriptor.stateOptions)
            assertTrue(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
            assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
        }
    }

    @Test
    fun doorAjar_encodingMapsBoolean() {
        assertEquals("open", AutomationSignalStateEncoding.doorAjarFromOpen(true))
        assertEquals("closed", AutomationSignalStateEncoding.doorAjarFromOpen(false))
        assertNull(AutomationSignalStateEncoding.doorAjarFromOpen(null))
    }

    @Test
    fun doorAjar_a9MbCanDecode_matchesTrunkScale() {
        assertEquals(false, BcmDoorDomain.decodeAjarOpenMbCan(1))
        assertEquals(true, BcmDoorDomain.decodeAjarOpenMbCan(2))
        assertEquals(false, BcmDoorDomain.decodeAjarOpenMbCan(0))
        assertNull(BcmDoorDomain.decodeAjarOpenMbCan(3))
        assertNull(BcmDoorDomain.decodeAjarOpenMbCan(null))
    }

    @Test
    fun doorAjar_a10CemDecode_usesCemBinaryActive() {
        assertEquals(true, BcmDoorDomain.decodeAjarOpenVhalCem(1))
        assertEquals(false, BcmDoorDomain.decodeAjarOpenVhalCem(0))
        assertNull(BcmDoorDomain.decodeAjarOpenVhalCem(2))
        assertEquals(
            TurnSignalsDomain.decodeCemBinaryActive(1),
            BcmDoorDomain.decodeAjarOpenVhalCem(1),
        )
    }

    @Test
    fun doorAjar_codecRoundTripsStateEqualsTriggerAndCondition() {
        val definition = AutomationDefinition(
            id = "door-rule",
            name = "door",
            enabled = true,
            triggers = listOf(
                AutomationTrigger.StateEquals(
                    id = "t1",
                    signal = AutomationSignalId.DOOR_FRONT_LEFT,
                    source = AutomationSignalSource.HEAD_UNIT,
                    expectedState = "open",
                ),
            ),
            conditions = listOf(
                AutomationCondition.State(
                    signal = AutomationSignalId.DOOR_REAR_RIGHT,
                    source = AutomationSignalSource.HEAD_UNIT,
                    expectedState = "closed",
                ),
            ),
            actions = emptyList(),
        )
        val encoded = AutomationCodec.encodeDefinitionDocument(definition)
        val decoded = AutomationCodec.decodeImport(encoded).getOrThrow().single()
        assertEquals(definition.triggers.single(), decoded.triggers.single())
        assertEquals(definition.conditions.single(), decoded.conditions.single())
    }

    @Test
    fun doorAjar_evaluatorStateEqualsMatchesSnapshot() {
        val key = AutomationSignalKey(
            AutomationSignalId.DOOR_FRONT_LEFT,
            AutomationSignalSource.HEAD_UNIT,
        )
        assertTrue(
            AutomationEvaluator.evaluateCondition(
                condition = AutomationCondition.State(
                    signal = AutomationSignalId.DOOR_FRONT_LEFT,
                    source = AutomationSignalSource.HEAD_UNIT,
                    expectedState = "open",
                ),
                context = AutomationTriggerContext("a", "1", 0L),
                snapshot = mapOf(key to AutomationSignalValue.State("open")),
            ),
        )
        assertFalse(
            AutomationEvaluator.evaluateCondition(
                condition = AutomationCondition.State(
                    signal = AutomationSignalId.DOOR_FRONT_LEFT,
                    source = AutomationSignalSource.HEAD_UNIT,
                    expectedState = "closed",
                ),
                context = AutomationTriggerContext("a", "1", 0L),
                snapshot = mapOf(key to AutomationSignalValue.State("open")),
            ),
        )
    }

    @Test
    fun automationEnabled_conditionMatchesLookup() {
        AutomationEnabledLookup.replaceAll(
            listOf(
                AutomationDefinition(
                    id = "rule-a",
                    name = "A",
                    enabled = true,
                    triggers = emptyList(),
                    actions = emptyList(),
                ),
                AutomationDefinition(
                    id = "rule-b",
                    name = "B",
                    enabled = false,
                    triggers = emptyList(),
                    actions = emptyList(),
                ),
            ),
        )
        assertTrue(
            evaluateEnabled(
                AutomationCondition.AutomationEnabled(automationId = "rule-a", enabled = true),
            ),
        )
        assertFalse(
            evaluateEnabled(
                AutomationCondition.AutomationEnabled(automationId = "rule-a", enabled = false),
            ),
        )
        assertTrue(
            evaluateEnabled(
                AutomationCondition.AutomationEnabled(automationId = "rule-b", enabled = false),
            ),
        )
        // Unknown id treated as disabled (same as missing trigger widget).
        assertFalse(
            evaluateEnabled(
                AutomationCondition.AutomationEnabled(automationId = "missing", enabled = true),
            ),
        )
        assertTrue(
            evaluateEnabled(
                AutomationCondition.AutomationEnabled(automationId = "missing", enabled = false),
            ),
        )
    }

    @Test
    fun automationEnabled_codecRoundTripsConditionAndBuiltin() {
        val definition = AutomationDefinition(
            id = "ctrl",
            name = "ctrl",
            enabled = true,
            triggers = listOf(
                AutomationTrigger.SystemEvent(
                    id = "t1",
                    event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                ),
            ),
            conditions = listOf(
                AutomationCondition.AutomationEnabled(
                    automationId = "other-id",
                    enabled = false,
                ),
            ),
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_AUTOMATION_ENABLED,
                    stringValue = "other-id",
                    boolValue = true,
                ),
            ),
        )
        val encoded = AutomationCodec.encodeDefinitionDocument(definition)
        val decoded = AutomationCodec.decodeImport(encoded).getOrThrow().single()
        assertEquals(definition.conditions.single(), decoded.conditions.single())
        assertEquals(definition.actions.single(), decoded.actions.single())
        assertEquals(
            "set_automation_enabled",
            AutomationBuiltinActionType.SET_AUTOMATION_ENABLED.storageKey,
        )
    }

    @Test
    fun automationEnabled_validatorRequiresIds() {
        val blankCondition = definitionWith(
            conditions = listOf(
                AutomationCondition.AutomationEnabled(automationId = "  ", enabled = true),
            ),
            actions = emptyList(),
        )
        assertTrue(
            AutomationValidator.validate(blankCondition).any { it.path.endsWith("automationId") },
        )

        val blankAction = definitionWith(
            conditions = emptyList(),
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_AUTOMATION_ENABLED,
                    stringValue = "",
                    boolValue = false,
                ),
            ),
        )
        assertTrue(
            AutomationValidator.validate(blankAction).any { it.path.endsWith("stringValue") },
        )

        val ok = definitionWith(
            conditions = listOf(
                AutomationCondition.AutomationEnabled(automationId = "x", enabled = true),
            ),
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_AUTOMATION_ENABLED,
                    stringValue = "x",
                    boolValue = false,
                ),
            ),
        )
        assertTrue(
            AutomationValidator.validate(ok).none {
                it.path.endsWith("automationId") || it.path.endsWith("stringValue")
            },
        )
    }

    private fun evaluateEnabled(condition: AutomationCondition.AutomationEnabled): Boolean =
        AutomationEvaluator.evaluateCondition(
            condition = condition,
            context = AutomationTriggerContext("a", "1", 0L),
            snapshot = emptyMap(),
        )

    private fun definitionWith(
        conditions: List<AutomationCondition>,
        actions: List<AutomationAction>,
    ): AutomationDefinition =
        AutomationDefinition(
            id = "a1",
            name = "test",
            enabled = true,
            triggers = listOf(
                AutomationTrigger.SystemEvent(
                    id = "t1",
                    event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                ),
            ),
            conditions = conditions,
            actions = actions,
        )
}
