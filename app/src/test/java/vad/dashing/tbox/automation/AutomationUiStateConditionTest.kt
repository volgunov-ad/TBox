package vad.dashing.tbox.automation

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutomationUiStateConditionTest {
    @Before
    fun reset() {
        AutomationUiEventReporter.resetForTests()
        AutomationUiSnapshot.resetForTests()
    }

    @After
    fun cleanup() {
        AutomationUiEventReporter.resetForTests()
        AutomationUiSnapshot.resetForTests()
    }

    @Test
    fun serviceRunning_reflectsSnapshot() {
        AutomationUiSnapshot.setServiceRunning(true)
        assertTrue(evaluate(AutomationUiState.SERVICE_RUNNING))
        AutomationUiSnapshot.setServiceRunning(false)
        assertFalse(evaluate(AutomationUiState.SERVICE_RUNNING))
    }

    @Test
    fun mainScreenOpen_requiresForegroundMain() {
        AutomationUiEventReporter.reportScreen(AutomationVisibleScreen.MAIN)
        assertFalse(evaluate(AutomationUiState.MAIN_SCREEN_OPEN))
        AutomationUiEventReporter.onMainActivityResumed()
        assertTrue(evaluate(AutomationUiState.MAIN_SCREEN_OPEN))
    }

    @Test
    fun menuOpen_requiresForegroundMenu() {
        AutomationUiEventReporter.reportScreen(AutomationVisibleScreen.MENU)
        AutomationUiEventReporter.onMainActivityResumed()
        assertTrue(evaluate(AutomationUiState.MENU_OPEN))
        assertFalse(evaluate(AutomationUiState.MAIN_SCREEN_OPEN))
    }

    private fun evaluate(state: AutomationUiState): Boolean =
        AutomationEvaluator.evaluateCondition(
            condition = AutomationCondition.UiState(state),
            context = AutomationTriggerContext("a", "1", 0L),
            snapshot = emptyMap(),
        )
}
