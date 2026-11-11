package vad.dashing.tbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val HVAC_BLOW_MODE_DEBOUNCE_MS = 1_000L

/** Idle window before shade / sunroof position steppers send a BodyComfort write. */
const val SHADE_ROOF_POSITION_DEBOUNCE_MS = 800L

/**
 * Schedules [onCommit] after [debounceMs] of idle time; each [schedule] resets the timer.
 *
 * [delayMs] is injectable for unit tests (production uses [delay]).
 */
class DebouncedCanCommandHost(
    private val debounceMs: Long,
    private val onCommit: suspend () -> Unit,
    private val delayMs: suspend (Long) -> Unit = { delay(it) },
) {
    private var pendingJob: Job? = null

    /** True while a debounce timer is armed (gesture / pending preview active). */
    val isPending: Boolean
        get() = pendingJob?.isActive == true

    fun schedule(scope: kotlinx.coroutines.CoroutineScope) {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delayMs(debounceMs)
            pendingJob = null
            onCommit()
        }
    }

    fun cancel() {
        pendingJob?.cancel()
        pendingJob = null
    }

    suspend fun flush() {
        pendingJob?.cancel()
        pendingJob = null
        onCommit()
    }
}

@Composable
fun rememberDebouncedCanCommandHost(
    debounceMs: Long,
    onCommit: suspend () -> Unit,
): DebouncedCanCommandHost {
    val onCommitLatest = rememberUpdatedState(onCommit)
    val host = remember(debounceMs) {
        DebouncedCanCommandHost(
            debounceMs = debounceMs,
            onCommit = { onCommitLatest.value() },
        )
    }
    DisposableEffect(host) {
        onDispose { host.cancel() }
    }
    return host
}
