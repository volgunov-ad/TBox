package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun cancelIfCurrentInvalidatesMatchingTokenOnly() {
        val seq = OverlayOpSequencer()
        val showToken = seq.begin(8)
        assertTrue(seq.isCurrent(8, showToken))
        assertTrue(seq.cancelIfCurrent(8, showToken))
        assertFalse(seq.isCurrent(8, showToken))
        assertEquals(showToken + 1L, seq.current(8))
        assertFalse(seq.cancelIfCurrent(8, showToken))
    }

    @Test
    fun lateShowTimeoutDoesNotCancelNewerShow() {
        // Old show times out after a newer show already began for the same input.
        val seq = OverlayOpSequencer()
        val oldShow = seq.begin(4)
        val newShow = seq.begin(4)
        assertFalse(
            "timeout must not bump when a newer begin already owns the generation",
            seq.cancelIfCurrent(4, oldShow),
        )
        assertTrue(seq.isCurrent(4, newShow))
        assertEquals(newShow, seq.current(4))

        val added = AtomicInteger(0)
        assertTrue(
            seq.runIfCurrent(4, newShow) {
                added.incrementAndGet()
            },
        )
        assertEquals(1, added.get())
        assertFalse(
            seq.runIfCurrent(4, oldShow) {
                added.incrementAndGet()
            },
        )
        assertEquals(1, added.get())
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
    fun showTimeoutCancelPreventsLateAddWhenStillCurrent() {
        val seq = OverlayOpSequencer()
        val showToken = seq.begin(4)
        assertTrue(seq.cancelIfCurrent(4, showToken))
        val added = AtomicInteger(0)
        assertFalse(
            seq.runIfCurrent(4, showToken) {
                added.incrementAndGet()
            },
        )
        assertEquals(0, added.get())
    }

    @Test
    fun lateHideOnStaleTokenReportsSupersededNotSuccess() {
        // Mirrors CameraWindows.hide: stale token → overlay_superseded (not ok=true).
        val seq = OverlayOpSequencer()
        val pending = ArrayDeque<() -> Unit>()
        fun post(block: () -> Unit) {
            pending.addLast(block)
        }
        fun drain() {
            while (pending.isNotEmpty()) pending.removeFirst().invoke()
        }

        val views = mutableSetOf(9)
        val inputId = 9
        var hideOk = true
        var hideError = ""

        val hideToken = seq.begin(inputId)
        post {
            if (!seq.isCurrent(inputId, hideToken)) {
                hideOk = false
                hideError = OverlayOpSequencer.ERROR_SUPERSEDED
                return@post
            }
            views.remove(inputId)
            hideOk = true
        }

        val showToken = seq.begin(inputId)
        post {
            seq.runIfCurrent(inputId, showToken) {
                views.add(inputId)
            }
        }

        drain()
        assertFalse("stale hide must not report success", hideOk)
        assertEquals(OverlayOpSequencer.ERROR_SUPERSEDED, hideError)
        assertEquals(setOf(inputId), views)
        assertTrue(seq.isCurrent(inputId, showToken))
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
    fun delayedMainOldShowTimeoutLeavesNewerShowRunnableCurrent() {
        val seq = OverlayOpSequencer()
        val pending = ArrayDeque<() -> Unit>()
        fun post(block: () -> Unit) {
            pending.addLast(block)
        }
        fun drain() {
            while (pending.isNotEmpty()) pending.removeFirst().invoke()
        }

        val views = mutableSetOf<Int>()
        val inputId = 8

        val oldShow = seq.begin(inputId)
        post {
            seq.runIfCurrent(inputId, oldShow) {
                views.add(inputId)
            }
        }

        // API thread: old show latch timed out, then a new OPEN began.
        val newShow = seq.begin(inputId)
        post {
            seq.runIfCurrent(inputId, newShow) {
                views.add(inputId)
            }
        }
        assertFalse(seq.cancelIfCurrent(inputId, oldShow))

        drain()
        assertEquals(setOf(inputId), views)
        assertTrue(seq.isCurrent(inputId, newShow))
    }

    @Test
    fun overlayFailureReasonNeverNullOrBlank() {
        assertEquals(OverlayOpSequencer.ERROR_SUPERSEDED, overlayFailureReason(null))
        assertEquals(OverlayOpSequencer.ERROR_SUPERSEDED, overlayFailureReason(""))
        assertEquals(OverlayOpSequencer.ERROR_SUPERSEDED, overlayFailureReason("   "))
        assertEquals(OverlayOpSequencer.ERROR_SUPERSEDED, overlayFailureReason(OverlayOpSequencer.ERROR_SUPERSEDED))
        assertEquals(OverlayOpSequencer.ERROR_TIMEOUT, overlayFailureReason(OverlayOpSequencer.ERROR_TIMEOUT))
        assertEquals("overlay", overlayFailureReason("overlay"))

        val reply = "state=error error=${overlayFailureReason(null)}"
        assertFalse(reply.contains("error=null"))
        assertNotEquals("state=error error=", reply)
        assertTrue(reply.endsWith("error=${OverlayOpSequencer.ERROR_SUPERSEDED}"))
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
