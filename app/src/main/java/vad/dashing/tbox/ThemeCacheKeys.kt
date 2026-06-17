package vad.dashing.tbox

import android.content.Context
import java.io.File

/**
 * Stable directory names under [ThemeMaterialization.THEMES_ROOT_DIR].
 */
object ThemeCacheKeys {

    private val SAFE_KEY_REGEX = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$")

    fun manualThemeCacheKey(sourceUri: String): String {
        val display = ThemeFileResolver.displayName(sourceUri).trim()
        val base = display
            .removeSuffix(".${ThemeBundleExport.THEME_FILE_EXTENSION}")
            .removeSuffix(".${ThemeBundleExport.THEME_FILE_EXTENSION.uppercase()}")
            .trim()
        return sanitizeCacheKey(base.ifBlank { "theme" })
    }

    fun driveModeCacheKey(rawValue: Int): String {
        val option = resolveDriveModeWidgetOption(rawValue)
        val slug = option.label
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "mode" }
        return sanitizeCacheKey("drive_mode_${option.rawValue}_$slug")
    }

    fun sanitizeCacheKey(raw: String): String {
        val cleaned = raw
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .trim('_', '.')
            .take(64)
        if (cleaned.isEmpty()) return "theme"
        val first = cleaned.first()
        if (!first.isLetterOrDigit()) return "t_$cleaned"
        return cleaned
    }

    fun isLikelyCacheKey(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains("://")) return false
        return SAFE_KEY_REGEX.matches(trimmed)
    }

    fun resolveUniqueManualCacheKey(context: Context, sourceUri: String): String {
        val base = manualThemeCacheKey(sourceUri)
        val root = ThemeMaterialization.themesRootDir(context)
        var candidate = base
        var suffix = 2
        while (File(root, candidate).exists()) {
            val manifest = ThemeMaterialization.readManifest(context, candidate)
            if (manifest?.sourceUri == sourceUri.trim()) {
                return candidate
            }
            candidate = sanitizeCacheKey("${base}_$suffix")
            suffix++
        }
        return candidate
    }
}
