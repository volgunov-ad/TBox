package vad.dashing.tbox

import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.ThemeBundleExport.ParsedThemeBundle

/**
 * Detects which [ThemeApplyTarget] values a `.tboxtheme` bundle can apply.
 */
object ThemeApplyTargetAvailability {

    fun detectAvailable(parsed: ParsedThemeBundle): Set<ThemeApplyTarget> {
        val themeJson = parsed.themeJson
        val sections = ThemeLayoutExport.parseSectionsFromThemeJson(themeJson)
        val root = runCatching { JSONObject(themeJson) }.getOrNull() ?: return emptySet()
        val out = linkedSetOf<ThemeApplyTarget>()

        if (ThemeSection.MAIN_SCREEN in sections) {
            val mainScreen = root.optJSONObject(ThemeSection.MAIN_SCREEN.jsonKey)
            if (mainScreen != null) {
                if (mainScreenHasPanelsOrLayout(mainScreen)) {
                    out.add(ThemeApplyTarget.MAIN_SCREEN_PANELS)
                }
                if (
                    parsed.lightWallpapers.isNotEmpty() ||
                    parsed.darkWallpapers.isNotEmpty() ||
                    mainScreen.has(MainScreenWallpaperSelectionsByPage.JSON_KEY)
                ) {
                    out.add(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS)
                }
            }
        }

        if (ThemeSection.FLOATING_PANELS in sections) {
            val floating = root.optJSONObject(ThemeSection.FLOATING_PANELS.jsonKey)
            val panels = floating?.optJSONArray("panels")
            if (panels != null && panels.length() > 0) {
                out.add(ThemeApplyTarget.FLOATING_PANELS)
            }
        }

        if (ThemeSection.APP_ICONS in sections) {
            val appIcons = root.optJSONObject(ThemeSection.APP_ICONS.jsonKey)
            if (
                parsed.icons.isNotEmpty() ||
                parsed.httpRequestIcons.isNotEmpty() ||
                appIcons?.optJSONArray("packages")?.length().orZero() > 0 ||
                appIcons?.optJSONArray("httpRequestIconKeys")?.length().orZero() > 0
            ) {
                out.add(ThemeApplyTarget.APP_ICONS)
            }
        }

        if (parsed.tileBackgrounds.isNotEmpty() || themeJsonReferencesTileBackgrounds(root, sections)) {
            out.add(ThemeApplyTarget.TILE_BACKGROUNDS)
        }

        return out
    }

    fun defaultEnabled(available: Set<ThemeApplyTarget>): Set<ThemeApplyTarget> = available

    private fun mainScreenHasPanelsOrLayout(mainScreen: JSONObject): Boolean {
        val panels = mainScreen.optJSONArray("panels")
        if (panels != null && panels.length() > 0) return true
        return mainScreen.has("theme") ||
            mainScreen.has("pageCount") ||
            mainScreen.has("settingsButton") ||
            mainScreen.has("addButton") ||
            mainScreen.has("pagePrevButton") ||
            mainScreen.has("pageNextButton")
    }

    private fun themeJsonReferencesTileBackgrounds(
        root: JSONObject,
        sections: Set<ThemeSection>,
    ): Boolean {
        if (ThemeSection.MAIN_SCREEN in sections) {
            if (panelsReferenceTileBackgrounds(root.optJSONObject(ThemeSection.MAIN_SCREEN.jsonKey)?.optJSONArray("panels"))) {
                return true
            }
        }
        if (ThemeSection.FLOATING_PANELS in sections) {
            if (panelsReferenceTileBackgrounds(root.optJSONObject(ThemeSection.FLOATING_PANELS.jsonKey)?.optJSONArray("panels"))) {
                return true
            }
        }
        return false
    }

    private fun panelsReferenceTileBackgrounds(panels: JSONArray?): Boolean {
        if (panels == null) return false
        for (i in 0 until panels.length()) {
            val panel = panels.optJSONObject(i) ?: continue
            val widgets = panel.opt("widgets")
            if (widgetsReferenceTileBackgrounds(widgets)) return true
        }
        return false
    }

    private fun widgetsReferenceTileBackgrounds(widgets: Any?): Boolean {
        when (widgets) {
            is JSONArray -> {
                for (i in 0 until widgets.length()) {
                    val widget = widgets.optJSONObject(i) ?: continue
                    if (widgetReferencesTileBackground(widget)) return true
                }
            }
            is JSONObject -> {
                val keys = widgets.keys()
                while (keys.hasNext()) {
                    val widget = widgets.optJSONObject(keys.next()) ?: continue
                    if (widgetReferencesTileBackground(widget)) return true
                }
            }
        }
        return false
    }

    private fun widgetReferencesTileBackground(widget: JSONObject): Boolean {
        val light = widget.optString("tileBackgroundImageRelPathLight").trim()
        val dark = widget.optString("tileBackgroundImageRelPathDark").trim()
        return light.isNotEmpty() || dark.isNotEmpty()
    }

    private fun Int?.orZero(): Int = this ?: 0
}
