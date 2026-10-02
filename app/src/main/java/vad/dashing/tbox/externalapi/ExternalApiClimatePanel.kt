package vad.dashing.tbox.externalapi

/**
 * Static climate page served by the external API HTTP server when the web-panel
 * setting is on. The page calls the existing `/v1` routes with the caller's token.
 */
object ExternalApiClimatePanel {
    const val MARKER = "tbox-climate-panel"

    private val html: String by lazy { loadHtml() }

    fun html(): String = html

    private fun loadHtml(): String {
        val stream = ExternalApiClimatePanel::class.java.classLoader
            ?.getResourceAsStream("external_api/climate_panel.html")
            ?: error("external_api/climate_panel.html is missing from resources")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
