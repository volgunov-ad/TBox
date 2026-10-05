package vad.dashing.tbox.automation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AutomationSystemEventBusTest {
    @Before
    fun setUp() {
        AutomationSystemEventBus.resetForTests()
    }

    @After
    fun tearDown() {
        AutomationSystemEventBus.resetForTests()
    }

    @Test
    fun eventsAfter_skipsBaselineAndOlder() = runBlocking {
        AutomationSystemEventBus.publish(AutomationSystemEvent.MENU_OPENED)
        val baseline = AutomationSystemEventBus.currentSequence()
        AutomationSystemEventBus.publish(AutomationSystemEvent.BACKGROUND_SERVICE_STARTED)
        AutomationSystemEventBus.publish(AutomationSystemEvent.MAIN_SCREEN_OPENED)

        val first = withTimeout(1_000L) {
            AutomationSystemEventBus.eventsAfter(baseline).first()
        }
        assertEquals(AutomationSystemEvent.BACKGROUND_SERVICE_STARTED, first.event)
        assertEquals(baseline + 1L, first.sequence)
    }

    @Test
    fun publish_incrementsSequenceFromZero() {
        assertEquals(0L, AutomationSystemEventBus.currentSequence())
        AutomationSystemEventBus.publish(AutomationSystemEvent.MENU_OPENED)
        assertEquals(1L, AutomationSystemEventBus.currentSequence())
        AutomationSystemEventBus.publish(AutomationSystemEvent.MAIN_SCREEN_OPENED)
        assertEquals(2L, AutomationSystemEventBus.currentSequence())
    }
}
