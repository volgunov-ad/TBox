package vad.dashing.tbox.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.SettingsViewModel
import java.io.File

/**
 * Decodes a custom icon for app-launcher / music-player widgets.
 * Checks the active theme cache first, then shared overrides.
 */
fun decodeLauncherAppCustomIconIfPresent(
    context: Context,
    packageName: String,
    iconSizePx: Int,
    lookup: LauncherAppIconPaths.Lookup = LauncherAppIconPaths.Lookup.None,
): ImageBitmap? = runCatching {
    if (packageName.isBlank()) return@runCatching null
    val f = LauncherAppIconPaths.resolveIconFile(context.filesDir, packageName, lookup)
        ?: return@runCatching null
    decodeAndScaleIconFile(f, iconSizePx)
}.getOrNull()

private fun decodeAndScaleIconFile(file: File, iconSizePx: Int): ImageBitmap? {
    if (!file.isFile || file.length() <= 0L) return null
    val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    if (decoded.width == iconSizePx && decoded.height == iconSizePx) {
        return decoded.asImageBitmap()
    }
    val scaled = Bitmap.createScaledBitmap(decoded, iconSizePx, iconSizePx, true)
    if (scaled != decoded) decoded.recycle()
    return scaled.asImageBitmap()
}

@Composable
fun rememberLauncherAppIconLookup(settingsViewModel: SettingsViewModel): LauncherAppIconPaths.Lookup {
    val uri by settingsViewModel.activeThemeUri.collectAsStateWithLifecycle()
    val sections by settingsViewModel.activeThemeSections.collectAsStateWithLifecycle()
    return remember(uri, sections) {
        LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = uri.trim(),
            activeThemeSections = sections,
        )
    }
}
