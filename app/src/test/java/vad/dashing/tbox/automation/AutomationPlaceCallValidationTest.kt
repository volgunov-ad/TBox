package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.externalapi.ExternalApiActionSafety
import vad.dashing.tbox.externalapi.ExternalApiActionSafetyRules

class AutomationPlaceCallValidationTest {

    @Test
    fun placeCall_requiresNumber() {
        val empty = AutomationAction.Builtin(
            type = AutomationBuiltinActionType.PLACE_CALL,
            stringValue = "",
        )
        val issues = AutomationValidator.validateActions(listOf(empty))
        assertTrue(issues.any { it.path.contains("stringValue") })

        val ok = AutomationAction.Builtin(
            type = AutomationBuiltinActionType.PLACE_CALL,
            stringValue = "+79200287693",
        )
        assertTrue(AutomationValidator.validateActions(listOf(ok)).isEmpty())
    }

    @Test
    fun placeCall_answer_hangup_areConfirmSafety() {
        assertEqualsConfirm(AutomationBuiltinActionType.PLACE_CALL)
        assertEqualsConfirm(AutomationBuiltinActionType.ANSWER_CALL)
        assertEqualsConfirm(AutomationBuiltinActionType.HANGUP_CALL)
    }

    @Test
    fun signalCatalog_containsPhoneSignals() {
        val ids = AutomationSignalCatalog.entries.map { it.id }.toSet()
        assertTrue(AutomationSignalId.PHONE_HFP_CONNECTED in ids)
        assertTrue(AutomationSignalId.PHONE_CALL_STATE in ids)
        assertTrue(AutomationSignalId.PHONE_CALL_DIRECTION in ids)
        assertTrue(AutomationSignalId.PHONE_CALL_NUMBER in ids)
        val state = AutomationSignalCatalog.get(AutomationSignalId.PHONE_CALL_STATE)
        assertTrue(state.sources.contains(AutomationSignalSource.APP))
        assertEquals(
            listOf("idle", "dialing", "active", "incoming", "held", "unknown"),
            state.stateOptions,
        )
    }

    private fun assertEqualsConfirm(type: AutomationBuiltinActionType) {
        val safety = ExternalApiActionSafetyRules.builtinSafety(type)
        assertTrue(safety == ExternalApiActionSafety.CONFIRM)
        assertFalse(ExternalApiActionSafetyRules.isDangerous(AutomationAction.Builtin(type)))
    }
}
