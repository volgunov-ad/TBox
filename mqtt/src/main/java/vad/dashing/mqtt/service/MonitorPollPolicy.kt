package vad.dashing.mqtt.service

/**
 * After /v1/health succeeded, a few bad catalog or signal polls keep the last values.
 * A streak or a long gap marks Monitor down so Home Assistant does not stay online on frozen data.
 */
object MonitorPollPolicy {
    const val FAIL_LIMIT = 3
    const val FAIL_WINDOW_MS = 30_000L

    fun shouldMarkDown(streak: Int, elapsedMs: Long): Boolean =
        streak >= FAIL_LIMIT || (streak > 0 && elapsedMs >= FAIL_WINDOW_MS)
}
