package vad.dashing.tbox

import android.content.Context
import java.io.File

/**
 * Panel background images stored as paths relative to [Context.filesDir]
 * (e.g. `panel_backgrounds/{panelId}_light`).
 *
 * Resolution order when reading:
 * 1. Active theme cache `files/themes/{cacheKey}/panel_backgrounds/…` when main-screen and/or
 *    floating panels are in the active theme apply targets
 * 2. Shared [DIR_NAME]/… — user overrides (written by [SettingsManager.setPanelBackgroundImageFromUri])
 * 3. Panel background color only (callers when this returns null)
 */
object PanelBackgroundImageStorage {
    const val DIR_NAME = "panel_backgrounds"

    fun sanitizePanelStorageId(panelId: String): String {
        val t = panelId.trim()
        if (t.isEmpty()) return "panel"
        return buildString(t.length.coerceAtMost(80)) {
            for (ch in t) {
                when {
                    ch.isLetterOrDigit() || ch == '_' || ch == '-' -> append(ch)
                    ch == '.' || ch == ' ' -> append('_')
                }
            }
        }.ifEmpty { "panel" }
    }

    fun relativePathFor(panelStorageId: String, darkTheme: Boolean): String {
        val safe = sanitizePanelStorageId(panelStorageId)
        val suffix = if (darkTheme) "dark" else "light"
        return "$DIR_NAME/${safe}_$suffix"
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
        ).resolve(ThemeMaterialization.PANEL_BACKGROUNDS_DIR)

    fun themeTargetsIncludePanelBackgrounds(lookup: LauncherAppIconPaths.Lookup): Boolean {
        val targets = lookup.activeThemeApplyTargets
        return ThemeApplyTarget.MAIN_SCREEN_PANELS in targets ||
            ThemeApplyTarget.FLOATING_PANELS in targets
    }

    fun resolveFile(context: Context, relPath: String?): File? =
        resolveSharedFile(context.filesDir, relPath)

    fun resolveFile(
        filesDir: File,
        relPath: String?,
        lookup: LauncherAppIconPaths.Lookup,
    ): File? {
        if (themeTargetsIncludePanelBackgrounds(lookup)) {
            resolveThemeCacheFile(filesDir, relPath, lookup.activeThemeCacheKey)?.let { return it }
        }
        return resolveSharedFile(filesDir, relPath)
    }

    fun hasSharedOverride(filesDir: File, relPath: String?): Boolean =
        resolveSharedFile(filesDir, relPath) != null

    fun hasThemeCacheFile(filesDir: File, relPath: String?, lookup: LauncherAppIconPaths.Lookup): Boolean {
        if (!themeTargetsIncludePanelBackgrounds(lookup)) return false
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
