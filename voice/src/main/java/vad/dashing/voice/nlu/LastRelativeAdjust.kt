package vad.dashing.voice.nlu

import vad.dashing.voice.api.CatalogAction

/**
 * Short memory for «ещё» / «ещё раз» after a relative slot invoke (потеплее, тише, ярче…).
 */
data class LastRelativeAdjust(
    val action: CatalogAction,
    val steps: Int,
    val savedAtElapsedMs: Long,
) {
    fun isFresh(nowElapsedMs: Long, ttlMs: Long = TTL_MS): Boolean =
        nowElapsedMs - savedAtElapsedMs in 0 until ttlMs

    companion object {
        const val TTL_MS = 60_000L
    }
}
