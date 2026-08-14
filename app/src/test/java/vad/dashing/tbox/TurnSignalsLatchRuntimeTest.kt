package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.TurnSignalSide
import vad.dashing.tbox.mbcan.TurnSignalsLatch
import vad.dashing.tbox.mbcan.TurnSignalsLatchRuntime
import vad.dashing.tbox.mbcan.TurnSignalsState

class TurnSignalsLatchRuntimeTest {
    private var now = 0L
    private val runtime = TurnSignalsLatchRuntime(elapsedRealtimeMs = { now })
    private val off = TurnSignalsState(
        leftActive = false,
        rightActive = false,
        hazardActive = false,
    )
    private val right = TurnSignalsState(
        leftActive = false,
        rightActive = true,
        hazardActive = false,
    )
    private val left = TurnSignalsState(
        leftActive = true,
        rightActive = false,
        hazardActive = false,
    )
    private val hazard = TurnSignalsState(
        leftActive = true,
        rightActive = true,
        hazardActive = true,
    )

    @Test
    fun pollRetriggersWhileRawOnThenExpiresAfterOff() {
        runtime.ingest(right)
        assertEquals(TurnSignalSide.Right, runtime.side.value)
        now = 5_000L
        runtime.poll()
        assertEquals(TurnSignalSide.Right, runtime.side.value)
        runtime.ingest(off)
        assertEquals(TurnSignalSide.Right, runtime.side.value)
        now = 5_000L + TurnSignalsLatch.HOLD_MS
        runtime.poll()
        assertEquals(TurnSignalSide.Right, runtime.side.value)
        now += 1L
        runtime.poll()
        assertNull(runtime.side.value)
        assertNull(runtime.peek())
    }

    @Test
    fun peekDoesNotRetriggerButPollDoesWhileRawOn() {
        runtime.ingest(right)
        now = TurnSignalsLatch.HOLD_MS + 1L
        assertNull("peek is a read; last flash was at 0", runtime.peek())
        assertEquals(TurnSignalSide.Right, runtime.side.value)
        runtime.poll()
        assertEquals(
            "poll retriggers A10 while the stalk sample is still true",
            TurnSignalSide.Right,
            runtime.side.value,
        )
        assertEquals(TurnSignalSide.Right, runtime.peek())
    }

    @Test
    fun ingestOppositeClearsPublishedSide() {
        runtime.ingest(right)
        now = 800L
        runtime.poll()
        assertEquals(TurnSignalSide.Right, runtime.side.value)
        runtime.ingest(left)
        assertEquals(TurnSignalSide.Left, runtime.side.value)
        assertEquals(TurnSignalSide.Left, runtime.peek())
    }

    @Test
    fun ingestHazardClearsPublishedSide() {
        runtime.ingest(right)
        runtime.ingest(hazard)
        assertNull(runtime.side.value)
        runtime.ingest(off)
        now = 1_000L
        runtime.poll()
        assertNull(runtime.side.value)
    }

    @Test
    fun resetClearsHoldAndLastSample() {
        runtime.ingest(right)
        runtime.reset()
        assertNull(runtime.side.value)
        now = 100L
        runtime.poll()
        assertNull(runtime.side.value)
        assertNull(runtime.peek())
    }

    @Test
    fun pollNoopsWhenIdle() {
        now = 10_000L
        runtime.poll()
        assertNull(runtime.side.value)
    }
}
