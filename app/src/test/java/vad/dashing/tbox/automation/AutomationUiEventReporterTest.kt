package vad.dashing.tbox.automation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AutomationUiEventReporterTest {
    @Before
    fun reset() {
        AutomationSystemEventBus.resetForTests()
        AutomationUiEventReporter.resetForTests()
    }

    @After
    fun cleanup() {
        AutomationSystemEventBus.resetForTests()
        AutomationUiEventReporter.resetForTests()
    }

    @Test
    fun screenWhileBackground_doesNotPublish() {
        AutomationUiEventReporter.reportScreen(AutomationVisibleScreen.MAIN)
        assertEquals(0L, AutomationSystemEventBus.currentSequence())
    }

    @Test
    fun resumePublishesCurrentScreen() = runBlocking {
        AutomationUiEventReporter.reportScreen(AutomationVisibleScreen.MAIN)
        AutomationUiEventReporter.onMainActivityResumed()

        val event = withTimeout(1_000L) {
            AutomationSystemEventBus.eventsAfter(0L).first()
        }
        assertEquals(AutomationSystemEvent.MAIN_SCREEN_OPENED, event.event)
    }

    @Test
    fun sameScreenWhileForeground_isNotRepublished() {
        AutomationUiEventReporter.reportScreen(AutomationVisibleScreen.MENU)
        AutomationUiEventReporter.onMainActivityResumed()
        val afterResume = AutomationSystemEventBus.currentSequence()

        AutomationUiEventReporter.reportScreen(AutomationVisibleScreen.MENU)
        assertEquals(afterResume, AutomationSystemEventBus.currentSequence())
    }

    @Test
    fun switchingToMenu_publishesMenuOpened() = runBlocking {
        AutomationUiEventReporter.reportScreen(AutomationVisibleScreen.MAIN)
        AutomationUiEventReporter.onMainActivityResumed()
        val afterMain = AutomationSystemEventBus.currentSequence()

        AutomationUiEventReporter.reportScreen(AutomationVisibleScreen.MENU)
        val event = withTimeout(1_000L) {
            AutomationSystemEventBus.eventsAfter(afterMain).first()
        }
        assertEquals(AutomationSystemEvent.MENU_OPENED, event.event)
    }

    @Test
    fun pauseThenResume_republishesCurrentScreen() = runBlocking {
        AutomationUiEventReporter.reportScreen(AutomationVisibleScreen.MAIN)
        AutomationUiEventReporter.onMainActivityResumed()
        val afterFirst = AutomationSystemEventBus.currentSequence()

        AutomationUiEventReporter.onMainActivityPaused()
        AutomationUiEventReporter.onMainActivityResumed()
        val event = withTimeout(1_000L) {
            AutomationSystemEventBus.eventsAfter(afterFirst).first()
        }
        assertEquals(AutomationSystemEvent.MAIN_SCREEN_OPENED, event.event)
    }
}
