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

    @Test
    fun canStartupReadiness_waitsUntilAvailable() = runBlocking {
        var now = 0L
        val ready = awaitCanBackendReadyForAction(
            isAvailable = { now >= 750L },
            nowElapsedMillis = { now },
            delayFor = { now += it },
            timeoutMs = 15_000L,
            pollMs = 250L,
        )
        assertTrue(ready)
        assertEquals(750L, now)
    }

    @Test
    fun canStartupRetry_retriesTransientUnavailableAndPreReadFailures() = runBlocking {
        var now = 0L
        var attempts = 0
        val outcomes = listOf(
            vad.dashing.tbox.mbcan.MbCanCommandResult(false, "mbCAN unavailable"),
            vad.dashing.tbox.mbcan.MbCanCommandResult(false, "Pre-read failed"),
            vad.dashing.tbox.mbcan.MbCanCommandResult(true, "Set result: 0"),
        )
        val result = executeCanWithStartupRetry(
            execute = {
                val next = outcomes[attempts.coerceAtMost(outcomes.lastIndex)]
                attempts += 1
                next
            },
            nowElapsedMillis = { now },
            delayFor = { now += it },
            retryWindowMs = 12_000L,
            retryDelayMs = 500L,
        )
        assertTrue(result.success)
        assertEquals(3, attempts)
        assertEquals(1_000L, now)
    }

    @Test
    fun canStartupRetry_doesNotRetryValidationOrPolicyErrors() = runBlocking {
        var now = 0L
        var attempts = 0
        val result = executeCanWithStartupRetry(
            execute = {
                attempts += 1
                vad.dashing.tbox.mbcan.MbCanCommandResult(false, "Value 99 is not allowed")
            },
            nowElapsedMillis = { now },
            delayFor = { now += it },
            retryWindowMs = 12_000L,
            retryDelayMs = 500L,
        )
        assertFalse(result.success)
        assertEquals(1, attempts)
        assertEquals(0L, now)
    }

    @Test
    fun canStartupRetry_capsRetriesOfRejectedWrites() = runBlocking {
        var now = 0L
        var attempts = 0
        val result = executeCanWithStartupRetry(
            execute = {
                attempts += 1
                vad.dashing.tbox.mbcan.MbCanCommandResult(false, "Set failed")
            },
            nowElapsedMillis = { now },
            delayFor = { now += it },
            retryWindowMs = 12_000L,
            retryDelayMs = 500L,
            maxWriteFailureRetries = 2,
        )
        assertFalse(result.success)
        assertEquals(3, attempts)
        assertEquals(1_000L, now)
    }

    @Test
    fun canStartupRetry_keepsRetryingWhileBackendNotReadyThenCapsWrites() = runBlocking {
        var now = 0L
        var attempts = 0
        val result = executeCanWithStartupRetry(
            execute = {
                attempts += 1
                if (now < 3_000L) {
                    vad.dashing.tbox.mbcan.MbCanCommandResult(false, "mbCAN unavailable")
                } else {
                    vad.dashing.tbox.mbcan.MbCanCommandResult(false, "Set command failed")
                }
            },
            nowElapsedMillis = { now },
            delayFor = { now += it },
            retryWindowMs = 12_000L,
            retryDelayMs = 500L,
            maxWriteFailureRetries = 1,
        )
        assertFalse(result.success)
        assertEquals(8, attempts)
        assertEquals(3_500L, now)
    }

    @Test
    fun canStartupRetry_zeroWindowRunsOnce() = runBlocking {
        var now = 0L
        var attempts = 0
        val result = executeCanWithStartupRetry(
            execute = {
                attempts += 1
                vad.dashing.tbox.mbcan.MbCanCommandResult(false, "mbCAN unavailable")
            },
            nowElapsedMillis = { now },
            delayFor = { now += it },
            retryWindowMs = 0L,
        )
        assertFalse(result.success)
        assertEquals(1, attempts)
        assertEquals(0L, now)
    }
}
