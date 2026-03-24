package vad.dashing.tbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.max

object TripRepository {
    const val MAX_TRIPS = 25
    const val MAX_FAVORITES = 25

    val lock = Any()

    private const val PERSIST_EPS = 1e-4f
    private const val MS_EPS = 500L

    private val _trips = MutableStateFlow<List<TripRecord>>(emptyList())
    val trips: StateFlow<List<TripRecord>> = _trips.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    /** Last value written to DataStore (full snapshot). */
    private var lastPersistedTripsJson: String = ""
    private var lastPersistedFavoritesJson: String = ""

    private val _activeTrip = MutableStateFlow<TripRecord?>(null)
    val activeTrip: StateFlow<TripRecord?> = _activeTrip.asStateFlow()

    fun setTripsFromStore(trips: List<TripRecord>, favorites: Set<String>) {
        synchronized(lock) {
            _trips.value = trips.takeLast(MAX_TRIPS)
            _favoriteIds.value = favorites.intersect(_trips.value.map { it.id }.toSet())
            lastPersistedTripsJson = tripsListToJson(_trips.value)
            lastPersistedFavoritesJson = favoritesSetToJson(_favoriteIds.value)
            _activeTrip.value = _trips.value.lastOrNull { it.isActive }
        }
    }

    fun needsPersistence(): Boolean = synchronized(lock) {
        tripsListToJson(_trips.value) != lastPersistedTripsJson ||
            favoritesSetToJson(_favoriteIds.value) != lastPersistedFavoritesJson
    }

    fun markPersisted(tripsJson: String, favoritesJson: String) {
        synchronized(lock) {
            lastPersistedTripsJson = tripsJson
            lastPersistedFavoritesJson = favoritesJson
        }
    }

    fun replaceTrip(updated: TripRecord) {
        synchronized(lock) {
            _trips.update { list ->
                val idx = list.indexOfFirst { it.id == updated.id }
                if (idx < 0) list else list.toMutableList().apply { this[idx] = updated }
            }
            if (updated.isActive) {
                _activeTrip.value = updated
            } else if (_activeTrip.value?.id == updated.id) {
                _activeTrip.value = null
            }
        }
    }

    fun appendTrip(trip: TripRecord) {
        synchronized(lock) {
            _trips.update { current ->
                (current + trip).takeLast(MAX_TRIPS)
            }
            if (trip.isActive) {
                _activeTrip.value = trip
            }
        }
    }

    fun removeTrip(id: String) {
        synchronized(lock) {
            _trips.update { it.filter { t -> t.id != id } }
            _favoriteIds.update { it - id }
            if (_activeTrip.value?.id == id) {
                _activeTrip.value = null
            }
        }
    }

    fun setFavorite(id: String, favorite: Boolean) {
        synchronized(lock) {
            if (favorite) {
                _favoriteIds.update { cur ->
                    if (cur.contains(id) || cur.size >= MAX_FAVORITES) cur else cur + id
                }
            } else {
                _favoriteIds.update { it - id }
            }
        }
    }

    fun updateActiveTrip(transform: (TripRecord) -> TripRecord) {
        synchronized(lock) {
            val cur = _activeTrip.value ?: return
            val next = transform(cur)
            _trips.update { list ->
                val idx = list.indexOfFirst { it.id == next.id }
                if (idx < 0) list else list.toMutableList().apply { this[idx] = next }
            }
            if (next.isActive) {
                _activeTrip.value = next
            } else {
                _activeTrip.value = null
            }
        }
    }

    fun startTrip(record: TripRecord) {
        synchronized(lock) {
            _trips.update { current -> (current + record).takeLast(MAX_TRIPS) }
            _activeTrip.value = record
        }
    }

    fun clearActiveTripMemory() {
        synchronized(lock) {
            _activeTrip.value = null
        }
    }

    fun averageSpeedMovingKmH(t: TripRecord): Float? {
        val d = t.distanceKm
        val sec = t.movingTimeMs / 1000f
        if (sec <= 0f || d <= 0f) return null
        return d / (sec / 3600f)
    }

    fun averageSpeedTripKmH(t: TripRecord): Float? {
        val d = t.distanceKm
        val totalMs = (t.endTimeEpochMs ?: System.currentTimeMillis()) - t.startTimeEpochMs
        val sec = totalMs / 1000f
        if (sec <= 0f || d <= 0f) return null
        return d / (sec / 3600f)
    }

    fun tripChangedEnough(a: TripRecord, b: TripRecord): Boolean {
        if (a.id != b.id) return true
        if (a.name != b.name) return true
        if (a.endTimeEpochMs != b.endTimeEpochMs) return true
        if (abs(a.distanceKm - b.distanceKm) > PERSIST_EPS) return true
        if (abs(a.maxSpeed - b.maxSpeed) > PERSIST_EPS) return true
        if (abs(a.fuelConsumedLiters - b.fuelConsumedLiters) > PERSIST_EPS) return true
        if (kotlin.math.abs(a.movingTimeMs - b.movingTimeMs) > MS_EPS) return true
        if (kotlin.math.abs(a.idleTimeMs - b.idleTimeMs) > MS_EPS) return true
        if (a.maxEngineTemp != b.maxEngineTemp) return true
        if (a.maxGearboxOilTemp != b.maxGearboxOilTemp) return true
        if (a.minOutsideTemp != b.minOutsideTemp) return true
        if (a.maxOutsideTemp != b.maxOutsideTemp) return true
        return false
    }

    fun mergeOutsideTemp(minCur: Float?, maxCur: Float?, sample: Float?): Pair<Float?, Float?> {
        if (sample == null) return Pair(minCur, maxCur)
        val min = minCur?.let { kotlin.math.min(it, sample) } ?: sample
        val max = maxCur?.let { kotlin.math.max(it, sample) } ?: sample
        return Pair(min, max)
    }

    fun updateMaxEngineTemp(current: Float?, sample: Float?): Float? {
        if (sample == null) return current
        return current?.let { max(it, sample) } ?: sample
    }

    fun updateMaxGearboxTemp(current: Int?, sample: Int?): Int? {
        if (sample == null) return current
        return current?.let { kotlin.math.max(it, sample) } ?: sample
    }
}
