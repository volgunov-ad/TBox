package vad.dashing.tbox.automation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationHardKeyGestureRecognizerTest {

    @Test
    fun shortPress_emitsSingleAfterDoubleWindow() = runBlocking {
        withRecognizer { harness ->
            harness.onRaw(115, AutomationHardKeyStatus.PRESSED)
            harness.advance(80L)
            harness.onRaw(115, AutomationHardKeyStatus.RELEASED)
            harness.advance(399L)
            assertEquals(emptyList<AutomationHardKeyStatus>(), harness.emittedStatuses())
            harness.advance(1L)
            assertEquals(listOf(AutomationHardKeyStatus.SINGLE), harness.emittedStatuses())
        }
    }

    @Test
    fun twoShortPressesWithinWindow_emitDoubleNotSingle() = runBlocking {
        withRecognizer { harness ->
            harness.onRaw(115, AutomationHardKeyStatus.PRESSED)
            harness.advance(50L)
            harness.onRaw(115, AutomationHardKeyStatus.RELEASED)
            harness.advance(100L)
            harness.onRaw(115, AutomationHardKeyStatus.PRESSED)
            harness.advance(50L)
            harness.onRaw(115, AutomationHardKeyStatus.RELEASED)
            harness.advance(500L)
            assertEquals(listOf(AutomationHardKeyStatus.DOUBLE), harness.emittedStatuses())
        }
    }

    @Test
    fun holdPastLongThreshold_emitsLong_noSingleOnRelease() = runBlocking {
        withRecognizer { harness ->
            harness.onRaw(210, AutomationHardKeyStatus.PRESSED)
            harness.advance(500L)
            assertEquals(listOf(AutomationHardKeyStatus.LONG), harness.emittedStatuses())
            harness.onRaw(210, AutomationHardKeyStatus.RELEASED)
            harness.advance(500L)
            assertEquals(listOf(AutomationHardKeyStatus.LONG), harness.emittedStatuses())
        }
    }

    @Test
    fun differentKeyCodes_areIndependent() = runBlocking {
        withRecognizer { harness ->
            harness.onRaw(115, AutomationHardKeyStatus.PRESSED)
            harness.onRaw(210, AutomationHardKeyStatus.PRESSED)
            harness.advance(80L)
            harness.onRaw(115, AutomationHardKeyStatus.RELEASED)
            harness.advance(500L)
            harness.onRaw(210, AutomationHardKeyStatus.RELEASED)
            harness.advance(500L)
            assertEquals(
                listOf(
                    115 to AutomationHardKeyStatus.SINGLE,
                    210 to AutomationHardKeyStatus.LONG,
                ),
                harness.emitted,
            )
        }
    }

    private class Harness(
        private val scope: CoroutineScope,
    ) {
        var now = 0L
        val emitted = mutableListOf<Pair<Int, AutomationHardKeyStatus>>()
        private val pendingDelays = mutableListOf<PendingDelay>()

        private data class PendingDelay(
            val dueAt: Long,
            val gate: CompletableDeferred<Unit>,
        )

        val recognizer = AutomationHardKeyGestureRecognizer(
            scope = scope,
            longPressMillis = 500L,
            doubleTapMillis = 400L,
            nowMillis = { now },
            delayMillis = { millis ->
                val gate = CompletableDeferred<Unit>()
                pendingDelays += PendingDelay(dueAt = now + millis, gate = gate)
                gate.await()
            },
            publish = { keyCode, status -> emitted += keyCode to status },
        )

        fun onRaw(keyCode: Int, status: AutomationHardKeyStatus) {
            recognizer.onRaw(keyCode, status)
        }

        suspend fun advance(deltaMillis: Long) {
            now += deltaMillis
            val due = pendingDelays.filter { it.dueAt <= now }.sortedBy { it.dueAt }
            pendingDelays.removeAll(due.toSet())
            due.forEach { it.gate.complete(Unit) }
            yield()
            // Drain nested delays scheduled by resumed jobs at the same virtual time.
            var guard = 0
            while (guard++ < 16) {
                val nested = pendingDelays.filter { it.dueAt <= now }.sortedBy { it.dueAt }
                if (nested.isEmpty()) break
                pendingDelays.removeAll(nested.toSet())
                nested.forEach { it.gate.complete(Unit) }
                yield()
            }
        }

        fun emittedStatuses(): List<AutomationHardKeyStatus> = emitted.map { it.second }
    }

    private suspend fun withRecognizer(block: suspend (Harness) -> Unit) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        try {
            block(Harness(scope))
        } finally {
            scope.cancel()
        }
    }
}
