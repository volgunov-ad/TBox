package vad.dashing.tbox.externalapi

/**
 * Static climate page served by the external API HTTP server when the web-panel
 * setting is on. The page calls the existing `/v1` routes with the caller's token.
 */
object ExternalApiClimatePanel {
    const val MARKER = "tbox-climate-panel"
    private const val LANG_TOKEN = "__APP_LANG__"

    private val template: String by lazy { loadHtml() }

    /** [language] is the app UI language (`ru` or `en`). Anything else stays Russian. */
    fun html(language: String = "ru"): String {
        val code = if (language.equals("en", ignoreCase = true)) "en" else "ru"
        return template.replace(LANG_TOKEN, code)
    }

    private fun loadHtml(): String {
        val stream = ExternalApiClimatePanel::class.java.classLoader
            ?.getResourceAsStream("external_api/climate_panel.html")
            ?: error("external_api/climate_panel.html is missing from resources")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
