package vad.dashing.tbox

import android.content.Context

object ThemeApply {

    data class ApplyResult(
        val sections: Set<ThemeSection>,
        val iconsImported: Int,
        val tileBackgroundsImported: Int,
    )

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
        return materializeAndActivateFromBytes(
            context = context,
            settingsManager = settingsManager,
            settingsViewModel = settingsViewModel,
            bytes = bytes,
            sourceUri = trimmed,
            cacheKey = ThemeCacheKeys.resolveUniqueManualCacheKey(context, trimmed),
        )
    }

    suspend fun applyBytes(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        bytes: ByteArray,
        sourceUri: String,
    ): Result<ApplyResult> {
        return materializeAndActivateFromBytes(
            context = context,
            settingsManager = settingsManager,
            settingsViewModel = settingsViewModel,
            bytes = bytes,
            sourceUri = sourceUri,
            cacheKey = ThemeCacheKeys.resolveUniqueManualCacheKey(context, sourceUri),
        )
    }

    suspend fun materializeDriveModeThemeFromUri(
        context: Context,
        rawValue: Int,
        sourceUri: String,
    ): Result<ThemeMaterialization.MaterializeResult> {
        val trimmed = sourceUri.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("theme_uri_empty"))
        }
        if (!ThemeFileResolver.isAccessible(context, trimmed)) {
            return Result.failure(IllegalArgumentException("theme_file_not_found"))
        }
        val bytes = ThemeFileResolver.openBytes(context, trimmed)
            ?: return Result.failure(IllegalArgumentException("theme_file_not_readable"))
        val cacheKey = ThemeCacheKeys.driveModeCacheKey(rawValue)
        return ThemeMaterialization.materializeFromBytes(
            context = context,
            bytes = bytes,
            cacheKey = cacheKey,
            sourceUri = trimmed,
            syncExisting = ThemeMaterialization.isMaterialized(context, cacheKey),
        )
    }

    suspend fun activateFromCache(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        cacheKey: String,
    ): Result<ApplyResult> {
        return ThemeMaterialization.activateFromCache(
            context = context,
            settingsManager = settingsManager,
            settingsViewModel = settingsViewModel,
            cacheKey = cacheKey,
        )
    }

    private suspend fun materializeAndActivateFromBytes(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        bytes: ByteArray,
        sourceUri: String,
        cacheKey: String,
    ): Result<ApplyResult> {
        val materialized = ThemeMaterialization.materializeFromBytes(
            context = context,
            bytes = bytes,
            cacheKey = cacheKey,
            sourceUri = sourceUri,
            syncExisting = ThemeMaterialization.isMaterialized(context, cacheKey),
        )
        if (materialized.isFailure) {
            return Result.failure(
                materialized.exceptionOrNull() ?: IllegalArgumentException("theme_materialize_failed"),
            )
        }
        return activateFromCache(context, settingsManager, settingsViewModel, cacheKey)
    }
}
