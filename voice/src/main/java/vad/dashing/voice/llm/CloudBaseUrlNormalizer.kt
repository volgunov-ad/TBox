package vad.dashing.voice.llm

/**
 * Normalizes an OpenAI-compatible API root to `scheme://host[:port]/path…/v1`
 * without a trailing slash (ready for `/models`, `/chat/completions`).
 */
object CloudBaseUrlNormalizer {
    fun normalize(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return ""

        // Allow pasting a full models URL.
        value = value.replace(Regex("/models/?$", RegexOption.IGNORE_CASE), "")
        value = value.replace(Regex("/chat/completions/?$", RegexOption.IGNORE_CASE), "")

        while (value.endsWith('/')) {
            value = value.dropLast(1)
        }

        if (!value.contains("://")) {
            value = "https://$value"
        }

        // If user pasted host without /v1, append it (common for custom gateways).
        val path = value.substringAfter("://", missingDelimiterValue = "")
            .substringAfter('/', missingDelimiterValue = "")
        if (path.isEmpty()) {
            value = "$value/v1"
        } else if (!path.endsWith("v1") && !path.contains("/v1/")) {
            // host/api → host/api/v1; already …/v1 stays.
            if (!Regex("""(^|/)v1$""", RegexOption.IGNORE_CASE).containsMatchIn(path)) {
                value = "$value/v1"
            }
        }

        while (value.endsWith('/')) {
            value = value.dropLast(1)
        }
        return value
    }

    fun modelsUrl(baseUrl: String): String {
        val root = normalize(baseUrl)
        require(root.isNotBlank()) { "Нужен base URL облачного API" }
        return "$root/models"
    }
}
