package vad.dashing.tbox.um980fw

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI progress for UM980 `.pkg` update (USB or Companion). */
object Um980FirmwareUiStore {
    data class State(
        val active: Boolean = false,
        val progressPct: Int = 0,
        val phase: String = "",
        val error: String? = null,
        val doneOk: Boolean = false,
        /** Hard reset: waiting for user to power-cycle before continue. */
        val awaitingHardReset: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    var hardResetContinue: (() -> Unit)? = null

    fun begin() {
        hardResetContinue = null
        _state.value = State(active = true, phase = "start")
    }

    fun setPhase(phase: String, progressPct: Int = _state.value.progressPct) {
        _state.value = _state.value.copy(
            phase = phase,
            progressPct = progressPct.coerceIn(0, 100),
            awaitingHardReset = false,
        )
    }

    fun setProgress(pct: Int) {
        _state.value = _state.value.copy(progressPct = pct.coerceIn(0, 100))
    }

    fun awaitHardReset(onContinue: () -> Unit) {
        hardResetContinue = onContinue
        _state.value = _state.value.copy(awaitingHardReset = true, phase = "hard_reset")
    }

    fun userContinuedHardReset() {
        val cb = hardResetContinue
        hardResetContinue = null
        _state.value = _state.value.copy(awaitingHardReset = false)
        cb?.invoke()
    }

    fun finish(error: String?) {
        hardResetContinue = null
        _state.value = State(
            active = false,
            progressPct = if (error == null) 100 else _state.value.progressPct,
            phase = if (error == null) "done" else "error",
            error = error,
            doneOk = error == null,
            awaitingHardReset = false,
        )
    }

    fun clearTerminal() {
        hardResetContinue = null
        _state.value = State()
    }
}
