package vad.dashing.voice.stt

/** Used in unit tests when Vosk native libs / model are unavailable. */
class NoOpVoiceStt : VoiceStt {
    @Volatile
    override var isReady: Boolean = true
        private set

    @Volatile
    override var lastError: String? = null
        private set

    @Volatile
    var started: Boolean = false
        private set

    override fun ensureReady() {
        isReady = true
    }

    override fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEnded: (ListenEndReason) -> Unit,
    ) {
        started = true
        onFinal("")
        onEnded(ListenEndReason.FINAL)
        started = false
    }

    override fun stopListening() {
        started = false
    }

    override fun release() {
        started = false
    }
}
