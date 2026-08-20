package vad.dashing.tbox.vehicle

/** Raw ESP pulse counters: LHF / RHF / LHR / RHR. */
data class WheelCounters(
    val lhf: Int,
    val rhf: Int,
    val lhr: Int,
    val rhr: Int,
    val updatedElapsedMs: Long = 0L,
) {
    fun withCorner(index: Int, value: Int): WheelCounters = when (index) {
        0 -> copy(lhf = value)
        1 -> copy(rhf = value)
        2 -> copy(lhr = value)
        3 -> copy(rhr = value)
        else -> this
    }
}
