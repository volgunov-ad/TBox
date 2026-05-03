package vad.dashing.tbox

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.trip.favoritesSetFromJson
import vad.dashing.tbox.trip.tripsListFromJson

class TboxApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        MainActivityForegroundTracker.register(this)
        val appDataManager = AppDataManager(this)
        val settingsManager = SettingsManager(this)
        applicationScope.launch {
            try {
                settingsManager.migrateSelectedTabIndexIfNeeded()
            } catch (_: Exception) {
                // Non-fatal; settings screen can retry migrations if needed.
            }
            try {
                settingsManager.migrateMainScreenWallpaperFilesToFolderUrisIfNeeded()
            } catch (_: Exception) {
            }
            try {
                coroutineScope {
                    val motorDeferred = async(Dispatchers.IO) {
                        appDataManager.motorHoursFlow.first()
                    }
                    val tripsDeferred = async(Dispatchers.IO) {
                        appDataManager.tripsJsonFlow.first()
                    }
                    val favDeferred = async(Dispatchers.IO) {
                        appDataManager.tripFavoritesJsonFlow.first()
                    }
                    val saved = motorDeferred.await()
                    CarDataRepository.setMotorHours(saved)
                    CarDataRepository.markPersisted(saved)
                    TripRepository.setTripsFromStore(
                        tripsListFromJson(tripsDeferred.await()),
                        favoritesSetFromJson(favDeferred.await())
                    )
                }
            } catch (_: Exception) {
                // [BackgroundService.onCreate] reloads trips; motor hours stay at default until service.
            }
        }
    }
}
