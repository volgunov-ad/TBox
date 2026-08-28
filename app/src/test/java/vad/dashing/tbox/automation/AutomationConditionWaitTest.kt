package vad.dashing.tbox.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationConditionWaitTest {
    @Test
    fun zeroWait_skipsWhenNotReady() = runBlocking {
        var now = 0L
        val ready = awaitAutomationConditionWindow(
            waitMillis = 0L,
            isReady = { false },
            nowElapsedMillis = { now },
            delayFor = { now += it },
        )
        assertFalse(ready)
        assertEquals(0L, now)
    }

    @Test
    fun alreadyReady_doesNotWait() = runBlocking {
        var now = 0L
        val ready = awaitAutomationConditionWindow(
            waitMillis = 5_000L,
            isReady = { true },
            nowElapsedMillis = { now },
            delayFor = { now += it },
        )
        assertTrue(ready)
        assertEquals(0L, now)
    }

    @Test
    fun becomesReadyBeforeTimeout_succeeds() = runBlocking {
        var now = 0L
        val ready = awaitAutomationConditionWindow(
            waitMillis = 1_000L,
            isReady = { now >= 400L },
            nowElapsedMillis = { now },
            delayFor = { now += it },
            pollMillis = 250L,
        )
        assertTrue(ready)
        assertEquals(500L, now)
    }

    @Test
    fun staysUnreadyUntilDeadline_failsWithoutEarlyAbort() = runBlocking {
        var now = 0L
        var checks = 0
        val ready = awaitAutomationConditionWindow(
            waitMillis = 1_000L,
            isReady = {
                checks += 1
                false
            },
            nowElapsedMillis = { now },
            delayFor = { now += it },
            pollMillis = 250L,
        )
        assertFalse(ready)
        assertTrue("should keep polling until timeout, checks=$checks", checks >= 4)
        assertEquals(1_000L, now)
    }

    @Test
    fun unreadyThenReadyAgain_stillSucceeds() = runBlocking {
        var now = 0L
        val ready = awaitAutomationConditionWindow(
            waitMillis = 1_000L,
            isReady = { now in 600L..800L },
            nowElapsedMillis = { now },
            delayFor = { now += it },
            pollMillis = 250L,
        )
        assertTrue(ready)
        assertEquals(750L, now)
    }
}
