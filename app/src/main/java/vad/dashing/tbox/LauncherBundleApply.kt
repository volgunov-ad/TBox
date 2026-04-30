package vad.dashing.tbox

import android.content.Context

/**
 * Applies a launcher design bundle (ZIP bytes): layout JSON, icons, wallpapers.
 * Shared by [MainActivity] and [SettingsViewModel] (e.g. drive-mode presets on the main screen).
 */
object LauncherBundleApply {

    suspend fun applyBytes(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        bytes: ByteArray,
    ): Result<Int> {
        val extracted = LauncherBundleExport.extractBundle(context, bytes)
        if (extracted.isFailure) {
            return Result.failure(extracted.exceptionOrNull() ?: IllegalArgumentException("bundle_extract_failed"))
        }
        val bundleResult = extracted.getOrThrow()
        val importResult = LauncherLayoutExport.importJson(context, settingsManager, bundleResult.layoutJson)
        if (importResult.isFailure) {
            return Result.failure(importResult.exceptionOrNull() ?: IllegalArgumentException("layout_import_failed"))
        }

        bundleResult.lightWallpaperDir?.let {
            settingsViewModel?.saveMainScreenWallpaperLightFolderUri("file://${it.absolutePath}")
        }
        bundleResult.darkWallpaperDir?.let {
            settingsViewModel?.saveMainScreenWallpaperDarkFolderUri("file://${it.absolutePath}")
        }
        return Result.success(bundleResult.iconsImported)
    }
}
