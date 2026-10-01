package vad.dashing.voice.tts

/** Used in unit tests and when the Piper model is not packaged. */
class NoOpVoiceTts : VoiceTts {
    @Volatile
    var lastSpoken: String? = null
        private set

    @Volatile
    var speakCount: Int = 0
        private set

    override fun ensureReady() = Unit

    override fun speak(text: String) {
        lastSpoken = text
        speakCount += 1
    }

    override fun stop() = Unit

    override fun release() = Unit
}
