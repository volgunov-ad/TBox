package vad.dashing.tbox.automation

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationHardKeyForwarderTest {

    @Test
    fun onHardKey_debouncesRepeatedEventsPerCodeAndStatus() = runBlocking {
        var now = 0L
        val forwarder = AutomationHardKeyForwarder(
            debounceMillis = 120L,
            nowMillis = { now },
        )
        val received = mutableListOf<AutomationHardKeyEvent>()
        val job = launch { AutomationTriggerHardKeyEventBus.events.collect { received += it } }
        yield()

        forwarder.onHardKey(115, 0)
        now = 50L
        forwarder.onHardKey(115, 0)
        now = 100L
        forwarder.onHardKey(115, 1)
        now = 130L
        forwarder.onHardKey(115, 0)
        now = 300L
        forwarder.onHardKey(115, 0)
        forwarder.onHardKey(210, 0)
        forwarder.onHardKey(115, 2)

        yield()
        job.cancel()
        assertEquals(
            listOf(
                AutomationHardKeyEvent(115, AutomationHardKeyStatus.PRESSED),
                AutomationHardKeyEvent(115, AutomationHardKeyStatus.RELEASED),
                AutomationHardKeyEvent(115, AutomationHardKeyStatus.PRESSED),
                AutomationHardKeyEvent(115, AutomationHardKeyStatus.PRESSED),
                AutomationHardKeyEvent(210, AutomationHardKeyStatus.PRESSED),
            ),
            received,
        )
    }
}
