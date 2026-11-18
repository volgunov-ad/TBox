package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OverlayOpSequencerTest {

    @Test
    fun beginIncrementsPerInputIndependently() {
        val seq = OverlayOpSequencer()
        assertEquals(0L, seq.current(4))
        assertEquals(1L, seq.begin(4))
        assertEquals(2L, seq.begin(4))
        assertEquals(1L, seq.begin(5))
        assertEquals(2L, seq.current(4))
        assertEquals(1L, seq.current(5))
    }

    @Test
    fun cancelInvalidatesPreviousToken() {
        val seq = OverlayOpSequencer()
        val showToken = seq.begin(8)
        assertTrue(seq.isCurrent(8, showToken))
        val cancelled = seq.cancel(8)
        assertFalse(seq.isCurrent(8, showToken))
        assertTrue(seq.isCurrent(8, cancelled))
        assertEquals(cancelled, seq.current(8))
    }

    @Test
    fun closeThenOpenLateHideNoOps() {
        val seq = OverlayOpSequencer()
        val hideToken = seq.begin(9)
        val showToken = seq.begin(9)
        val removed = AtomicInteger(0)
        val added = AtomicInteger(0)

        assertFalse(
            seq.runIfCurrent(9, hideToken) {
                removed.incrementAndGet()
            },
        )
        assertTrue(
            seq.runIfCurrent(9, showToken) {
                added.incrementAndGet()
            },
        )
        assertEquals(0, removed.get())
        assertEquals(1, added.get())
    }

    @Test
    fun showTimeoutCancelPreventsLateAdd() {
        val seq = OverlayOpSequencer()
        val showToken = seq.begin(4)
        // Latch timed out on the API thread — invalidate the posted show.
        seq.cancel(4)
        val added = AtomicInteger(0)
        assertFalse(
            seq.runIfCurrent(4, showToken) {
                added.incrementAndGet()
            },
        )
        assertEquals(0, added.get())
    }

    @Test
    fun delayedMainExecutorCloseOpenDoesNotRemoveNewOverlay() {
        // Fake main: runnables sit until drain(), simulating main-thread jank.
        val seq = OverlayOpSequencer()
        val pending = ArrayDeque<() -> Unit>()
        fun post(block: () -> Unit) {
            pending.addLast(block)
        }
        fun drain() {
            while (pending.isNotEmpty()) pending.removeFirst().invoke()
        }

        val views = mutableSetOf<Int>()
        val inputId = 5

        val hideToken = seq.begin(inputId)
        post {
            seq.runIfCurrent(inputId, hideToken) {
                views.remove(inputId)
            }
        }

        val showToken = seq.begin(inputId)
        post {
            seq.runIfCurrent(inputId, showToken) {
                views.add(inputId)
            }
        }

        // CLOSE returned; OPEN returned; main finally catches up.
        drain()
        assertEquals(setOf(inputId), views)
        assertTrue(seq.isCurrent(inputId, showToken))
        assertFalse(seq.isCurrent(inputId, hideToken))
    }

    @Test
    fun concurrentBeginsStayMonotonic() {
        val seq = OverlayOpSequencer()
        val pool = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(1)
        val tokens = java.util.Collections.synchronizedList(mutableListOf<Long>())
        repeat(4) {
            pool.execute {
                latch.await()
                repeat(50) { tokens.add(seq.begin(1)) }
            }
        }
        latch.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(200, tokens.size)
        assertEquals(200L, seq.current(1))
        assertEquals(200, tokens.toSet().size)
    }
}
