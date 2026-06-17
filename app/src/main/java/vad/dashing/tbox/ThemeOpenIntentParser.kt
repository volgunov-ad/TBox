package vad.dashing.tbox

import android.content.Intent
import android.net.Uri

data class ThemeOpenRequest(
    val uriString: String,
    val displayName: String,
)

object ThemeOpenIntentParser {

    fun parse(intent: Intent?): ThemeOpenRequest? {
        val i = intent ?: return null
        val uri = resolveThemeUri(i) ?: return null
        val uriString = uri.toString()
        if (!isTboxThemeUri(uriString)) return null
        return ThemeOpenRequest(
            uriString = uriString,
            displayName = ThemeFileResolver.displayName(uriString),
        )
    }

    fun isTboxThemeUri(uriString: String): Boolean {
        val name = ThemeFileResolver.displayName(uriString)
        return isTboxThemeFileName(name)
    }

    fun isTboxThemeFileName(fileName: String): Boolean {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.endsWith(".${ThemeBundleExport.THEME_FILE_EXTENSION}", ignoreCase = true)
    }

    private fun resolveThemeUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            else -> null
        }
    }
}
