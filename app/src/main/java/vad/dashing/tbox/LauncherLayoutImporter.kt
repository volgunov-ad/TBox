package vad.dashing.tbox

import org.json.JSONArray
import org.json.JSONObject

/**
 * Imports standalone `tbox_launcher_layout` JSON (see project-root design files).
 */
object LauncherLayoutImporter {

    private const val TYPE = "tbox_launcher_layout"

    suspend fun importJson(settingsManager: SettingsManager, json: String): Result<Unit> {
        val root = runCatching { JSONObject(json) }.getOrElse {
            return Result.failure(IllegalArgumentException("invalid_json"))
        }
        if (root.optString("type") != TYPE) {
            return Result.failure(IllegalArgumentException("unsupported_type"))
        }
        if (root.optInt("formatVersion", -1) < 1) {
            return Result.failure(IllegalArgumentException("unsupported_format_version"))
        }
        return runCatching {
            val section = JSONObject().apply {
                if (root.has("pageCount")) put("pageCount", root.optInt("pageCount"))
                if (root.has("currentPage")) put("currentPage", root.optInt("currentPage"))
                root.optJSONObject("theme")?.let { put("theme", it) }
                root.optJSONObject("settingsButton")?.let { put("settingsButton", it) }
                root.optJSONObject("addButton")?.let { put("addButton", it) }
                root.optJSONObject("pagePrevButton")?.let { put("pagePrevButton", it) }
                root.optJSONObject("pageNextButton")?.let { put("pageNextButton", it) }
                root.optJSONArray("mainScreenPanels")?.let { put("panels", it) }
            }
            ThemeLayoutExport.importMainScreenSectionPublic(section, settingsManager)
            when (val floating = root.opt("floatingPanels")) {
                is JSONArray -> if (floating.length() > 0) {
                    ThemeLayoutExport.importFloatingPanelsPublic(floating, settingsManager)
                }
                is JSONObject -> floating.optJSONArray("panels")?.let { panels ->
                    if (panels.length() > 0) {
                        ThemeLayoutExport.importFloatingPanelsPublic(panels, settingsManager)
                    }
                }
            }
        }
    }
}
