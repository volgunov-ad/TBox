package vad.dashing.tbox.mbcan

/**
 * When a Compose panel leaves composition, [enqueueClearSource] must outlive
 * [androidx.compose.runtime.DisposableEffect] (its coroutine is cancelled with the composition).
 * A short debounce absorbs tab remounts; it must not keep OEM push/poll for minutes.
 */
object CanInterestClear {
    const val UI_DISPOSE_DEBOUNCE_MS = 2_000L
}
