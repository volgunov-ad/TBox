package vad.dashing.voice.tts

/**
 * Offline speech synthesis for Voice answers.
 * Implementations must be safe to call from a background dispatcher.
 */
interface VoiceTts {
    /** Prepare native engine / models. Idempotent. */
    fun ensureReady()

    /** Synthesize and play [text]. Stops any in-progress utterance first. */
    fun speak(text: String)

    /** Stop playback immediately. */
    fun stop()

    /** Release native resources. */
    fun release()
}
