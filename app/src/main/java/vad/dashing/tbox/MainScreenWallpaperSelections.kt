package vad.dashing.tbox

import org.json.JSONObject

/** Per main-screen page wallpaper file names for light and dark HU themes. */
data class MainScreenWallpaperSelectionsByPage(
    val lightByPage: Map<Int, String> = emptyMap(),
    val darkByPage: Map<Int, String> = emptyMap(),
) {
    fun fileNameFor(page: Int, forLightTheme: Boolean): String? {
        val map = if (forLightTheme) lightByPage else darkByPage
        return map[page]?.takeIf { it.isNotBlank() }
    }

    fun hasSelectionFor(page: Int, forLightTheme: Boolean): Boolean =
        fileNameFor(page, forLightTheme) != null

    /** Prefer [page]; if that page has no entry, use the first page that has a selection. */
    fun fileNameForCurrentOrAnyPage(page: Int, forLightTheme: Boolean): String? {
        fileNameFor(page, forLightTheme)?.let { return it }
        val preferredSide = if (forLightTheme) lightByPage else darkByPage
        val otherSide = if (forLightTheme) darkByPage else lightByPage
        val pages = (preferredSide.keys + otherSide.keys).sorted()
        return pages.firstNotNullOfOrNull { fileNameFor(it, forLightTheme) }
    }

    fun withFileName(page: Int, forLightTheme: Boolean, fileName: String): MainScreenWallpaperSelectionsByPage {
        val normalizedPage = page.coerceAtLeast(1)
        return if (forLightTheme) {
            copy(lightByPage = lightByPage + (normalizedPage to fileName))
        } else {
            copy(darkByPage = darkByPage + (normalizedPage to fileName))
        }
    }

    fun clearedForTheme(forLightTheme: Boolean): MainScreenWallpaperSelectionsByPage =
        if (forLightTheme) copy(lightByPage = emptyMap()) else copy(darkByPage = emptyMap())

    fun isEmpty(): Boolean = lightByPage.isEmpty() && darkByPage.isEmpty()

    fun toJson(): JSONObject {
        val json = JSONObject()
        if (lightByPage.isNotEmpty()) {
            val light = JSONObject()
            lightByPage.toSortedMap().forEach { (page, fileName) ->
                light.put(page.toString(), fileName)
            }
            json.put(KEY_LIGHT, light)
        }
        if (darkByPage.isNotEmpty()) {
            val dark = JSONObject()
            darkByPage.toSortedMap().forEach { (page, fileName) ->
                dark.put(page.toString(), fileName)
            }
            json.put(KEY_DARK, dark)
        }
        return json
    }

    companion object {
        const val JSON_KEY = "wallpaperSelectionByPage"
        private const val KEY_LIGHT = "light"
        private const val KEY_DARK = "dark"

        fun fromJson(obj: JSONObject?): MainScreenWallpaperSelectionsByPage {
            if (obj == null) return empty()
            return MainScreenWallpaperSelectionsByPage(
                lightByPage = parsePageMap(obj.optJSONObject(KEY_LIGHT)),
                darkByPage = parsePageMap(obj.optJSONObject(KEY_DARK)),
            )
        }

        fun fromDataStoreJson(raw: String?): MainScreenWallpaperSelectionsByPage {
            if (raw.isNullOrBlank()) return empty()
            return fromJson(runCatching { JSONObject(raw) }.getOrNull())
        }

        fun empty(): MainScreenWallpaperSelectionsByPage = MainScreenWallpaperSelectionsByPage()

        private fun parsePageMap(obj: JSONObject?): Map<Int, String> {
            if (obj == null) return emptyMap()
            val out = linkedMapOf<Int, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val page = key.toIntOrNull() ?: continue
                val fileName = obj.optString(key, "").trim()
                if (fileName.isNotEmpty()) {
                    out[page] = fileName
                }
            }
            return out
        }
    }
}
