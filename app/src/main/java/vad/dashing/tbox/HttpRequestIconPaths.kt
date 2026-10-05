package vad.dashing.tbox

import java.io.File

/** Custom icons for HTTP request widgets, keyed by panel id and tile index. */
object HttpRequestIconPaths {

    const val ICONS_SUBDIR = ThemeMaterialization.HTTP_REQUEST_ICONS_DIR

    fun iconKey(panelStorageId: String, widgetIndex: Int): String {
        val panel = panelStorageId.trim().ifBlank { "dashboard" }
            .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        return "$panel-$widgetIndex"
    }

    fun sharedIconsDir(filesDir: File): File =
        File(filesDir, SettingsManager.HTTP_REQUEST_ICONS_DIR)

    fun themeIconsDir(filesDir: File, cacheKey: String): File =
        File(File(filesDir, ThemeMaterialization.THEMES_ROOT_DIR), ThemeCacheKeys.sanitizeCacheKey(cacheKey))
            .resolve(ICONS_SUBDIR)

    fun liveIconFile(iconsDir: File, iconKey: String): File =
        File(iconsDir, iconKey.trim())

    /** Destination for a new HTTP-request icon write (theme cache when APP_ICONS is active). */
    fun destinationIconFile(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
    ): File? {
        val key = iconKey.trim()
        if (key.isEmpty()) return null
        if (ThemeApplyTarget.APP_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                return liveIconFile(themeIconsDir(filesDir, cacheKey), key)
            }
        }
        return liveIconFile(sharedIconsDir(filesDir), key)
    }

    fun resolveStoredIconFile(iconsDir: File, iconKey: String): File? {
        val key = iconKey.trim()
        if (key.isEmpty()) return null
        val primary = liveIconFile(iconsDir, key)
        if (primary.isFile && primary.length() > 0L) return primary
        val legacyPng = File(iconsDir, "$key.png")
        if (legacyPng.isFile && legacyPng.length() > 0L) return legacyPng
        return null
    }

    fun resolveIconFile(filesDir: File, iconKey: String, lookup: LauncherAppIconPaths.Lookup): File? {
        if (ThemeApplyTarget.APP_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                resolveStoredIconFile(themeIconsDir(filesDir, cacheKey), iconKey)?.let { return it }
            }
        }
        resolveStoredIconFile(sharedIconsDir(filesDir), iconKey)?.let { return it }
        return null
    }

    fun hasThemeCacheIcon(filesDir: File, iconKey: String, lookup: LauncherAppIconPaths.Lookup): Boolean {
        if (ThemeApplyTarget.APP_ICONS !in lookup.activeThemeApplyTargets) return false
        val cacheKey = lookup.activeThemeCacheKey.trim()
        if (!ThemeCacheKeys.isLikelyCacheKey(cacheKey)) return false
        return resolveStoredIconFile(themeIconsDir(filesDir, cacheKey), iconKey) != null
    }

    fun hasResolvableIcon(filesDir: File, iconKey: String, lookup: LauncherAppIconPaths.Lookup): Boolean =
        resolveIconFile(filesDir, iconKey, lookup) != null

    fun deleteThemeCacheIcon(filesDir: File, iconKey: String, lookup: LauncherAppIconPaths.Lookup): Boolean {
        if (ThemeApplyTarget.APP_ICONS !in lookup.activeThemeApplyTargets) return false
        val cacheKey = lookup.activeThemeCacheKey.trim()
        if (!ThemeCacheKeys.isLikelyCacheKey(cacheKey)) return false
        val file = resolveStoredIconFile(themeIconsDir(filesDir, cacheKey), iconKey) ?: return false
        return file.delete()
    }

    fun deleteSharedIcon(filesDir: File, iconKey: String): Boolean {
        val file = resolveStoredIconFile(sharedIconsDir(filesDir), iconKey) ?: return false
        return file.delete()
    }

    fun countThemeCacheIcons(filesDir: File, cacheKey: String): Int {
        val dir = themeIconsDir(filesDir, cacheKey)
        if (!dir.isDirectory) return 0
        return dir.listFiles()?.count { it.isFile && it.length() > 0L } ?: 0
    }
}
