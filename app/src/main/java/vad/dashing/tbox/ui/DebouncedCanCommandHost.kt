package vad.dashing.tbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val HVAC_BLOW_MODE_DEBOUNCE_MS = 1_000L

class DebouncedCanCommandHost(
    private val debounceMs: Long,
    private val onCommit: suspend () -> Unit,
) {
    private var pendingJob: Job? = null

    fun schedule(scope: kotlinx.coroutines.CoroutineScope) {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(debounceMs)
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
    val scope = rememberCoroutineScope()
    val host = remember(debounceMs, onCommit) {
        DebouncedCanCommandHost(debounceMs, onCommit)
    }
    DisposableEffect(host) {
        onDispose { host.cancel() }
    }
    return host
}
