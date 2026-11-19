package vad.dashing.camd

/**
 * Exponential backoff for the API listen supervisor (P1-2 / P1-5).
 * Pure JVM — unit-testable without sockets.
 */
object CamdApiListenBackoff {
    const val INITIAL_MS = 500L
    const val MAX_MS = 30_000L

    fun next(currentMs: Long, maxMs: Long = MAX_MS): Long =
        (currentMs.coerceAtLeast(1L) * 2L).coerceAtMost(maxMs)
}
