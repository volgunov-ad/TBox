package vad.dashing.tbox

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.fuel.RefuelRepository
import vad.dashing.tbox.fuel.refuelsListFromJson
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.trip.favoritesSetFromJson
import vad.dashing.tbox.trip.tripsListFromJson

/**
 * Loads app-data snapshot once per process (single DataStore read + parse) and hydrates repositories.
 * Critical path: motor hours + trips/favorites. Refuels are deferred until [ensureRefuelsLoaded].
 */
object StartupRepositoryLoader {
    private val loadMutex = Mutex()
    private var cachedSnapshot: AppDataStartupSnapshot? = null
    private var refuelsApplied = false

    fun getCachedSnapshot(): AppDataStartupSnapshot? = cachedSnapshot

    fun areRefuelsLoaded(): Boolean = refuelsApplied

    fun invalidate() {
        cachedSnapshot = null
        refuelsApplied = false
    }

    internal fun applyCritical(snapshot: AppDataStartupSnapshot) {
        CarDataRepository.setMotorHours(snapshot.motorHours)
        CarDataRepository.markPersisted(snapshot.motorHours)
        TripRepository.setTripsFromStore(
            tripsListFromJson(snapshot.tripsJson),
            favoritesSetFromJson(snapshot.tripFavoritesJson),
        )
        TripRepository.ensurePersistentTrip(defaultName = "")
    }

    internal fun applyRefuels(snapshot: AppDataStartupSnapshot) {
        RefuelRepository.setRefuelsFromStore(refuelsListFromJson(snapshot.refuelsJson))
        refuelsApplied = true
    }

    /** Application / service bootstrap: one disk read unless cache is warm. */
    suspend fun ensureCriticalLoaded(appDataManager: AppDataManager): AppDataStartupSnapshot {
        cachedSnapshot?.let { return it }
        return loadMutex.withLock {
            cachedSnapshot?.let { return it }
            StartupLoadTimings.mark("data_read_begin")
            val snapshot = appDataManager.readStartupSnapshot()
            StartupLoadTimings.mark("data_read_done")
            StartupLoadTimings.mark("trips_parse_begin")
            applyCritical(snapshot)
            StartupLoadTimings.mark("trips_parse_done")
            cachedSnapshot = snapshot
            snapshot
        }
    }

    /** Non-critical refuels load; uses cached snapshot when available. */
    suspend fun ensureRefuelsLoaded(appDataManager: AppDataManager) {
        if (refuelsApplied) return
        loadMutex.withLock {
            if (refuelsApplied) return
            val snapshot = cachedSnapshot ?: run {
                StartupLoadTimings.mark("refuels_read_begin")
                val read = appDataManager.readStartupSnapshot()
                StartupLoadTimings.mark("refuels_read_done")
                cachedSnapshot = read
                read
            }
            StartupLoadTimings.mark("refuels_parse_begin")
            applyRefuels(snapshot)
            StartupLoadTimings.mark("refuels_parse_done")
        }
    }

    /** Force reload from disk (backup import / explicit reload action). */
    suspend fun reloadAllFromStore(appDataManager: AppDataManager): AppDataStartupSnapshot {
        return loadMutex.withLock {
            invalidate()
            StartupLoadTimings.mark("data_read_begin")
            val snapshot = appDataManager.readStartupSnapshot()
            StartupLoadTimings.mark("data_read_done")
            StartupLoadTimings.mark("trips_parse_begin")
            applyCritical(snapshot)
            StartupLoadTimings.mark("trips_parse_done")
            cachedSnapshot = snapshot
            StartupLoadTimings.mark("refuels_parse_begin")
            applyRefuels(snapshot)
            StartupLoadTimings.mark("refuels_parse_done")
            snapshot
        }
    }

    internal fun resetForUnitTests() {
        cachedSnapshot = null
        refuelsApplied = false
    }
}
