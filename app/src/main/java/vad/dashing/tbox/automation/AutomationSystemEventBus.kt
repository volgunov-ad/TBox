package vad.dashing.tbox.automation

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter

data class SequencedAutomationSystemEvent(
    val sequence: Long,
    val event: AutomationSystemEvent,
    val occurredAtEpochMillis: Long,
)

/**
 * Process-wide lifecycle/navigation event stream.
 *
 * Replay prevents UI events that occur during the long service startup pipeline from being lost.
 * Each service session captures a sequence baseline and consumes only newer events.
 */
object AutomationSystemEventBus {
    private const val REPLAY_EVENTS = 64

    private val sequence = AtomicLong(0L)
    private val events = MutableSharedFlow<SequencedAutomationSystemEvent>(
        replay = REPLAY_EVENTS,
        extraBufferCapacity = REPLAY_EVENTS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun currentSequence(): Long = sequence.get()

    fun publish(event: AutomationSystemEvent) {
        events.tryEmit(
            SequencedAutomationSystemEvent(
                sequence = sequence.incrementAndGet(),
                event = event,
                occurredAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun eventsAfter(sequenceExclusive: Long): Flow<SequencedAutomationSystemEvent> =
        events.filter { it.sequence > sequenceExclusive }

    internal fun resetForTests() {
        sequence.set(0L)
        events.resetReplayCache()
    }
}

enum class AutomationVisibleScreen {
    MAIN,
    MENU,
}

/**
 * Joins Activity resume/pause with Compose's selected screen without deriving edges from a late
 * StateFlow subscription.
 */
object AutomationUiEventReporter {
    private val lock = Any()
    private var foreground = false
    private var screen: AutomationVisibleScreen? = null
    private var publishedForResume: AutomationVisibleScreen? = null

    fun reportScreen(displayed: AutomationVisibleScreen) {
        val publish = synchronized(lock) {
            val changed = screen != displayed
            screen = displayed
            if (foreground && (changed || publishedForResume != displayed)) {
                publishedForResume = displayed
                true
            } else {
                false
            }
        }
        if (publish) publish(displayed)
    }

    fun onMainActivityResumed() {
        val toPublish = synchronized(lock) {
            foreground = true
            publishedForResume = screen
            screen
        }
        toPublish?.let(::publish)
    }

    fun onMainActivityPaused() {
        synchronized(lock) {
            foreground = false
            publishedForResume = null
        }
    }

    private fun publish(screen: AutomationVisibleScreen) {
        AutomationSystemEventBus.publish(
            when (screen) {
                AutomationVisibleScreen.MAIN -> AutomationSystemEvent.MAIN_SCREEN_OPENED
                AutomationVisibleScreen.MENU -> AutomationSystemEvent.MENU_OPENED
            },
        )
    }

    internal fun resetForTests() {
        synchronized(lock) {
            foreground = false
            screen = null
            publishedForResume = null
        }
    }
}
