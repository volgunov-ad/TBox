package vad.dashing.tbox

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val JSON_ID = "id"
private const val JSON_NAME = "name"
private const val JSON_START = "start"
private const val JSON_END = "end"
private const val JSON_DISTANCE_KM = "distanceKm"
private const val JSON_MOVING_MS = "movingMs"
private const val JSON_IDLE_MS = "idleMs"
private const val JSON_MAX_SPEED = "maxSpeed"
private const val JSON_MAX_ENGINE_TEMP = "maxEngineTemp"
private const val JSON_MAX_GEARBOX_TEMP = "maxGearboxTemp"
private const val JSON_MIN_OUTSIDE_TEMP = "minOutsideTemp"
private const val JSON_MAX_OUTSIDE_TEMP = "maxOutsideTemp"
private const val JSON_FUEL_LITERS = "fuelLiters"
private const val JSON_REFUEL_COUNT = "refuelCount"
private const val JSON_FUEL_REFUELED_LITERS = "fuelRefueledLiters"
private const val JSON_FUEL_BASELINE_PERCENT = "fuelBaselinePercent"

data class TripRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long? = null,
    val distanceKm: Float = 0f,
    val movingTimeMs: Long = 0L,
    val idleTimeMs: Long = 0L,
    val maxSpeed: Float = 0f,
    val maxEngineTemp: Float? = null,
    val maxGearboxOilTemp: Int? = null,
    val minOutsideTemp: Float? = null,
    val maxOutsideTemp: Float? = null,
    val fuelConsumedLiters: Float = 0f,
    /** Number of refueling events detected during the trip (level jump heuristic). */
    val refuelCount: Int = 0,
    /** Estimated liters added from detected refuels (% rise × tank size per event). */
    val fuelRefueledLiters: Float = 0f,
    /**
     * Last known filtered fuel level (%) for this trip, persisted so consumption/refuel logic can
     * resume after the HU/service was off (e.g. refuel while engine stopped).
     */
    val fuelBaselinePercent: Float? = null,
) {
    val isActive: Boolean get() = endTimeEpochMs == null

    fun toJson(): JSONObject = JSONObject().apply {
        put(JSON_ID, id)
        put(JSON_NAME, name)
        put(JSON_START, startTimeEpochMs)
        if (endTimeEpochMs != null) put(JSON_END, endTimeEpochMs)
        put(JSON_DISTANCE_KM, distanceKm.toDouble())
        put(JSON_MOVING_MS, movingTimeMs)
        put(JSON_IDLE_MS, idleTimeMs)
        put(JSON_MAX_SPEED, maxSpeed.toDouble())
        if (maxEngineTemp != null) put(JSON_MAX_ENGINE_TEMP, maxEngineTemp.toDouble())
        if (maxGearboxOilTemp != null) put(JSON_MAX_GEARBOX_TEMP, maxGearboxOilTemp)
        if (minOutsideTemp != null) put(JSON_MIN_OUTSIDE_TEMP, minOutsideTemp.toDouble())
        if (maxOutsideTemp != null) put(JSON_MAX_OUTSIDE_TEMP, maxOutsideTemp.toDouble())
        put(JSON_FUEL_LITERS, fuelConsumedLiters.toDouble())
        put(JSON_REFUEL_COUNT, refuelCount)
        put(JSON_FUEL_REFUELED_LITERS, fuelRefueledLiters.toDouble())
        if (fuelBaselinePercent != null) put(JSON_FUEL_BASELINE_PERCENT, fuelBaselinePercent.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): TripRecord = TripRecord(
            id = o.optString(JSON_ID).ifEmpty { UUID.randomUUID().toString() },
            name = o.optString(JSON_NAME),
            startTimeEpochMs = o.optLong(JSON_START),
            endTimeEpochMs = if (o.has(JSON_END) && !o.isNull(JSON_END)) o.optLong(JSON_END) else null,
            distanceKm = o.optDouble(JSON_DISTANCE_KM, 0.0).toFloat(),
            movingTimeMs = o.optLong(JSON_MOVING_MS),
            idleTimeMs = o.optLong(JSON_IDLE_MS),
            maxSpeed = o.optDouble(JSON_MAX_SPEED, 0.0).toFloat(),
            maxEngineTemp = if (o.has(JSON_MAX_ENGINE_TEMP) && !o.isNull(JSON_MAX_ENGINE_TEMP)) {
                o.optDouble(JSON_MAX_ENGINE_TEMP).toFloat()
            } else null,
            maxGearboxOilTemp = if (o.has(JSON_MAX_GEARBOX_TEMP) && !o.isNull(JSON_MAX_GEARBOX_TEMP)) {
                o.optInt(JSON_MAX_GEARBOX_TEMP)
            } else null,
            minOutsideTemp = if (o.has(JSON_MIN_OUTSIDE_TEMP) && !o.isNull(JSON_MIN_OUTSIDE_TEMP)) {
                o.optDouble(JSON_MIN_OUTSIDE_TEMP).toFloat()
            } else null,
            maxOutsideTemp = if (o.has(JSON_MAX_OUTSIDE_TEMP) && !o.isNull(JSON_MAX_OUTSIDE_TEMP)) {
                o.optDouble(JSON_MAX_OUTSIDE_TEMP).toFloat()
            } else null,
            fuelConsumedLiters = o.optDouble(JSON_FUEL_LITERS, 0.0).toFloat(),
            refuelCount = o.optInt(JSON_REFUEL_COUNT),
            fuelRefueledLiters = o.optDouble(JSON_FUEL_REFUELED_LITERS, 0.0).toFloat(),
            fuelBaselinePercent = if (o.has(JSON_FUEL_BASELINE_PERCENT) && !o.isNull(JSON_FUEL_BASELINE_PERCENT)) {
                o.optDouble(JSON_FUEL_BASELINE_PERCENT).toFloat()
            } else null,
        )
    }
}

internal fun tripsListToJson(trips: List<TripRecord>): String {
    val arr = JSONArray()
    trips.forEach { arr.put(it.toJson()) }
    return arr.toString()
}

internal fun tripsListFromJson(raw: String): List<TripRecord> {
    if (raw.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(TripRecord.fromJson(o))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun favoritesSetToJson(ids: Set<String>): String {
    val arr = JSONArray()
    ids.forEach { arr.put(it) }
    return arr.toString()
}

internal fun favoritesSetFromJson(raw: String): Set<String> {
    if (raw.isBlank()) return emptySet()
    return try {
        val arr = JSONArray(raw)
        buildSet {
            for (i in 0 until arr.length()) {
                add(arr.optString(i))
            }
        }.filter { it.isNotEmpty() }.toSet()
    } catch (_: Exception) {
        emptySet()
    }
}
