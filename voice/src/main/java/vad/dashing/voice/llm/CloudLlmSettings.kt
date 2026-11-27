package vad.dashing.voice.llm

/**
 * Cloud LLM connection prefs for Voice (stages B–D + freeform speak).
 */
data class CloudLlmSettings(
    val enabled: Boolean = false,
    val preferCloud: Boolean = true,
    /** Explicit consent to send STT text to the cloud provider. */
    val allowSendPhrases: Boolean = false,
    /**
     * Allow long free-form answers (stories, explanations) without a tool call.
     * Spoken via Piper in sentence chunks. Off by default.
     */
    val allowFreeformSpeak: Boolean = false,
    val preset: CloudLlmPreset = CloudLlmPreset.AITUNNEL,
    val baseUrl: String = CloudLlmPreset.AITUNNEL.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = "",
) {
    /** Settings look ready for the cloud agent; does not prove network. */
    fun looksConfigured(): Boolean =
        enabled &&
            allowSendPhrases &&
            CloudBaseUrlNormalizer.normalize(baseUrl).isNotBlank() &&
            apiKey.isNotBlank() &&
            model.isNotBlank()
}

object FreeformSpeakLimits {
    /** Short refuse / non-freeform Speak cap (stage C). */
    const val SHORT_MAX_CHARS = 240

    /** Hard cap for freeform answers before TTS (safety / latency). */
    const val FREEFORM_MAX_CHARS = 8_000
}
