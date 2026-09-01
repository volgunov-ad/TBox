package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Device-smoke contract: HU fixtures use [AutomationStartupBehavior.INITIALIZE_ONLY] so importing
 * a backup while Settings/Monitor is already visible does not immediately toggle overlays.
 */
class HuForegroundAppAutomationTest {
    @Test
    fun initializeOnly_doesNotFireWhenExpectedAppIsAlreadyForeground() {
        val evaluator = evaluator(expectedPackage = "com.android.settings", allowStartupFire = true)
        assertNull(evaluator.onSignalSample(fg("com.android.settings", 0L)))
        assertNull(evaluator.onSignalSample(fg("com.android.settings", 1_000L)))
    }

    @Test
    fun initializeOnly_firesAfterLeavingAndReenteringExpectedApp() {
        val evaluator = evaluator(expectedPackage = "com.android.settings", allowStartupFire = true)
        assertNull(evaluator.onSignalSample(fg("com.android.settings", 0L)))
        assertNull(evaluator.onSignalSample(fg("vad.dashing.tbox", 500L)))
        assertEquals(
            "1",
            evaluator.onSignalSample(fg("com.android.settings", 1_000L))?.triggerId,
        )
    }

    @Test
    fun monitorRule_firesWhenReturningFromSettings() {
        val evaluator = evaluator(expectedPackage = "vad.dashing.tbox", allowStartupFire = true)
        assertNull(evaluator.onSignalSample(fg("vad.dashing.tbox", 0L)))
        assertNull(evaluator.onSignalSample(fg("com.android.settings", 500L)))
        assertEquals(
            "1",
            evaluator.onSignalSample(fg("vad.dashing.tbox", 1_000L))?.triggerId,
        )
    }

    @Test
    fun definitionUpdate_neverFiresOnCurrentForeground() {
        val evaluator = evaluator(expectedPackage = "com.android.settings", allowStartupFire = false)
        assertNull(evaluator.onSignalSample(fg("com.android.settings", 0L)))
        assertNull(evaluator.onSignalSample(fg("com.android.settings", 50L)))
        assertNull(evaluator.onSignalSample(fg("vad.dashing.tbox", 100L)))
        assertEquals(
            "1",
            evaluator.onSignalSample(fg("com.android.settings", 150L))?.triggerId,
        )
    }

    private fun evaluator(
        expectedPackage: String,
        allowStartupFire: Boolean,
    ): AutomationEvaluator = AutomationEvaluator(
        definition = AutomationDefinition(
            id = "hu-test",
            name = "HU test",
            enabled = true,
            triggers = listOf(
                AutomationTrigger.StateEquals(
                    id = "1",
                    signal = AutomationSignalId.FOREGROUND_APP,
                    source = AutomationSignalSource.APP,
                    expectedState = expectedPackage,
                    holdMillis = 0L,
                    startupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
                ),
            ),
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SHOW_TOAST,
                    stringValue = "HUTEST",
                ),
            ),
            runMode = AutomationRunMode.RESTART,
            maxRuns = 10,
        ),
        allowStartupFire = allowStartupFire,
    )

    private fun fg(packageName: String, elapsedMillis: Long): AutomationSignalSample =
        AutomationSignalSample(
            key = AutomationSignalKey(
                AutomationSignalId.FOREGROUND_APP,
                AutomationSignalSource.APP,
            ),
            value = AutomationSignalValue.State(packageName),
            observedAtElapsedMillis = elapsedMillis,
        )
}
