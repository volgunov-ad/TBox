package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationDispatchGuardTest {
    @Test
    fun cooldown_rejectsSecondLaunchInsideInterval() {
        val guard = AutomationDispatchGuard(minIntervalMs = 2_000L)
        assertTrue(guard.tryAcquire("a", 10_000L))
        assertFalse(guard.tryAcquire("a", 11_999L))
        assertTrue(guard.tryAcquire("a", 12_000L))
    }

    @Test
    fun cooldown_isPerAutomation() {
        val guard = AutomationDispatchGuard(minIntervalMs = 2_000L)
        assertTrue(guard.tryAcquire("a", 0L))
        assertTrue(guard.tryAcquire("b", 100L))
    }

    @Test
    fun consecutiveFailures_disableAfterLimit_andResetOnSuccess() {
        val guard = AutomationDispatchGuard(maxConsecutiveFailures = 3)
        assertFalse(guard.recordOutcome("a", success = false))
        assertFalse(guard.recordOutcome("a", success = false))
        assertFalse(guard.recordOutcome("a", success = true))
        assertEquals(0, guard.consecutiveFailures("a"))
        assertFalse(guard.recordOutcome("a", success = false))
        assertFalse(guard.recordOutcome("a", success = false))
        assertTrue(guard.recordOutcome("a", success = false))
        assertEquals(3, guard.consecutiveFailures("a"))
    }

    @Test
    fun retain_dropsRemovedAutomations() {
        val guard = AutomationDispatchGuard()
        guard.tryAcquire("keep", 0L)
        guard.tryAcquire("drop", 0L)
        guard.retain(setOf("keep"))
        assertTrue(guard.tryAcquire("drop", 100L))
        assertFalse(guard.tryAcquire("keep", 100L))
    }
}
