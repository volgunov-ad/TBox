package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository

class DriveModeThemeWatcher(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope,
) {
    private var lastAppliedUri: String? = null
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
                Triple(paths, drive, wet)
            }
                .distinctUntilChanged()
                .collect { (paths, drive, wet) ->
                    if (paths.isEmpty()) return@collect
                    val key = resolveDriveModeThemeKey(drive, wet) ?: return@collect
                    val uri = paths[key]?.trim().orEmpty()
                    if (uri.isEmpty()) return@collect
                    if (uri == lastAppliedUri && key == lastAppliedKey) return@collect
                    if (!ThemeFileResolver.isAccessible(context, uri)) return@collect
                    withContext(Dispatchers.IO) {
                        val result = ThemeApply.applyFromUri(
                            context = context,
                            settingsManager = settingsManager,
                            settingsViewModel = null,
                            uriString = uri,
                        )
                        if (result.isSuccess) {
                            lastAppliedUri = uri
                            lastAppliedKey = key
                        }
                    }
                }
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
            lastAppliedUri = null
            lastAppliedKey = null
        }
    }

    companion object {
        const val SOURCE_ID = "theme_drive_mode"

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
