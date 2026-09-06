package vad.dashing.tbox

import java.io.File

/**
 * User overrides for built-in widget and navigation icons.
 *
 * Theme assets are an optional overlay. When [ThemeApplyTarget.UI_ICONS] is not active, reads and
 * writes use the shared directory, so a theme that does not own icons cannot modify its cache.
 */
object UiIconPaths {

    const val SHARED_DIR = "ui_icons"
    const val THEME_DIR = "ui_icons"

    private val validKey = Regex("[a-z0-9][a-z0-9._-]{0,127}")

    fun isValidKey(key: String): Boolean = validKey.matches(key.trim())

    fun sharedIconsDir(filesDir: File): File = File(filesDir, SHARED_DIR)

    fun themeIconsDir(filesDir: File, cacheKey: String): File =
        File(
            File(filesDir, ThemeMaterialization.THEMES_ROOT_DIR),
            ThemeCacheKeys.sanitizeCacheKey(cacheKey),
        ).resolve(THEME_DIR)

    fun destinationIconFile(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
    ): File? {
        val key = iconKey.trim()
        if (!isValidKey(key)) return null
        if (ThemeApplyTarget.UI_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                return File(themeIconsDir(filesDir, cacheKey), key)
            }
        }
        return File(sharedIconsDir(filesDir), key)
    }

    fun resolveIconFile(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
    ): File? {
        val key = iconKey.trim()
        if (!isValidKey(key)) return null
        if (ThemeApplyTarget.UI_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                validFile(File(themeIconsDir(filesDir, cacheKey), key))?.let { return it }
            }
        }
        return validFile(File(sharedIconsDir(filesDir), key))
    }

    fun hasThemeCacheIcon(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
    ): Boolean {
        if (ThemeApplyTarget.UI_ICONS !in lookup.activeThemeApplyTargets) return false
        val key = iconKey.trim()
        val cacheKey = lookup.activeThemeCacheKey.trim()
        if (!isValidKey(key) || !ThemeCacheKeys.isLikelyCacheKey(cacheKey)) return false
        return validFile(File(themeIconsDir(filesDir, cacheKey), key)) != null
    }

    fun hasResolvableIcon(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
    ): Boolean = resolveIconFile(filesDir, iconKey, lookup) != null

    fun deleteCurrentOverride(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
    ): Boolean {
        val key = iconKey.trim()
        if (!isValidKey(key)) return false
        if (ThemeApplyTarget.UI_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                validFile(File(themeIconsDir(filesDir, cacheKey), key))?.let { return it.delete() }
            }
        }
        return validFile(File(sharedIconsDir(filesDir), key))?.delete() == true
    }

    fun listResolvableKeys(filesDir: File, lookup: LauncherAppIconPaths.Lookup): Set<String> {
        val keys = linkedSetOf<String>()
        keys.addAll(listKeys(sharedIconsDir(filesDir)))
        if (ThemeApplyTarget.UI_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                keys.addAll(listKeys(themeIconsDir(filesDir, cacheKey)))
            }
        }
        return keys
    }

    fun countThemeCacheIcons(filesDir: File, cacheKey: String): Int =
        listKeys(themeIconsDir(filesDir, cacheKey)).size

    private fun listKeys(dir: File): Set<String> {
        if (!dir.isDirectory) return emptySet()
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0L && isValidKey(it.name) }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
    }

    private fun validFile(file: File): File? =
        file.takeIf { it.isFile && it.length() > 0L }
}
