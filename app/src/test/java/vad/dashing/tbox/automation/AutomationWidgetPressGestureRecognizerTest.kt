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

class AutomationWidgetPressGestureRecognizerTest {

    @Test
    fun singleTap_emitsSingleAfterDoubleWindow() = runBlocking {
        withRecognizer { harness ->
            harness.onTap("btn1")
            harness.advance(299L)
            assertEquals(emptyList<AutomationWidgetPressKind>(), harness.emittedKinds())
            harness.advance(1L)
            assertEquals(
                listOf("btn1" to AutomationWidgetPressKind.SINGLE),
                harness.emitted,
            )
        }
    }

    @Test
    fun twoTapsWithinWindow_emitDoubleNotSingle() = runBlocking {
        withRecognizer { harness ->
            harness.onTap("btn1")
            harness.advance(100L)
            harness.onTap("btn1")
            harness.advance(500L)
            assertEquals(
                listOf("btn1" to AutomationWidgetPressKind.DOUBLE),
                harness.emitted,
            )
        }
    }

    @Test
    fun differentTriggerIds_areIndependent() = runBlocking {
        withRecognizer { harness ->
            harness.onTap("a")
            harness.advance(50L)
            harness.onTap("b")
            harness.advance(300L)
            assertEquals(
                listOf(
                    "a" to AutomationWidgetPressKind.SINGLE,
                    "b" to AutomationWidgetPressKind.SINGLE,
                ),
                harness.emitted,
            )
        }
    }

    private class Harness(
        private val scope: CoroutineScope,
    ) {
        var now = 0L
        val emitted = mutableListOf<Pair<String, AutomationWidgetPressKind>>()
        private val pendingDelays = mutableListOf<PendingDelay>()

        private data class PendingDelay(
            val dueAt: Long,
            val gate: CompletableDeferred<Unit>,
        )

        val recognizer = AutomationWidgetPressGestureRecognizer(
            scope = scope,
            doubleTapMillis = 300L,
            delayMillis = { millis ->
                val gate = CompletableDeferred<Unit>()
                pendingDelays += PendingDelay(dueAt = now + millis, gate = gate)
                gate.await()
            },
            publish = { triggerId, kind -> emitted += triggerId to kind },
        )

        fun onTap(triggerId: String) {
            recognizer.onTap(triggerId)
        }

        suspend fun advance(deltaMillis: Long) {
            now += deltaMillis
            val due = pendingDelays.filter { it.dueAt <= now }.sortedBy { it.dueAt }
            pendingDelays.removeAll(due.toSet())
            due.forEach { it.gate.complete(Unit) }
            yield()
            var guard = 0
            while (guard++ < 16) {
                val nested = pendingDelays.filter { it.dueAt <= now }.sortedBy { it.dueAt }
                if (nested.isEmpty()) break
                pendingDelays.removeAll(nested.toSet())
                nested.forEach { it.gate.complete(Unit) }
                yield()
            }
        }

        fun emittedKinds(): List<AutomationWidgetPressKind> = emitted.map { it.second }
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
