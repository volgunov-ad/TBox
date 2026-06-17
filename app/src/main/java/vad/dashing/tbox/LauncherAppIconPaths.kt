package vad.dashing.tbox

import java.io.File

/**
 * Paths for custom app-launcher widget icons under [SettingsManager.LAUNCHER_APP_ICONS_DIR].
 * Live storage uses the package name as the file name (no extension); theme zips use the same name.
 */
object LauncherAppIconPaths {

    private val NON_ICON_FILE_EXTENSIONS = setOf("txt", "md", "json", "xml", "bak")

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

    /** Maps a file name from theme cache/zip to the on-disk live icon file name. */
    fun liveFileNameFromThemeAsset(themeAssetFileName: String): String {
        val trimmed = themeAssetFileName.trim()
        return trimmed.removeSuffix(".png").removeSuffix(".PNG")
    }

    /**
     * Copies icon files from a theme cache/zip icons directory into [liveIconsDir]
     * using live storage naming (package name, no extension).
     */
    fun installFromThemeCacheDirectory(cacheIconsDir: File, liveIconsDir: File): Int {
        if (!cacheIconsDir.isDirectory) return 0
        liveIconsDir.mkdirs()
        var count = 0
        cacheIconsDir.listFiles()?.filter { it.isFile }?.forEach { file ->
            val liveName = liveFileNameFromThemeAsset(file.name)
            if (liveName.isBlank()) return@forEach
            val dest = liveIconFile(liveIconsDir, liveName)
            if (!dest.exists() || dest.length() != file.length()) {
                file.copyTo(dest, overwrite = true)
            }
            count++
        }
        return count
    }
}
