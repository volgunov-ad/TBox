package vad.dashing.tbox

/**
 * Delay before the first [FloatingOverlayController.ensureFloatingDashboards] after
 * [BackgroundService] starts its periodic job (cold start / overlay permission race).
 * Replaces the former hardcoded 5 s pause.
 */
internal object FloatingPanelsShowOnServiceStartDelay {
    const val DEFAULT_SECONDS = 3
    const val MIN_SECONDS = 0
    const val MAX_SECONDS = 60

    fun coerceSeconds(raw: Int): Int = raw.coerceIn(MIN_SECONDS, MAX_SECONDS)

    /** Milliseconds to [kotlinx.coroutines.delay] before the initial ensure; 0 = no wait. */
    fun delayMs(seconds: Int): Long = coerceSeconds(seconds).toLong() * 1000L
}
