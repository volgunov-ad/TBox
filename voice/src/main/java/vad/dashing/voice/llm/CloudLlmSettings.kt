package vad.dashing.voice.llm

/**
 * Cloud LLM connection prefs for Voice (stage B). Agent tool-calling is stage C.
 */
data class CloudLlmSettings(
    val enabled: Boolean = false,
    val preferCloud: Boolean = true,
    /** Explicit consent to send STT text to the cloud provider. */
    val allowSendPhrases: Boolean = false,
    val preset: CloudLlmPreset = CloudLlmPreset.AITUNNEL,
    val baseUrl: String = CloudLlmPreset.AITUNNEL.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = "",
) {
    /** Settings look ready for a future cloud agent (stage C); does not prove network. */
    fun looksConfigured(): Boolean =
        enabled &&
            allowSendPhrases &&
            CloudBaseUrlNormalizer.normalize(baseUrl).isNotBlank() &&
            apiKey.isNotBlank() &&
            model.isNotBlank()
}
