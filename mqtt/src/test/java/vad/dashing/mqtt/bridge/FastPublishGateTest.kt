package vad.dashing.mqtt.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastPublishGateTest {
    @Test
    fun numericChangesWaitForTheIntervalThenSendTheLatest() {
        val gate = FastPublishGate()
        assertTrue(gate.allow("speed", nowMs = 0, intervalMs = 5_000))
        assertFalse(gate.allow("speed", nowMs = 4_999, intervalMs = 5_000))
        assertTrue(gate.allow("speed", nowMs = 5_000, intervalMs = 5_000))
    }

    @Test
    fun zeroIntervalPublishesEveryChange() {
        val gate = FastPublishGate()
        assertTrue(gate.allow("rpm", nowMs = 0, intervalMs = 0))
        assertTrue(gate.allow("rpm", nowMs = 1, intervalMs = 0))
    }

    @Test
    fun droppedEntityDoesNotKeepTheClock() {
        val gate = FastPublishGate()
        assertTrue(gate.allow("speed", nowMs = 0, intervalMs = 5_000))
        gate.retain(emptySet())
        assertTrue(gate.allow("speed", nowMs = 1_000, intervalMs = 5_000))
    }
}
