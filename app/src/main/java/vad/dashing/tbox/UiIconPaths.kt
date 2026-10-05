package vad.dashing.tbox

import java.io.File

/**
 * User overrides for built-in widget and navigation icons.
 *
 * Theme assets are an optional overlay. When [ThemeApplyTarget.UI_ICONS] is not active, reads and
 * writes use the shared directory, so a theme that does not own icons cannot modify its cache.
 *
 * When [preserveColors] is enabled for a key, optional day/night files are used:
 * - day: `{iconKey}`
 * - night: `{iconKey}.dark`
 * If only one exists, it is used for both themes. Without preserveColors, only the day file is
 * read and callers apply tint as usual.
 */
object UiIconPaths {

    const val SHARED_DIR = "ui_icons"
    const val THEME_DIR = "ui_icons"
    const val MAX_EDGE_PX = 512
    const val MAX_BYTES = 512 * 1024L
    const val NIGHT_SUFFIX = ".dark"

    enum class Variant {
        Day,
        Night,
    }

    private val validKey = Regex("[a-z0-9][a-z0-9._-]{0,127}")

    fun isValidKey(key: String): Boolean = validKey.matches(key.trim())

    fun storageFileName(iconKey: String, variant: Variant): String? {
        val key = iconKey.trim()
        if (!isValidKey(key)) return null
        return when (variant) {
            Variant.Day -> key
            Variant.Night -> key + NIGHT_SUFFIX
        }
    }

    fun baseKeyFromStorageName(fileName: String): String? {
        val name = fileName.trim()
        if (name.endsWith(NIGHT_SUFFIX)) {
            val base = name.removeSuffix(NIGHT_SUFFIX)
            return base.takeIf { isValidKey(it) }
        }
        return name.takeIf { isValidKey(it) }
    }

    fun isValidStorageFileName(fileName: String): Boolean =
        baseKeyFromStorageName(fileName) != null

    fun variantForTheme(currentTheme: Int): Variant =
        if (currentTheme == 2) Variant.Night else Variant.Day

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
        variant: Variant = Variant.Day,
    ): File? {
        val fileName = storageFileName(iconKey, variant) ?: return null
        return destinationStorageFile(filesDir, fileName, lookup)
    }

    fun resolveIconFile(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
        preserveColors: Boolean = false,
        currentTheme: Int = 1,
    ): File? {
        val key = iconKey.trim()
        if (!isValidKey(key)) return null
        if (!preserveColors) {
            return resolveStorageFile(filesDir, key, lookup)
        }
        val preferred = variantForTheme(currentTheme)
        val fallback = if (preferred == Variant.Night) Variant.Day else Variant.Night
        resolveStorageFile(filesDir, storageFileName(key, preferred)!!, lookup)?.let { return it }
        return resolveStorageFile(filesDir, storageFileName(key, fallback)!!, lookup)
    }

    fun resolveVariantFile(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
        variant: Variant,
    ): File? {
        val fileName = storageFileName(iconKey, variant) ?: return null
        return resolveStorageFile(filesDir, fileName, lookup)
    }

    fun hasThemeCacheIcon(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
        variant: Variant = Variant.Day,
    ): Boolean {
        if (ThemeApplyTarget.UI_ICONS !in lookup.activeThemeApplyTargets) return false
        val fileName = storageFileName(iconKey, variant) ?: return false
        val cacheKey = lookup.activeThemeCacheKey.trim()
        if (!ThemeCacheKeys.isLikelyCacheKey(cacheKey)) return false
        return validFile(File(themeIconsDir(filesDir, cacheKey), fileName)) != null
    }

    fun hasResolvableIcon(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
        preserveColors: Boolean = false,
        currentTheme: Int = 1,
    ): Boolean = resolveIconFile(filesDir, iconKey, lookup, preserveColors, currentTheme) != null

    fun hasAnyVariant(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
    ): Boolean =
        resolveVariantFile(filesDir, iconKey, lookup, Variant.Day) != null ||
            resolveVariantFile(filesDir, iconKey, lookup, Variant.Night) != null

    fun deleteCurrentOverride(
        filesDir: File,
        iconKey: String,
        lookup: LauncherAppIconPaths.Lookup,
        variant: Variant? = null,
    ): Boolean {
        val key = iconKey.trim()
        if (!isValidKey(key)) return false
        val variants = if (variant != null) listOf(variant) else listOf(Variant.Day, Variant.Night)
        var deleted = false
        for (v in variants) {
            val fileName = storageFileName(key, v) ?: continue
            if (deleteStorageFile(filesDir, fileName, lookup)) deleted = true
        }
        return deleted
    }

    fun listResolvableKeys(filesDir: File, lookup: LauncherAppIconPaths.Lookup): Set<String> {
        val keys = linkedSetOf<String>()
        keys.addAll(listBaseKeys(sharedIconsDir(filesDir)))
        if (ThemeApplyTarget.UI_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                keys.addAll(listBaseKeys(themeIconsDir(filesDir, cacheKey)))
            }
        }
        return keys
    }

    fun listStorageFileNames(filesDir: File, lookup: LauncherAppIconPaths.Lookup): Set<String> {
        val names = linkedSetOf<String>()
        names.addAll(listStorageNames(sharedIconsDir(filesDir)))
        if (ThemeApplyTarget.UI_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                names.addAll(listStorageNames(themeIconsDir(filesDir, cacheKey)))
            }
        }
        return names
    }

    fun countThemeCacheIcons(filesDir: File, cacheKey: String): Int =
        listBaseKeys(themeIconsDir(filesDir, cacheKey)).size

    private fun destinationStorageFile(
        filesDir: File,
        fileName: String,
        lookup: LauncherAppIconPaths.Lookup,
    ): File? {
        if (!isValidStorageFileName(fileName)) return null
        if (ThemeApplyTarget.UI_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                return File(themeIconsDir(filesDir, cacheKey), fileName)
            }
        }
        return File(sharedIconsDir(filesDir), fileName)
    }

    private fun resolveStorageFile(
        filesDir: File,
        fileName: String,
        lookup: LauncherAppIconPaths.Lookup,
    ): File? {
        if (!isValidStorageFileName(fileName)) return null
        if (ThemeApplyTarget.UI_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                validFile(File(themeIconsDir(filesDir, cacheKey), fileName))?.let { return it }
            }
        }
        return validFile(File(sharedIconsDir(filesDir), fileName))
    }

    private fun deleteStorageFile(
        filesDir: File,
        fileName: String,
        lookup: LauncherAppIconPaths.Lookup,
    ): Boolean {
        if (!isValidStorageFileName(fileName)) return false
        if (ThemeApplyTarget.UI_ICONS in lookup.activeThemeApplyTargets) {
            val cacheKey = lookup.activeThemeCacheKey.trim()
            if (ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
                validFile(File(themeIconsDir(filesDir, cacheKey), fileName))?.let { return it.delete() }
            }
        }
        return validFile(File(sharedIconsDir(filesDir), fileName))?.delete() == true
    }

    private fun listBaseKeys(dir: File): Set<String> =
        listStorageNames(dir).mapNotNullTo(linkedSetOf()) { baseKeyFromStorageName(it) }

    private fun listStorageNames(dir: File): Set<String> {
        if (!dir.isDirectory) return emptySet()
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0L && isValidStorageFileName(it.name) }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
    }

    private fun validFile(file: File): File? =
        file.takeIf { it.isFile && it.length() > 0L }
}
