package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationGlobalLoopGuardTest {
    @Test
    fun allowsUpToMaxRunsInsideWindowThenRejects() {
        val guard = AutomationGlobalLoopGuard(windowMs = 10_000L, maxRuns = 3)
        assertTrue(guard.tryAcquire(1_000L))
        assertTrue(guard.tryAcquire(2_000L))
        assertTrue(guard.tryAcquire(3_000L))
        assertFalse(guard.tryAcquire(4_000L))
        assertEquals(3, guard.sizeForTests())
    }

    @Test
    fun slidesWindowAndFreesOldestSlots() {
        val guard = AutomationGlobalLoopGuard(windowMs = 10_000L, maxRuns = 2)
        assertTrue(guard.tryAcquire(0L))
        assertTrue(guard.tryAcquire(1_000L))
        assertFalse(guard.tryAcquire(9_999L))
        assertTrue(guard.tryAcquire(10_001L))
        assertEquals(2, guard.sizeForTests())
        assertFalse(guard.tryAcquire(10_500L))
        assertTrue(guard.tryAcquire(11_002L))
    }

    @Test
    fun clear_resetsBudget() {
        val guard = AutomationGlobalLoopGuard(windowMs = 10_000L, maxRuns = 1)
        assertTrue(guard.tryAcquire(0L))
        assertFalse(guard.tryAcquire(1L))
        guard.clear()
        assertTrue(guard.tryAcquire(2L))
    }
}
