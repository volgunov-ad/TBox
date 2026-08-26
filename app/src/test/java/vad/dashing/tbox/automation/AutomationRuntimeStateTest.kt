package vad.dashing.tbox.automation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AutomationRuntimeStateTest {
    @Before
    fun setUp() {
        AutomationRuntimeState.resetForTests()
    }

    @After
    fun tearDown() {
        AutomationRuntimeState.resetForTests()
    }

    @Test
    fun overlappingRuns_stayRunningUntilLastFinishes() {
        AutomationRuntimeState.markStarted("a", "rpm", 1L)
        AutomationRuntimeState.markStarted("a", "menu", 2L)
        assertEquals(AutomationExecutionState.RUNNING, status().state)
        assertEquals(2, status().activeRuns)

        AutomationRuntimeState.markFinished("a", success = false, message = "fail", 3L)
        assertEquals(AutomationExecutionState.RUNNING, status().state)
        assertEquals(1, status().activeRuns)

        AutomationRuntimeState.markFinished("a", success = true, message = "ok", 4L)
        assertEquals(AutomationExecutionState.SUCCESS, status().state)
        assertEquals(0, status().activeRuns)
        assertEquals("ok", status().lastMessage)
    }

    @Test
    fun retainAutomationIds_dropsRemovedRules() {
        AutomationRuntimeState.markStarted("keep", "svc", 1L)
        AutomationRuntimeState.markStarted("drop", "svc", 1L)
        AutomationRuntimeState.retainAutomationIds(setOf("keep"))
        assertEquals(setOf("keep"), AutomationRuntimeState.statuses.value.keys)
    }

    private fun status(): AutomationRuntimeStatus =
        requireNotNull(AutomationRuntimeState.statuses.value["a"])
}
