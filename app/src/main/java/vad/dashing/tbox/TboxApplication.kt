package vad.dashing.tbox

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.UniversalCanRepository

class TboxApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
        MainActivityForegroundTracker.register(this)
        val appDataManager = AppDataManager(this)
        val settingsManager = SettingsManager(this)
        applicationScope.launch {
            settingsManager.headUnitCanModeFlow.collectLatest { mode ->
                UniversalCanRepository.setMode(mode)
            }
        }
        applicationScope.launch {
            try {
                settingsManager.reconcileSelectedTabWithMenuLayoutIfNeeded()
            } catch (_: Exception) {
            }
            try {
                settingsManager.migrateMainScreenWallpaperFilesToFolderUrisIfNeeded()
            } catch (_: Exception) {
            }
            try {
                StartupRepositoryLoader.ensureCriticalLoaded(appDataManager)
                StartupLoadTimings.log("Timings.application_startup_data")
            } catch (_: Exception) {
                // [BackgroundService.onCreate] reloads trips; motor hours stay at default until service.
            }
        }
    }
}
