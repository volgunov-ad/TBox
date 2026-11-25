package vad.dashing.camdbench

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BenchPhase { IDLE, STARTING, RUNNING, FINISHING, DONE, ERROR }

data class BenchUi(
    val phase: BenchPhase = BenchPhase.IDLE,
    val message: String = "",
    val plannedSec: Int = 0,
    val elapsedSec: Int = 0,
    val cpuBusyPct: Double = 0.0,
    val camdPct: Double = 0.0,
    val grabPct: Double = 0.0,
    val resultFolder: String? = null,
    val resultHeadline: String = "",
) {
    val busy: Boolean
        get() = phase == BenchPhase.STARTING || phase == BenchPhase.RUNNING || phase == BenchPhase.FINISHING
}

/** Process-wide state shared by the service (writer) and the screen (reader). */
object BenchState {
    private val _ui = MutableStateFlow(BenchUi())
    val ui: StateFlow<BenchUi> = _ui.asStateFlow()

    @Volatile
    var stopRequested = false

    fun update(change: (BenchUi) -> BenchUi) = _ui.update(change)
}
