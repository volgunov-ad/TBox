package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.flow.first

object ThemeApply {

    data class ApplyResult(
        val sections: Set<ThemeSection>,
        val iconsImported: Int,
        val tileBackgroundsImported: Int,
    )

    suspend fun applyBytes(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        bytes: ByteArray,
        themeUriForActive: String,
    ): Result<ApplyResult> {
        val extracted = ThemeBundleExport.extractBundle(context, bytes)
        if (extracted.isFailure) {
            return Result.failure(extracted.exceptionOrNull() ?: IllegalArgumentException("theme_extract_failed"))
        }
        val bundle = extracted.getOrThrow()
        val importResult = ThemeLayoutExport.importJson(context, settingsManager, bundle.themeJson)
        if (importResult.isFailure) {
            return Result.failure(importResult.exceptionOrNull() ?: IllegalArgumentException("theme_import_failed"))
        }
        val sections = importResult.getOrThrow()

        bundle.lightWallpaperDir?.let { dir ->
            settingsViewModel?.saveMainScreenWallpaperLightFolderUri("file://${dir.absolutePath}")
                ?: settingsManager.saveMainScreenWallpaperLightFolderUri("file://${dir.absolutePath}")
        }
        bundle.darkWallpaperDir?.let { dir ->
            settingsViewModel?.saveMainScreenWallpaperDarkFolderUri("file://${dir.absolutePath}")
                ?: settingsManager.saveMainScreenWallpaperDarkFolderUri("file://${dir.absolutePath}")
        }

        if (bundle.iconsImported > 0) {
            settingsManager.bumpLauncherAppIconRevision()
        }
        if (bundle.tileBackgroundsImported > 0) {
            settingsManager.bumpTileBackgroundImageRevision()
        }

        val fingerprint = ThemeFingerprint.compute(context, settingsManager, sections)
        settingsManager.saveActiveTheme(
            uri = themeUriForActive,
            fingerprint = fingerprint,
            sections = sections,
        )

        return Result.success(
            ApplyResult(
                sections = sections,
                iconsImported = bundle.iconsImported,
                tileBackgroundsImported = bundle.tileBackgroundsImported,
            ),
        )
    }

    suspend fun applyFromUri(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        uriString: String,
    ): Result<ApplyResult> {
        val trimmed = uriString.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("theme_uri_empty"))
        }
        if (!ThemeFileResolver.isAccessible(context, trimmed)) {
            return Result.failure(IllegalArgumentException("theme_file_not_found"))
        }
        val bytes = ThemeFileResolver.openBytes(context, trimmed)
            ?: return Result.failure(IllegalArgumentException("theme_file_not_readable"))
        return applyBytes(context, settingsManager, settingsViewModel, bytes, trimmed)
    }
}
