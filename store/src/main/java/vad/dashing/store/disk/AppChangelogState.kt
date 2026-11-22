package vad.dashing.store.disk

/** Lazy-loaded per-app changelog text from the AppStore share. */
sealed interface AppChangelogState {
    data object Idle : AppChangelogState
    data object Loading : AppChangelogState
    data class Ready(
        val text: String,
        /** `ru` / `en` of the file that was used. */
        val lang: String,
    ) : AppChangelogState

    /** No changelog.*.txt on the share (and no usable fallback yet in UI). */
    data object Absent : AppChangelogState

    data class Error(val message: String) : AppChangelogState
}
