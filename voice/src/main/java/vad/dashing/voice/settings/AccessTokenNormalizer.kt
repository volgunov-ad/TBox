package vad.dashing.voice.settings

object AccessTokenNormalizer {
    private val bearerPrefix = Regex("^\\s*Bearer\\s+", RegexOption.IGNORE_CASE)

    /**
     * Accepts either a raw access token or an `Authorization` value like `Bearer …`.
     */
    fun normalize(raw: String): String =
        raw.trim().replaceFirst(bearerPrefix, "").trim()
}
