package vad.dashing.tbox

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TboxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MainActivityForegroundTracker.register(this)
        val appDataManager = AppDataManager(this)
        val settingsManager = SettingsManager(this)
        runBlocking {
            settingsManager.migrateSelectedTabIndexIfNeeded()
            val saved = appDataManager.motorHoursFlow.first()
            CarDataRepository.setMotorHours(saved)
            CarDataRepository.markPersisted(saved)
            val tripsJson = appDataManager.tripsJsonFlow.first()
            val favJson = appDataManager.tripFavoritesJsonFlow.first()
            TripRepository.setTripsFromStore(
                tripsListFromJson(tripsJson),
                favoritesSetFromJson(favJson)
            )
        }
    }
}
