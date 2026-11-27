package vad.dashing.voice.llm

/**
 * OpenAI-compatible cloud endpoints for Voice AI (stage B settings).
 * [CUSTOM] keeps whatever [CloudLlmSettings.baseUrl] the user typed.
 */
enum class CloudLlmPreset(
    val storageKey: String,
    val labelRu: String,
    val defaultBaseUrl: String,
) {
    AITUNNEL(
        storageKey = "aitunnel",
        labelRu = "AITunnel",
        defaultBaseUrl = "https://api.aitunnel.ru/v1",
    ),
    OPENAI(
        storageKey = "openai",
        labelRu = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
    ),
    OPENROUTER(
        storageKey = "openrouter",
        labelRu = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
    ),
    CUSTOM(
        storageKey = "custom",
        labelRu = "Свой URL",
        defaultBaseUrl = "",
    ),
    ;

    companion object {
        fun fromStorageKey(raw: String?): CloudLlmPreset {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageKey == key } ?: AITUNNEL
        }
    }
}
