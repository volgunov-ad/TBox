package vad.dashing.mqtt.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalPollBatchTest {
    @Test
    fun groupsBySourceAndSplitsAtFifty() {
        val signals = (1..51).map { SignalRef("id$it", "head_unit") } +
            SignalRef("fuel", "tbox") +
            SignalRef("skip", "")
        val batches = batchSignals(signals)
        assertEquals(3, batches.size)
        assertEquals("head_unit", batches[0].source)
        assertEquals(50, batches[0].ids.size)
        assertEquals(listOf("id51"), batches[1].ids)
        assertEquals(listOf("fuel"), batches[2].ids)
    }
}
