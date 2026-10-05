package vad.dashing.tbox

import android.content.Context
import java.io.File

/**
 * Tile background images stored as paths relative to [Context.filesDir] (e.g. `tile_backgrounds/panel/0_light`).
 *
 * Resolution order when reading:
 * 1. Active theme cache `files/themes/{cacheKey}/tile_backgrounds/…` when [ThemeApplyTarget.TILE_BACKGROUNDS] is active
 * 2. Shared [DIR_NAME]/… — used when no theme tile-background target (or theme file missing)
 * 3. Tile background color only (callers when this returns null)
 *
 * Writes: when the active theme includes tile backgrounds, [destinationFile] targets the theme cache;
 * otherwise the shared folder.
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

    fun themeTargetsIncludeTileBackgrounds(lookup: LauncherAppIconPaths.Lookup): Boolean =
        ThemeApplyTarget.TILE_BACKGROUNDS in lookup.activeThemeApplyTargets

    /**
     * Absolute file for a new write of [relPath]. Prefers the active theme cache when tile
     * backgrounds are in apply targets and [cacheKey] looks valid.
     */
    fun destinationFile(filesDir: File, relPath: String?, lookup: LauncherAppIconPaths.Lookup): File? {
        val normalized = relPath?.trim()?.replace('\\', '/') ?: return null
        if (!isAllowedStoredRelPath(normalized)) return null
        val relInStorage = normalized.removePrefix("$DIR_NAME/")
        if (themeTargetsIncludeTileBackgrounds(lookup)) {
            val key = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(key)) {
                return File(themeCacheDir(filesDir, key), relInStorage.replace('/', File.separatorChar))
            }
        }
        return File(filesDir, normalized.replace('/', File.separatorChar))
    }

    /** Shared folder only (export helpers, legacy). */
    fun resolveFile(context: Context, relPath: String?): File? =
        resolveSharedFile(context.filesDir, relPath)

    fun resolveFile(
        filesDir: File,
        relPath: String?,
        lookup: LauncherAppIconPaths.Lookup,
    ): File? {
        if (themeTargetsIncludeTileBackgrounds(lookup)) {
            resolveThemeCacheFile(filesDir, relPath, lookup.activeThemeCacheKey)?.let { return it }
        }
        return resolveSharedFile(filesDir, relPath)
    }

    fun hasSharedOverride(filesDir: File, relPath: String?): Boolean =
        resolveSharedFile(filesDir, relPath) != null

    fun hasThemeCacheFile(filesDir: File, relPath: String?, lookup: LauncherAppIconPaths.Lookup): Boolean {
        if (!themeTargetsIncludeTileBackgrounds(lookup)) return false
        return resolveThemeCacheFile(filesDir, relPath, lookup.activeThemeCacheKey) != null
    }

    fun hasResolvableFile(filesDir: File, relPath: String?, lookup: LauncherAppIconPaths.Lookup): Boolean =
        resolveFile(filesDir, relPath, lookup) != null

    fun deleteThemeCacheFile(filesDir: File, relPath: String?, cacheKey: String): Boolean {
        val file = resolveThemeCacheFile(filesDir, relPath, cacheKey) ?: return false
        return file.delete()
    }

    fun deleteSharedFile(filesDir: File, relPath: String?): Boolean {
        val file = resolveSharedFile(filesDir, relPath) ?: return false
        return file.delete()
    }

    fun clearSharedDir(filesDir: File) {
        val dir = sharedDir(filesDir)
        if (!dir.isDirectory) return
        dir.deleteRecursively()
        dir.mkdirs()
    }

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
