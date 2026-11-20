package vad.dashing.tbox.adb

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalhostAdbBootReadyTest {

    @Before
    fun reset() {
        LocalhostAdbBootReady.resetForTests()
    }

    @Test
    fun runAttempts_stopsEarlyOnSuccess() = runBlocking {
        var tries = 0
        val pauses = mutableListOf<Long>()
        val result = LocalhostAdbBootReady.runAttempts(
            delaysMs = listOf(0L, 5_000L, 10_000L),
            pause = { pauses += it },
            shouldRetry = { it < 0 },
            attempt = {
                tries++
                if (tries == 1) -1 else 42
            },
        )
        assertEquals(42, result)
        assertEquals(2, tries)
        assertEquals(listOf(5_000L), pauses)
    }

    @Test
    fun runAttempts_exhaustsBoundedDelays() = runBlocking {
        var tries = 0
        val result = LocalhostAdbBootReady.runAttempts(
            delaysMs = listOf(0L, 1L, 2L),
            pause = { },
            shouldRetry = { true },
            attempt = {
                tries++
                "fail-$tries"
            },
        )
        assertEquals(3, tries)
        assertEquals("fail-3", result)
    }

    @Test
    fun runSessionAttempts_retriesTcpNotReadyThenOk() = runBlocking {
        var tries = 0
        val result = LocalhostAdbBootReady.runSessionAttempts(
            delaysMs = listOf(0L, 1L),
            pause = { },
            attempt = {
                tries++
                if (tries == 1) {
                    LocalhostAdbSession.Result.Failed(LocalhostAdbSession.Reason.TcpNotReady)
                } else {
                    LocalhostAdbSession.Result.Ok("ready")
                }
            },
        )
        assertEquals(2, tries)
        assertTrue(result is LocalhostAdbSession.Result.Ok)
        assertEquals("ready", (result as LocalhostAdbSession.Result.Ok).value)
    }

    @Test
    fun bootWindow_gatesAutomationDelays() {
        assertEquals(listOf(0L), LocalhostAdbBootReady.delaysForCurrentWindow(nowElapsedMs = 0L))
        LocalhostAdbBootReady.markServiceReady(1_000L)
        assertTrue(LocalhostAdbBootReady.inBootWindow(nowElapsedMs = 2_000L))
        assertEquals(
            LocalhostAdbBootReady.ATTEMPT_DELAYS_MS,
            LocalhostAdbBootReady.delaysForCurrentWindow(nowElapsedMs = 2_000L),
        )
        assertFalse(
            LocalhostAdbBootReady.inBootWindow(
                nowElapsedMs = 1_000L + LocalhostAdbBootReady.BOOT_RETRY_WINDOW_MS,
            ),
        )
        assertEquals(
            listOf(0L),
            LocalhostAdbBootReady.delaysForCurrentWindow(
                nowElapsedMs = 1_000L + LocalhostAdbBootReady.BOOT_RETRY_WINDOW_MS,
            ),
        )
    }

    @Test
    fun isRetryableAdbErrorCode_matchesSessionReasonNames() {
        assertTrue(LocalhostAdbBootReady.isRetryableAdbErrorCode("TcpNotReady"))
        assertTrue(LocalhostAdbBootReady.isRetryableAdbErrorCode("AdbConnectFailed"))
        assertFalse(LocalhostAdbBootReady.isRetryableAdbErrorCode("band"))
        assertFalse(LocalhostAdbBootReady.isRetryableAdbErrorCode(null))
    }
}
