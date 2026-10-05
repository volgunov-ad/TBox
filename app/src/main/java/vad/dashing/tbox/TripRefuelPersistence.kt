package vad.dashing.tbox

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.fuel.RefuelRepository
import vad.dashing.tbox.fuel.refuelsListToJson
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.trip.favoritesSetToJson
import vad.dashing.tbox.trip.tripsListToJson

/**
 * Process-wide writer for trips / refuels JSON shared by the service and UI.
 * The snapshot is taken under the lock: one built before it could be written after a newer
 * one and roll the stored list back.
 */
object TripRefuelPersistence {
    private val tripsMutex = Mutex()
    private val refuelsMutex = Mutex()

    suspend fun persistTrips(appDataManager: AppDataManager, onlyIfNeeded: Boolean) {
        tripsMutex.withLock {
            if (onlyIfNeeded && !TripRepository.needsPersistence()) return
            val tripsJson = tripsListToJson(TripRepository.trips.value)
            val favJson = favoritesSetToJson(TripRepository.favoriteIds.value)
            appDataManager.saveTripsJson(tripsJson)
            appDataManager.saveTripFavoritesJson(favJson)
            TripRepository.markPersisted(tripsJson, favJson)
        }
    }

    suspend fun persistRefuels(appDataManager: AppDataManager, onlyIfNeeded: Boolean) {
        refuelsMutex.withLock {
            if (onlyIfNeeded && !RefuelRepository.needsPersistence()) return
            val refuelsJson = refuelsListToJson(RefuelRepository.refuels.value)
            appDataManager.saveRefuelsJson(refuelsJson)
            RefuelRepository.markPersisted(refuelsJson)
        }
    }
}
