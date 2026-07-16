package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThemeApply {

    data class ApplyResult(
        val sections: Set<ThemeSection>,
        val applyTargets: Set<ThemeApplyTarget> = emptySet(),
        val iconsImported: Int,
        val tileBackgroundsImported: Int,
        val httpRequestIconsImported: Int = 0,
    )

    suspend fun applyFromUri(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        uriString: String,
        applyTargets: Set<ThemeApplyTarget>? = null,
    ): Result<ApplyResult> {
        val trimmed = uriString.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("theme_uri_empty"))
        }
        if (!ThemeOpenIntentParser.isTboxThemeUri(trimmed)) {
            return Result.failure(IllegalArgumentException("not_tboxtheme_file"))
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
            applyTargets = applyTargets,
        )
    }

    suspend fun applyBytes(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        bytes: ByteArray,
        sourceUri: String,
        applyTargets: Set<ThemeApplyTarget>? = null,
    ): Result<ApplyResult> {
        return materializeAndActivateFromBytes(
            context = context,
            settingsManager = settingsManager,
            settingsViewModel = settingsViewModel,
            bytes = bytes,
            sourceUri = sourceUri,
            cacheKey = ThemeCacheKeys.resolveUniqueManualCacheKey(context, sourceUri),
            applyTargets = applyTargets,
        )
    }

    suspend fun materializeDriveModeThemeFromUri(
        context: Context,
        settingsManager: SettingsManager,
        rawValue: Int,
        sourceUri: String,
        applyTargets: Set<ThemeApplyTarget>? = null,
    ): Result<ThemeMaterialization.MaterializeResult> {
        val trimmed = sourceUri.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("theme_uri_empty"))
        }
        if (!ThemeOpenIntentParser.isTboxThemeUri(trimmed)) {
            return Result.failure(IllegalArgumentException("not_tboxtheme_file"))
        }
        if (!ThemeFileResolver.isAccessible(context, trimmed)) {
            return Result.failure(IllegalArgumentException("theme_file_not_found"))
        }
        val bytes = ThemeFileResolver.openBytes(context, trimmed)
            ?: return Result.failure(IllegalArgumentException("theme_file_not_readable"))
        val cacheKey = ThemeCacheKeys.driveModeCacheKey(rawValue)
        return settingsManager.runWithThemeActivation {
            withContext(Dispatchers.IO) {
                ThemeMaterialization.materializeFromBytes(
                    context = context,
                    bytes = bytes,
                    cacheKey = cacheKey,
                    sourceUri = trimmed,
                    syncExisting = ThemeMaterialization.isMaterialized(context, cacheKey),
                    applyTargets = applyTargets,
                )
            }
        }
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
        applyTargets: Set<ThemeApplyTarget>? = null,
    ): Result<ApplyResult> {
        return ThemeMaterialization.materializeAndActivateFromCache(
            context = context,
            settingsManager = settingsManager,
            settingsViewModel = settingsViewModel,
            bytes = bytes,
            cacheKey = cacheKey,
            sourceUri = sourceUri,
            syncExisting = ThemeMaterialization.isMaterialized(context, cacheKey),
            applyTargets = applyTargets,
        )
    }

    fun peekAvailableApplyTargets(bytes: ByteArray): Result<Set<ThemeApplyTarget>> =
        runCatching {
            val parsed = ThemeBundleExport.parseBundleBytes(bytes).getOrThrow()
            ThemeApplyTargetAvailability.detectAvailable(parsed)
        }
}
