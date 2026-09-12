package vad.dashing.tbox

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.LaunchableAppsCatalog

class TboxApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
        // Keep launchable-app pickers in sync when packages are installed/removed while
        // TBox Monitor stays alive (otherwise the in-process icon list stays stale until restart).
        LaunchableAppsCatalog.ensurePackageChangeWatcher(this)
        MainActivityForegroundTracker.register(this)
        val appDataManager = AppDataManager(this)
        val settingsManager = SettingsManager(this)
        applicationScope.launch {
            settingsManager.headUnitCanModeFlow.collectLatest { mode ->
                UniversalCanRepository.setMode(mode)
            }
        }
        applicationScope.launch {
            settingsManager.launchMainInStockAppWindowFlow.collectLatest { enabled ->
                LaunchMainInStockAppWindowSetting.update(enabled)
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
