package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import java.io.File

class DriveModeThemeWatcher(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope,
) {
    private var lastAppliedCacheKey: String? = null
    private var lastAppliedKey: Int? = null

    fun start() {
        scope.launch {
            settingsManager.driveModeThemePathsFlow
                .distinctUntilChanged()
                .collect { paths ->
                    updateCanInterest(paths)
                }
        }
        scope.launch {
            combine(
                settingsManager.driveModeThemePathsFlow,
                UniversalCanRepository.carSettingsDriveMode,
                UniversalCanRepository.carSettingsDriveMode6dctWet,
            ) { paths, drive, wet ->
                resolveActivationRequest(paths, drive, wet)
            }
                .distinctUntilChanged()
                .debounce(DRIVE_MODE_THEME_ACTIVATION_DEBOUNCE_MS)
                .collect { request ->
                    if (request == null) return@collect
                    if (request.cacheKey == lastAppliedCacheKey && request.modeRawValue == lastAppliedKey) {
                        return@collect
                    }
                    ThemeActivationCoordinator.awaitMainScreenUiReady()
                    if (!ThemeMaterialization.isMaterialized(context, request.cacheKey)) {
                        return@collect
                    }
                    val manifest = ThemeMaterialization.readManifest(context, request.cacheKey)
                    if (isDriveModeThemeAlreadyApplied(
                            request = request,
                            activeThemeUri = settingsManager.activeThemeUriFlow.first(),
                            activeThemeFingerprint = settingsManager.activeThemeFingerprintFlow.first(),
                            manifest = manifest,
                        ) && isDriveModeWallpaperSelectionApplied(
                            context = context,
                            request = request,
                            settingsManager = settingsManager,
                        )
                    ) {
                        rememberApplied(request)
                        return@collect
                    }
                    applyActivationRequest(request)
                }
        }
    }

    private suspend fun applyActivationRequest(request: DriveModeThemeActivationRequest) {
        val cacheKey = request.cacheKey
        val sourceUri = request.sourceUri
        val result = ThemeMaterialization.materializeAndActivateDriveModeFromCache(
            context = context,
            settingsManager = settingsManager,
            cacheKey = cacheKey,
            sourceUri = sourceUri,
        )
        if (result.isSuccess) {
            rememberApplied(request)
        }
    }

    private fun rememberApplied(request: DriveModeThemeActivationRequest) {
        lastAppliedCacheKey = request.cacheKey
        lastAppliedKey = request.modeRawValue
    }

    private suspend fun updateCanInterest(paths: Map<Int, String>) {
        if (paths.values.any { it.trim().isNotEmpty() }) {
            UniversalCanRepository.setSourceSignals(
                SOURCE_ID,
                setOf(MbCanSignal.CarSettingsVehicleParams),
            )
        } else {
            UniversalCanRepository.enqueueClearSource(SOURCE_ID)
            lastAppliedCacheKey = null
            lastAppliedKey = null
        }
    }

    companion object {
        const val SOURCE_ID = "theme_drive_mode"
        const val DRIVE_MODE_THEME_ACTIVATION_DEBOUNCE_MS = 1_000L

        internal data class DriveModeThemeActivationRequest(
            val modeRawValue: Int,
            val sourceUri: String,
            val cacheKey: String,
        )

        internal fun isDriveModeThemeAlreadyApplied(
            request: DriveModeThemeActivationRequest,
            activeThemeUri: String,
            activeThemeFingerprint: String,
            manifest: ThemeMaterialization.ThemeManifest?,
        ): Boolean {
            val activeCacheKey = activeThemeUri.trim()
            val activeFingerprint = activeThemeFingerprint.trim()
            val manifestCacheKey = manifest?.cacheKey?.trim().orEmpty()
            val manifestFingerprint = manifest?.fingerprint?.trim().orEmpty()
            return activeCacheKey == request.cacheKey &&
                manifestCacheKey == request.cacheKey &&
                activeFingerprint.isNotEmpty() &&
                manifestFingerprint.isNotEmpty() &&
                activeFingerprint == manifestFingerprint
        }

        internal suspend fun isDriveModeWallpaperSelectionApplied(
            context: Context,
            request: DriveModeThemeActivationRequest,
            settingsManager: SettingsManager,
        ): Boolean {
            val cacheDir = ThemeMaterialization.cacheDir(context, request.cacheKey)
            val themeJsonFile = File(cacheDir, ThemeMaterialization.THEME_JSON_FILE)
            if (!themeJsonFile.isFile) return false
            return wallpaperSelectionMatchesCache(
                cacheDir = cacheDir,
                themeJson = themeJsonFile.readText(),
                actual = settingsManager.mainScreenWallpaperSelectionByPageFlow.first(),
            )
        }

        internal fun wallpaperSelectionMatchesCache(
            cacheDir: File,
            themeJson: String,
            actual: MainScreenWallpaperSelectionsByPage,
        ): Boolean {
            val expected = ThemeRuntimeState.resolveWallpaperSelectionsForActivation(cacheDir, themeJson)
            return expected == actual
        }

        internal fun resolveActivationRequest(
            paths: Map<Int, String>,
            drive: Int?,
            wet6dct: Int?,
        ): DriveModeThemeActivationRequest? {
            if (paths.isEmpty()) return null
            val key = resolveDriveModeThemeKey(drive, wet6dct) ?: return null
            val sourceUri = paths[key]?.trim().orEmpty()
            if (sourceUri.isEmpty()) return null
            return DriveModeThemeActivationRequest(
                modeRawValue = key,
                sourceUri = sourceUri,
                cacheKey = ThemeCacheKeys.driveModeCacheKey(key),
            )
        }

        fun resolveDriveModeThemeKey(drive: Int?, wet6dct: Int?): Int? {
            DRIVE_MODE_WIDGET_OPTIONS
                .filter { it.propertyId == MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE }
                .firstOrNull { it.propertyValue == drive }
                ?.let { return it.rawValue }
            DRIVE_MODE_WIDGET_OPTIONS
                .filter { it.propertyId == MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET }
                .firstOrNull { it.propertyValue == wet6dct }
                ?.let { return it.rawValue }
            return null
        }
    }
}
