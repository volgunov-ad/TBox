package vad.dashing.tbox

import android.content.Context
import java.io.File

/** On-disk tile backgrounds live under [Context.filesDir]/[DIR_NAME]/… (paths stored in settings JSON). */
object TileBackgroundImageStorage {
    const val DIR_NAME = "tile_backgrounds"
    /** Used when editing tiles on the main Dashboard tab (no floating / main-screen panel id). */
    const val MAIN_TAB_DASHBOARD_STORAGE_ID = "main_tab_dashboard"

    fun sanitizePanelStorageId(panelId: String): String {
        val t = panelId.trim()
        if (t.isEmpty()) return MAIN_TAB_DASHBOARD_STORAGE_ID
        return buildString(t.length.coerceAtMost(80)) {
            for (ch in t) {
                when {
                    ch.isLetterOrDigit() || ch == '_' || ch == '-' -> append(ch)
                    ch == '.' || ch == ' ' -> append('_')
                }
            }
        }.ifEmpty { MAIN_TAB_DASHBOARD_STORAGE_ID }
    }

    fun relativePathFor(panelStorageId: String, widgetIndex: Int, darkTheme: Boolean): String {
        val safe = sanitizePanelStorageId(panelStorageId)
        val slot = widgetIndex.coerceAtLeast(0)
        val suffix = if (darkTheme) "dark" else "light"
        return "$DIR_NAME/$safe/${slot}_$suffix"
    }

    fun isAllowedStoredRelPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val normalized = path.trim().replace('\\', '/')
        if (".." in normalized) return false
        if (!normalized.startsWith("$DIR_NAME/")) return false
        return true
    }

    fun resolveFile(context: Context, relPath: String?): File? {
        val normalized = relPath?.trim()?.replace('\\', '/') ?: return null
        if (!isAllowedStoredRelPath(normalized)) return null
        return File(context.filesDir, normalized.replace('/', File.separatorChar))
    }
}
