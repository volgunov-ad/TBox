package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository

class DriveModeThemeWatcher(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope,
) {
    private var lastAppliedCacheKey: String? = null
    private var lastAppliedKey: Int? = null
    private val activationMutex = Mutex()

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
                    withContext(Dispatchers.IO) {
                        activationMutex.withLock {
                            applyActivationRequest(request)
                        }
                    }
                }
        }
    }

    private suspend fun applyActivationRequest(request: DriveModeThemeActivationRequest) {
        val cacheKey = request.cacheKey
        val key = request.modeRawValue
        val sourceUri = request.sourceUri
        if (!ThemeMaterialization.isMaterialized(context, cacheKey)) {
            if (!ThemeFileResolver.isAccessible(context, sourceUri)) return
            val materialized = ThemeApply.materializeDriveModeThemeFromUri(
                context = context,
                rawValue = key,
                sourceUri = sourceUri,
            )
            if (materialized.isFailure) return
        }
        if (!ThemeMaterialization.isMaterialized(context, cacheKey)) return
        val result = ThemeApply.activateFromCache(
            context = context,
            settingsManager = settingsManager,
            settingsViewModel = null,
            cacheKey = cacheKey,
        )
        if (result.isSuccess) {
            lastAppliedCacheKey = cacheKey
            lastAppliedKey = key
        }
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
        const val DRIVE_MODE_THEME_ACTIVATION_DEBOUNCE_MS = 2_000L

        internal data class DriveModeThemeActivationRequest(
            val modeRawValue: Int,
            val sourceUri: String,
            val cacheKey: String,
        )

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
