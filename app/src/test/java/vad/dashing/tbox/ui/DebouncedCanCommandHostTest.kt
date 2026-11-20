package vad.dashing.tbox.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedCanCommandHostTest {

    @Test
    fun schedule_resetsTimerAndCommitsOnceAfterIdle() = runTest {
        var commits = 0
        val host = DebouncedCanCommandHost(
            debounceMs = 800L,
            onCommit = { commits += 1 },
        )

        host.schedule(backgroundScope)
        runCurrent()
        advanceTimeBy(400)
        assertEquals(0, commits)
        assertTrue(host.isPending)

        host.schedule(backgroundScope)
        runCurrent()
        advanceTimeBy(799)
        assertEquals(0, commits)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, commits)
        assertFalse(host.isPending)
    }

    @Test
    fun cancel_dropsArmedCommit() = runTest {
        var commits = 0
        val host = DebouncedCanCommandHost(
            debounceMs = 800L,
            onCommit = { commits += 1 },
        )

        host.schedule(backgroundScope)
        runCurrent()
        advanceTimeBy(400)
        host.cancel()
        assertFalse(host.isPending)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, commits)
    }

    @Test
    fun flush_commitsImmediatelyAndClearsPending() = runTest {
        var commits = 0
        val host = DebouncedCanCommandHost(
            debounceMs = 800L,
            onCommit = { commits += 1 },
        )

        host.schedule(backgroundScope)
        runCurrent()
        advanceTimeBy(100)
        host.flush()
        assertEquals(1, commits)
        assertFalse(host.isPending)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, commits)
    }

    @Test
    fun shadeRoofConstant_isEightHundredMs() {
        assertEquals(800L, SHADE_ROOF_POSITION_DEBOUNCE_MS)
    }
}
