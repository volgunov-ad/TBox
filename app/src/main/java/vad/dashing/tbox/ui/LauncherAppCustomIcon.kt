package vad.dashing.tbox.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.decodeFileToOwnedScaledImageBitmap
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

private fun decodeAndScaleIconFile(file: File, iconSizePx: Int): ImageBitmap? =
    decodeFileToOwnedScaledImageBitmap(file, iconSizePx)

@Composable
fun rememberLauncherAppIconLookup(settingsViewModel: SettingsViewModel): LauncherAppIconPaths.Lookup {
    val uri by settingsViewModel.activeThemeUri.collectAsStateWithLifecycle()
    val targets by settingsViewModel.activeThemeApplyTargets.collectAsStateWithLifecycle()
    return remember(uri, targets) {
        LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = uri.trim(),
            activeThemeApplyTargets = targets,
        )
    }
}

fun launcherAppIconRemoveLabelRes(
    filesDir: File,
    packageName: String,
    lookup: LauncherAppIconPaths.Lookup,
): Int =
    if (LauncherAppIconPaths.hasThemeCacheIcon(filesDir, packageName, lookup)) {
        R.string.widget_app_launcher_remove_icon_from_theme
    } else {
        R.string.widget_app_launcher_remove_icon
    }
