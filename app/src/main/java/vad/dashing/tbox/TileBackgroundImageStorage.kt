package vad.dashing.tbox

import android.content.Context
import java.io.File

/**
 * Tile background images stored as paths relative to [Context.filesDir] (e.g. `tile_backgrounds/panel/0_light`).
 *
 * Resolution order when reading:
 * 1. Active theme cache `files/themes/{cacheKey}/tile_backgrounds/…` when main screen or floating panels are in the active theme
 * 2. Shared [DIR_NAME]/… — user overrides (written by [SettingsManager.setTileBackgroundImageFromUri])
 * 3. Tile background color only (callers when this returns null)
 */
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

    fun sharedDir(filesDir: File): File = File(filesDir, DIR_NAME)

    fun themeCacheDir(filesDir: File, cacheKey: String): File =
        File(
            File(filesDir, ThemeMaterialization.THEMES_ROOT_DIR),
            ThemeCacheKeys.sanitizeCacheKey(cacheKey),
        ).resolve(ThemeMaterialization.TILE_BACKGROUNDS_DIR)

    fun themeSectionsIncludeTileBackgrounds(lookup: LauncherAppIconPaths.Lookup): Boolean =
        ThemeSection.MAIN_SCREEN in lookup.activeThemeSections ||
            ThemeSection.FLOATING_PANELS in lookup.activeThemeSections

    /** Shared folder only (export helpers, legacy). */
    fun resolveFile(context: Context, relPath: String?): File? =
        resolveSharedFile(context.filesDir, relPath)

    fun resolveFile(
        filesDir: File,
        relPath: String?,
        lookup: LauncherAppIconPaths.Lookup,
    ): File? {
        if (themeSectionsIncludeTileBackgrounds(lookup)) {
            resolveThemeCacheFile(filesDir, relPath, lookup.activeThemeCacheKey)?.let { return it }
        }
        return resolveSharedFile(filesDir, relPath)
    }

    fun hasSharedOverride(filesDir: File, relPath: String?): Boolean =
        resolveSharedFile(filesDir, relPath) != null

    fun countThemeCacheFiles(filesDir: File, cacheKey: String): Int {
        val dir = themeCacheDir(filesDir, cacheKey)
        if (!dir.isDirectory) return 0
        return dir.walkTopDown().count { it.isFile && it.length() > 0L }
    }

    private fun resolveSharedFile(filesDir: File, relPath: String?): File? {
        val normalized = relPath?.trim()?.replace('\\', '/') ?: return null
        if (!isAllowedStoredRelPath(normalized)) return null
        val file = File(filesDir, normalized.replace('/', File.separatorChar))
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    private fun resolveThemeCacheFile(filesDir: File, relPath: String?, cacheKey: String): File? {
        val normalized = relPath?.trim()?.replace('\\', '/') ?: return null
        if (!isAllowedStoredRelPath(normalized)) return null
        val key = cacheKey.trim()
        if (!ThemeCacheKeys.isLikelyCacheKey(key)) return null
        val relInCache = normalized.removePrefix("$DIR_NAME/")
        val file = File(themeCacheDir(filesDir, key), relInCache.replace('/', File.separatorChar))
        return file.takeIf { it.isFile && it.length() > 0L }
    }
}
