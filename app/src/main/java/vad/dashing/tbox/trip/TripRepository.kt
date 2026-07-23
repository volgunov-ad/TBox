package vad.dashing.tbox.trip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class TripResumeStartResult(
    val resumed: Boolean,
)

object TripRepository {
    const val MAX_TRIPS = 31
    const val MAX_FAVORITES = 25

    val lock = Any()

    /**
     * When false, periodic (1 s) trip accounting ([vad.dashing.tbox.BackgroundService.onTripPeriodicSample]) is skipped.
     * Disk load and [responseWork] gating use separate flags in the service.
     */
    @Volatile
    private var tripsProcessingEnabled: Boolean = true

    fun setTripsProcessingEnabled(enabled: Boolean) {
        tripsProcessingEnabled = enabled
    }

    fun isTripsProcessingEnabled(): Boolean = tripsProcessingEnabled

    /** Clears in-memory state without org.json (JVM unit tests use stubbed android JSON). */
    internal fun resetForUnitTests() {
        tripsProcessingEnabled = true
        synchronized(lock) {
            _trips.value = emptyList()
            _favoriteIds.value = emptySet()
            _activeTrip.value = null
            lastPersistedTripsJson = "[]"
            lastPersistedFavoritesJson = "[]"
        }
    }

    private const val PERSIST_EPS = 1e-4f
    private const val MS_EPS = 5000L
    /** Avoid persisting on every tiny % tick from the fuel sender. */
    private const val FUEL_BASELINE_PERCENT_EPS = 1.5f
    private const val FUEL_BASELINE_LITERS_EPS = 0.75f

    private val _trips = MutableStateFlow<List<TripRecord>>(emptyList())
    val trips: StateFlow<List<TripRecord>> = _trips.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    /** Last value written to DataStore (full snapshot). */
    private var lastPersistedTripsJson: String = ""
    private var lastPersistedFavoritesJson: String = ""

    private val _activeTrip = MutableStateFlow<TripRecord?>(null)
    val activeTrip: StateFlow<TripRecord?> = _activeTrip.asStateFlow()

    fun persistentTrip(): TripRecord? = _trips.value.firstOrNull { it.isPersistent }

    fun setTripsFromStore(trips: List<TripRecord>, favorites: Set<String>) {
        synchronized(lock) {
            _trips.value = normalizeTripsList(applyListLimit(trips))
            _favoriteIds.value = favorites.intersect(_trips.value.map { it.id }.toSet())
            lastPersistedTripsJson = tripsListToJson(_trips.value)
            lastPersistedFavoritesJson = favoritesSetToJson(_favoriteIds.value)
            refreshActiveTripLocked()
        }
    }

    /**
     * Ensures exactly one live persistent (daily) trip exists.
     * Call after loading from store / on service start. Safe to call repeatedly.
     */
    fun ensurePersistentTrip(
        defaultName: String,
        nowMs: Long = System.currentTimeMillis(),
        odometerStartKm: UInt? = null,
        fuelBaselinePercent: Float? = null,
        fuelBaselineLiters: Float? = null,
    ) {
        synchronized(lock) {
            val lives = _trips.value.filter { it.isPersistent }
            if (lives.size == 1 && lives.first().isActive) {
                val live = lives.first()
                if (live.name.isBlank() && defaultName.isNotBlank()) {
                    _trips.update { list ->
                        list.map { t ->
                            if (t.id == live.id) t.copy(name = defaultName) else t
                        }
                    }
                }
                return
            }
            val keptName = lives.maxByOrNull { it.startTimeEpochMs }?.name?.takeIf { it.isNotBlank() }
                ?: defaultName
            val withoutBrokenLive = _trips.value.map { t ->
                if (t.isPersistent && t.isActive) {
                    t.copy(
                        endTimeEpochMs = nowMs.coerceAtLeast(t.startTimeEpochMs),
                        isPersistent = false,
                        originPersistent = true,
                    )
                } else {
                    t
                }
            }
            val fresh = TripRecord(
                name = keptName,
                startTimeEpochMs = nowMs,
                endTimeEpochMs = null,
                odometerStartKm = odometerStartKm,
                fuelBaselinePercent = fuelBaselinePercent,
                fuelBaselineLiters = fuelBaselineLiters,
                isPersistent = true,
                originPersistent = true,
                lastSampleWallMs = nowMs,
            )
            _trips.value = normalizeTripsList(applyListLimit(withoutBrokenLive + fresh))
            refreshActiveTripLocked()
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

    /** Replaces one trip by id in the list; updates [activeTrip] if the record is active or was active. */
    fun replaceTrip(updated: TripRecord) {
        synchronized(lock) {
            val list = _trips.value
            val idx = list.indexOfFirst { it.id == updated.id }
            val merged = if (idx >= 0) {
                updated.copy(odometerStartKm = list[idx].odometerStartKm)
            } else {
                updated
            }
            _trips.update { cur ->
                val i = cur.indexOfFirst { it.id == merged.id }
                if (i < 0) cur else cur.toMutableList().apply { this[i] = merged }
            }
            refreshActiveTripLocked()
        }
    }

    fun appendTrip(trip: TripRecord) {
        synchronized(lock) {
            _trips.update { current ->
                applyListLimit(current + trip)
            }
            if (trip.isCurrentActive) {
                _activeTrip.value = trip
            }
        }
    }

    fun removeTrip(id: String) {
        synchronized(lock) {
            val target = _trips.value.firstOrNull { it.id == id } ?: return
            if (target.isPersistent) return
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
                val trip = _trips.value.firstOrNull { it.id == id } ?: return
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
            if (cur.isPersistent) return
            val transformed = transform(cur)
            // First non-null wins: allow backfilling start odometer when CAN was late; never overwrite once set.
            val mergedOdo = cur.odometerStartKm ?: transformed.odometerStartKm
            val next = transformed.copy(odometerStartKm = mergedOdo, isPersistent = false)
            _trips.update { list ->
                val idx = list.indexOfFirst { it.id == next.id }
                if (idx < 0) list else list.toMutableList().apply { this[idx] = next }
            }
            if (next.isCurrentActive) {
                _activeTrip.value = next
            } else {
                _activeTrip.value = null
            }
        }
    }

    fun updatePersistentTrip(transform: (TripRecord) -> TripRecord) {
        synchronized(lock) {
            val cur = _trips.value.firstOrNull { it.isPersistent } ?: return
            val transformed = transform(cur)
            val mergedOdo = cur.odometerStartKm ?: transformed.odometerStartKm
            val next = transformed.copy(
                odometerStartKm = mergedOdo,
                isPersistent = true,
                originPersistent = true,
                endTimeEpochMs = null,
            )
            _trips.update { list ->
                val idx = list.indexOfFirst { it.id == next.id }
                if (idx < 0) list else list.toMutableList().apply { this[idx] = next }
            }
        }
    }

    /**
     * Archives the live daily trip into normal history and starts a fresh live persistent trip.
     * Archived segment keeps [TripRecord.originPersistent] so it never resumes as current.
     */
    fun resetPersistentTrip(
        nowMs: Long = System.currentTimeMillis(),
        defaultName: String,
        odometerStartKm: UInt? = null,
        fuelBaselinePercent: Float? = null,
        fuelBaselineLiters: Float? = null,
    ) {
        synchronized(lock) {
            val live = _trips.value.firstOrNull { it.isPersistent } ?: run {
                ensurePersistentTripLocked(
                    defaultName = defaultName,
                    nowMs = nowMs,
                    odometerStartKm = odometerStartKm,
                    fuelBaselinePercent = fuelBaselinePercent,
                    fuelBaselineLiters = fuelBaselineLiters,
                )
                return
            }
            val carriedName = live.name.trim().ifEmpty { defaultName }
            val archived = live.copy(
                endTimeEpochMs = nowMs.coerceAtLeast(live.startTimeEpochMs),
                isPersistent = false,
                originPersistent = true,
                lastSampleWallMs = null,
            )
            val fresh = TripRecord(
                name = carriedName,
                startTimeEpochMs = nowMs,
                endTimeEpochMs = null,
                odometerStartKm = odometerStartKm,
                fuelBaselinePercent = fuelBaselinePercent,
                fuelBaselineLiters = fuelBaselineLiters,
                isPersistent = true,
                originPersistent = true,
                lastSampleWallMs = nowMs,
            )
            val withoutLive = _trips.value.filter { it.id != live.id }
            _trips.value = normalizeTripsList(applyListLimit(withoutLive + archived + fresh))
            refreshActiveTripLocked()
        }
    }

    /**
     * Appends a new active trip and closes any other **current** active trip with a wall-clock end time.
     * Does not close the live persistent (daily) trip.
     * Caller (e.g. [vad.dashing.tbox.BackgroundService]) sets [TripRecord.startTimeEpochMs] and odometer/fuel baseline.
     */
    fun startTrip(record: TripRecord) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val toStart = record.copy(isPersistent = false)
            _trips.update { current ->
                val closed = current.map { t ->
                    if (t.isCurrentActive && t.id != toStart.id) {
                        t.copy(endTimeEpochMs = now.coerceAtLeast(t.startTimeEpochMs))
                    } else {
                        t
                    }
                }
                applyListLimit(closed + toStart)
            }
            _activeTrip.value = toStart
        }
    }

    fun clearActiveTripMemory() {
        synchronized(lock) {
            _activeTrip.value = null
        }
    }

    /**
     * When the service starts, continue a trip only if it is already active in storage.
     * Ended trips inside the split window stay closed until the first RPM>0 sample confirms continuation.
     */
    fun tryResumeLastTripAfterServiceStart(splitWindowMs: Long): TripResumeStartResult {
        synchronized(lock) {
            val list = _trips.value
            if (list.isEmpty()) return TripResumeStartResult(false)
            val now = System.currentTimeMillis()
            val candidate = TripRules.findResumeCandidate(list, now, splitWindowMs)
                ?: return TripResumeStartResult(false)
            if (!candidate.isCurrentActive ||
                !TripRules.shouldResumeLastTripOnColdStart(candidate, now, splitWindowMs)
            ) {
                return TripResumeStartResult(false)
            }
            _trips.update { cur ->
                val mapped = cur.map { t ->
                    if (t.id == candidate.id) candidate else t
                }
                normalizeTripsList(mapped)
            }
            refreshActiveTripLocked()
            return TripResumeStartResult(true)
        }
    }

    /**
     * Most recently finished **current** trip (excludes daily live/archive), for UI when there is no active trip.
     */
    fun latestFinishedTrip(trips: List<TripRecord>): TripRecord? =
        trips.filter {
            !it.isActive &&
                it.endTimeEpochMs != null &&
                !it.originPersistent &&
                !it.isPersistent
        }
            .maxWithOrNull(
                compareBy<TripRecord> { it.endTimeEpochMs!! }
                    .thenBy { it.startTimeEpochMs }
            )

    fun averageSpeedMovingKmH(t: TripRecord): Float? {
        val d = t.distanceKm
        val sec = t.movingTimeMs / 1000f
        if (sec <= 0f || d <= 0f) return null
        return d / (sec / 3600f)
    }

    /** Average speed for trip active engine time: moving + engine-on idle, without parking time. */
    fun averageSpeedTripKmH(t: TripRecord): Float? {
        val d = t.distanceKm
        val sec = (t.movingTimeMs + t.idleTimeMs) / 1000f
        if (sec <= 0f || d <= 0f) return null
        return d / (sec / 3600f)
    }

    /** Average trip fuel consumption in liters per 100 km. */
    fun averageFuelConsumptionLitersPer100Km(t: TripRecord): Float? {
        val d = t.distanceKm
        if (d <= 0f) return null
        return t.fuelConsumedLiters * 100f / d
    }

    fun tripChangedEnough(a: TripRecord, b: TripRecord): Boolean {
        if (a.id != b.id) return true
        if (a.name != b.name) return true
        if (a.endTimeEpochMs != b.endTimeEpochMs) return true
        if (a.odometerStartKm != b.odometerStartKm) return true
        if (a.isPersistent != b.isPersistent) return true
        if (a.originPersistent != b.originPersistent) return true
        if (a.lastSampleWallMs != b.lastSampleWallMs) return true
        if (abs(a.distanceKm - b.distanceKm) > PERSIST_EPS) return true
        if (abs(a.maxSpeed - b.maxSpeed) > PERSIST_EPS) return true
        if (abs(a.fuelConsumedLiters - b.fuelConsumedLiters) > PERSIST_EPS) return true
        if (a.refuelCount != b.refuelCount) return true
        if (a.engineStartCount != b.engineStartCount) return true
        if (abs(a.fuelRefueledLiters - b.fuelRefueledLiters) > PERSIST_EPS) return true
        if (abs(a.fuelRefueledCostRub - b.fuelRefueledCostRub) > PERSIST_EPS) return true
        when {
            a.fuelBaselinePercent == null && b.fuelBaselinePercent == null -> Unit
            a.fuelBaselinePercent == null || b.fuelBaselinePercent == null -> return true
            abs(a.fuelBaselinePercent - b.fuelBaselinePercent) > FUEL_BASELINE_PERCENT_EPS -> return true
        }
        when {
            a.fuelBaselineLiters == null && b.fuelBaselineLiters == null -> Unit
            a.fuelBaselineLiters == null || b.fuelBaselineLiters == null -> return true
            abs(a.fuelBaselineLiters - b.fuelBaselineLiters) > FUEL_BASELINE_LITERS_EPS -> return true
        }
        if (abs(a.movingTimeMs - b.movingTimeMs) > MS_EPS) return true
        if (abs(a.idleTimeMs - b.idleTimeMs) > MS_EPS) return true
        if (abs(a.parkingTimeMs - b.parkingTimeMs) > MS_EPS) return true
        if (a.maxEngineTemp != b.maxEngineTemp) return true
        if (a.maxGearboxOilTemp != b.maxGearboxOilTemp) return true
        if (a.minOutsideTemp != b.minOutsideTemp) return true
        if (a.maxOutsideTemp != b.maxOutsideTemp) return true
        return false
    }

    fun mergeOutsideTemp(minCur: Float?, maxCur: Float?, sample: Float?): Pair<Float?, Float?> {
        if (sample == null) return Pair(minCur, maxCur)
        val min = minCur?.let { min(it, sample) } ?: sample
        val max = maxCur?.let { max(it, sample) } ?: sample
        return Pair(min, max)
    }

    fun updateMaxEngineTemp(current: Float?, sample: Float?): Float? {
        if (sample == null) return current
        return current?.let { max(it, sample) } ?: sample
    }

    fun updateMaxGearboxTemp(current: Int?, sample: Int?): Int? {
        if (sample == null) return current
        return current?.let { max(it, sample) } ?: sample
    }

    /**
     * Keeps at most one live persistent trip and at most [MAX_TRIPS] non-persistent trips.
     */
    internal fun applyListLimit(list: List<TripRecord>): List<TripRecord> {
        val liveCandidates = list.filter { it.isPersistent }
        val live = liveCandidates.maxByOrNull { it.startTimeEpochMs }
        val demotedExtras = liveCandidates
            .filter { live == null || it.id != live.id }
            .map { t ->
                t.copy(
                    isPersistent = false,
                    originPersistent = true,
                    endTimeEpochMs = t.endTimeEpochMs
                        ?: (System.currentTimeMillis().coerceAtLeast(t.startTimeEpochMs)),
                )
            }
        val nonPersistent = (list.filter { !it.isPersistent } + demotedExtras)
            .distinctBy { it.id }
            .let { all ->
                // Preserve relative order of original list as much as possible, then takeLast.
                val order = list.mapIndexed { index, tripRecord -> tripRecord.id to index }.toMap()
                all.sortedBy { order[it.id] ?: Int.MAX_VALUE }.takeLast(MAX_TRIPS)
            }
        return if (live != null) {
            // Keep live in the list; prefer original relative position if present.
            val withoutDup = nonPersistent.filter { it.id != live.id }
            val liveIdx = list.indexOfFirst { it.id == live.id }
            if (liveIdx < 0) withoutDup + live else {
                val before = withoutDup.count { (orderIndex(list, it.id)) < liveIdx }
                withoutDup.toMutableList().apply { add(before.coerceIn(0, size), live) }
            }
        } else {
            nonPersistent
        }
    }

    private fun orderIndex(list: List<TripRecord>, id: String): Int =
        list.indexOfFirst { it.id == id }.let { if (it < 0) Int.MAX_VALUE else it }

    private fun ensurePersistentTripLocked(
        defaultName: String,
        nowMs: Long,
        odometerStartKm: UInt?,
        fuelBaselinePercent: Float?,
        fuelBaselineLiters: Float?,
    ) {
        val fresh = TripRecord(
            name = defaultName,
            startTimeEpochMs = nowMs,
            endTimeEpochMs = null,
            odometerStartKm = odometerStartKm,
            fuelBaselinePercent = fuelBaselinePercent,
            fuelBaselineLiters = fuelBaselineLiters,
            isPersistent = true,
            originPersistent = true,
            lastSampleWallMs = nowMs,
        )
        _trips.value = normalizeTripsList(applyListLimit(_trips.value + fresh))
        refreshActiveTripLocked()
    }

    private fun refreshActiveTripLocked() {
        _activeTrip.value = _trips.value.lastOrNull { it.isCurrentActive }
    }

    /**
     * If multiple **current** trips have no end, keep only the latest by start and close the others.
     * Live persistent is never closed here.
     */
    private fun normalizeTripsList(list: List<TripRecord>): List<TripRecord> {
        val actives = list.filter { it.isCurrentActive }
        if (actives.size <= 1) return list
        val keep = actives.maxByOrNull { it.startTimeEpochMs } ?: return list
        return list.map { t ->
            if (!t.isCurrentActive || t.id == keep.id) {
                t
            } else {
                val boundary = (keep.startTimeEpochMs - 1L).coerceAtLeast(t.startTimeEpochMs)
                t.copy(endTimeEpochMs = boundary)
            }
        }
    }
}
