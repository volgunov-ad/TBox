package vad.dashing.voice.stt

/**
 * Offline speech-to-text session.
 * Callbacks may arrive on the main thread (Vosk SpeechService).
 */
interface VoiceStt {
    fun ensureReady()

    val isReady: Boolean

    val lastError: String?

    /**
     * Start microphone recognition.
     * [onPartial] / [onFinal] receive plain text (not JSON).
     * [onEnded] is always called when the session finishes (final, timeout, stop, or error).
     */
    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEnded: (reason: ListenEndReason) -> Unit,
    )

    /** Stop listening; triggers a final result if any audio was captured. */
    fun stopListening()

    fun release()
}

enum class ListenEndReason {
    FINAL,
    TIMEOUT,
    STOPPED,
    ERROR,
}
