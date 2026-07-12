package vad.dashing.tbox

import java.io.File

/**
 * Custom icons for app-launcher and music-player widgets.
 *
 * Resolution order when reading:
 * 1. Active theme cache `files/themes/{cacheKey}/icons/` when [ThemeSection.APP_ICONS] is in the active theme
 * 2. [SettingsManager.LAUNCHER_APP_ICONS_DIR] — user overrides in the shared folder
 * 3. System icon (resolved by callers when this returns null)
 *
 * User saves always go to the shared folder only.
 */
object LauncherAppIconPaths {

    const val ICONS_SUBDIR = ThemeMaterialization.ICONS_DIR

    private val NON_ICON_FILE_EXTENSIONS = setOf("txt", "md", "json", "xml", "bak")

    data class Lookup(
        val activeThemeCacheKey: String = "",
        val activeThemeApplyTargets: Set<ThemeApplyTarget> = emptySet(),
    ) {
        companion object {
            val None = Lookup()
        }
    }

    fun sharedIconsDir(filesDir: File): File =
        File(filesDir, SettingsManager.LAUNCHER_APP_ICONS_DIR)

    fun themeIconsDir(filesDir: File, cacheKey: String): File =
        File(File(filesDir, ThemeMaterialization.THEMES_ROOT_DIR), ThemeCacheKeys.sanitizeCacheKey(cacheKey))
            .resolve(ICONS_SUBDIR)

    fun liveIconFile(iconsDir: File, packageName: String): File =
        File(iconsDir, packageName.trim())

    fun resolveStoredIconFile(iconsDir: File, packageName: String): File? {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return null
        val primary = liveIconFile(iconsDir, pkg)
        if (primary.isFile && primary.length() > 0L) return primary
        val legacyPng = File(iconsDir, "$pkg.png")
        if (legacyPng.isFile && legacyPng.length() > 0L) return legacyPng
        return null
    }

    fun resolveIconFile(filesDir: File, packageName: String, lookup: Lookup): File? {
        if (ThemeApplyTarget.APP_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                val themeDir = themeIconsDir(filesDir, cacheKey)
                if (themeDir.isDirectory) {
                    resolveStoredIconFile(themeDir, packageName)?.let { return it }
                }
            }
        }
        resolveStoredIconFile(sharedIconsDir(filesDir), packageName)?.let { return it }
        return null
    }

    fun hasSharedOverride(filesDir: File, packageName: String): Boolean =
        resolveStoredIconFile(sharedIconsDir(filesDir), packageName) != null

    fun hasThemeCacheIcon(filesDir: File, packageName: String, lookup: Lookup): Boolean {
        if (ThemeApplyTarget.APP_ICONS !in lookup.activeThemeApplyTargets) return false
        val cacheKey = lookup.activeThemeCacheKey.trim()
        if (!ThemeCacheKeys.isLikelyCacheKey(cacheKey)) return false
        val themeDir = themeIconsDir(filesDir, cacheKey)
        if (!themeDir.isDirectory) return false
        return resolveStoredIconFile(themeDir, packageName) != null
    }

    fun hasResolvableIcon(filesDir: File, packageName: String, lookup: Lookup): Boolean =
        resolveIconFile(filesDir, packageName, lookup) != null

    fun deleteThemeCacheIcon(filesDir: File, packageName: String, lookup: Lookup): Boolean {
        if (ThemeApplyTarget.APP_ICONS !in lookup.activeThemeApplyTargets) return false
        val cacheKey = lookup.activeThemeCacheKey.trim()
        if (!ThemeCacheKeys.isLikelyCacheKey(cacheKey)) return false
        val themeDir = themeIconsDir(filesDir, cacheKey)
        if (!themeDir.isDirectory) return false
        val file = resolveStoredIconFile(themeDir, packageName) ?: return false
        return file.delete()
    }

    fun deleteSharedIcon(filesDir: File, packageName: String): Boolean {
        val file = resolveStoredIconFile(sharedIconsDir(filesDir), packageName) ?: return false
        return file.delete()
    }

    fun listStoredPackageNames(iconsDir: File): Set<String> {
        if (!iconsDir.isDirectory) return emptySet()
        return iconsDir.listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.mapNotNull { file ->
                val name = file.name
                when {
                    name.endsWith(".png", ignoreCase = true) ->
                        file.nameWithoutExtension.takeIf { it.isNotEmpty() }
                    file.extension.lowercase() in NON_ICON_FILE_EXTENSIONS -> null
                    else -> name.takeIf { it.isNotEmpty() }
                }
            }
            ?.toSet()
            .orEmpty()
    }

    fun listAllResolvablePackageNames(filesDir: File, lookup: Lookup): Set<String> {
        val names = linkedSetOf<String>()
        names.addAll(listStoredPackageNames(sharedIconsDir(filesDir)))
        if (ThemeApplyTarget.APP_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                names.addAll(listStoredPackageNames(themeIconsDir(filesDir, cacheKey)))
            }
        }
        return names
    }

    fun liveFileNameFromThemeAsset(themeAssetFileName: String): String {
        val trimmed = themeAssetFileName.trim()
        return trimmed.removeSuffix(".png").removeSuffix(".PNG")
    }

    fun countThemeCacheIcons(filesDir: File, cacheKey: String): Int {
        val dir = themeIconsDir(filesDir, cacheKey)
        if (!dir.isDirectory) return 0
        return dir.listFiles()?.count { it.isFile && it.length() > 0L } ?: 0
    }
}
