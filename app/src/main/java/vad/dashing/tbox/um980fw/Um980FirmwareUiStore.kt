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
        /** Last RX snippet when [error] is set (baud / banner mismatch). */
        val detail: String? = null,
        val doneOk: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun begin() {
        _state.value = State(active = true, phase = "start")
    }

    fun setPhase(phase: String, progressPct: Int = _state.value.progressPct) {
        _state.value = _state.value.copy(
            phase = phase,
            progressPct = progressPct.coerceIn(0, 100),
        )
    }

    fun setProgress(pct: Int) {
        _state.value = _state.value.copy(progressPct = pct.coerceIn(0, 100))
    }

    /** Keep the error on screen while [Um980FirmwareUpdater] restores the link. */
    fun beginRecover() {
        _state.value = _state.value.copy(active = true, phase = "recover")
    }

    fun endRecover() {
        _state.value = _state.value.copy(active = false)
    }

    fun finish(error: String?, detail: String? = null) {
        _state.value = State(
            active = false,
            progressPct = if (error == null) 100 else _state.value.progressPct,
            phase = if (error == null) "done" else "error",
            error = error,
            detail = detail?.take(160),
            doneOk = error == null,
        )
    }

    fun clearTerminal() {
        _state.value = State()
    }
}
