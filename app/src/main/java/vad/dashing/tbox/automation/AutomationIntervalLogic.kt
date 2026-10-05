package vad.dashing.tbox.automation

/**
 * Fixed-phase interval schedule based on monotonic elapsed time.
 *
 * Boundary zero is the service start itself and never fires. The first due boundary is one full
 * interval later. Returning only the latest boundary coalesces delays without producing a burst.
 */
object AutomationIntervalLogic {
    fun elapsedBoundaryCount(
        anchorElapsedMillis: Long,
        intervalMillis: Long,
        nowElapsedMillis: Long,
    ): Long {
        require(intervalMillis > 0L) { "intervalMillis must be positive" }
        val elapsed = nowElapsedMillis - anchorElapsedMillis
        return if (elapsed <= 0L) 0L else elapsed / intervalMillis
    }

    fun boundaryElapsedMillis(
        anchorElapsedMillis: Long,
        intervalMillis: Long,
        boundaryIndex: Long,
    ): Long {
        require(intervalMillis > 0L) { "intervalMillis must be positive" }
        require(boundaryIndex >= 0L) { "boundaryIndex must not be negative" }
        return anchorElapsedMillis + intervalMillis * boundaryIndex
    }
}
