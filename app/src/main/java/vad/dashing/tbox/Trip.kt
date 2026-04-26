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
private const val JSON_PARKING_MS = "parkingMs"
private const val JSON_MAX_SPEED = "maxSpeed"
private const val JSON_MAX_ENGINE_TEMP = "maxEngineTemp"
private const val JSON_MAX_GEARBOX_TEMP = "maxGearboxTemp"
private const val JSON_MIN_OUTSIDE_TEMP = "minOutsideTemp"
private const val JSON_MAX_OUTSIDE_TEMP = "maxOutsideTemp"
private const val JSON_FUEL_LITERS = "fuelLiters"
private const val JSON_REFUEL_COUNT = "refuelCount"
private const val JSON_FUEL_REFUELED_LITERS = "fuelRefueledLiters"
private const val JSON_FUEL_REFUELED_COST_RUB = "fuelRefueledCostRub"
private const val JSON_FUEL_BASELINE_PERCENT = "fuelBaselinePercent"
private const val JSON_ODOMETER_START_KM = "odometerStartKm"
private const val JSON_ENGINE_START_COUNT = "engineStartCount"

data class TripRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long? = null,
    /** Odometer reading (km, same units as CAN) at trip start; null for legacy records. */
    val odometerStartKm: UInt? = null,
    val distanceKm: Float = 0f,
    val movingTimeMs: Long = 0L,
    val idleTimeMs: Long = 0L,
    /** Time while engine is off (rpm = 0), including HU-off segments within split-window continuation. */
    val parkingTimeMs: Long = 0L,
    val maxSpeed: Float = 0f,
    val maxEngineTemp: Float? = null,
    val maxGearboxOilTemp: Int? = null,
    val minOutsideTemp: Float? = null,
    val maxOutsideTemp: Float? = null,
    val fuelConsumedLiters: Float = 0f,
    /** Number of refueling events detected during the trip (level jump heuristic). */
    val refuelCount: Int = 0,
    /** Engine starts: transition from 0 or unknown RPM to positive; includes service start with RPM already positive. */
    val engineStartCount: Int = 0,
    /** Estimated liters added from detected refuels (% rise × tank size per event). */
    val fuelRefueledLiters: Float = 0f,
    /** Estimated cost of detected refuels, in rubles. */
    val fuelRefueledCostRub: Float = 0f,
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
        if (odometerStartKm != null) put(JSON_ODOMETER_START_KM, odometerStartKm.toLong())
        put(JSON_DISTANCE_KM, distanceKm.toDouble())
        put(JSON_MOVING_MS, movingTimeMs)
        put(JSON_IDLE_MS, idleTimeMs)
        put(JSON_PARKING_MS, parkingTimeMs)
        put(JSON_MAX_SPEED, maxSpeed.toDouble())
        if (maxEngineTemp != null) put(JSON_MAX_ENGINE_TEMP, maxEngineTemp.toDouble())
        if (maxGearboxOilTemp != null) put(JSON_MAX_GEARBOX_TEMP, maxGearboxOilTemp)
        if (minOutsideTemp != null) put(JSON_MIN_OUTSIDE_TEMP, minOutsideTemp.toDouble())
        if (maxOutsideTemp != null) put(JSON_MAX_OUTSIDE_TEMP, maxOutsideTemp.toDouble())
        put(JSON_FUEL_LITERS, fuelConsumedLiters.toDouble())
        put(JSON_REFUEL_COUNT, refuelCount)
        put(JSON_ENGINE_START_COUNT, engineStartCount)
        put(JSON_FUEL_REFUELED_LITERS, fuelRefueledLiters.toDouble())
        put(JSON_FUEL_REFUELED_COST_RUB, fuelRefueledCostRub.toDouble())
        if (fuelBaselinePercent != null) put(JSON_FUEL_BASELINE_PERCENT, fuelBaselinePercent.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): TripRecord = TripRecord(
            id = o.optString(JSON_ID).ifEmpty { UUID.randomUUID().toString() },
            name = o.optString(JSON_NAME),
            startTimeEpochMs = o.optLong(JSON_START),
            endTimeEpochMs = if (o.has(JSON_END) && !o.isNull(JSON_END)) o.optLong(JSON_END) else null,
            odometerStartKm = if (o.has(JSON_ODOMETER_START_KM) && !o.isNull(JSON_ODOMETER_START_KM)) {
                o.optLong(JSON_ODOMETER_START_KM).takeIf { it >= 0 }?.toUInt()
            } else null,
            distanceKm = o.optDouble(JSON_DISTANCE_KM, 0.0).toFloat(),
            movingTimeMs = o.optLong(JSON_MOVING_MS),
            idleTimeMs = o.optLong(JSON_IDLE_MS),
            parkingTimeMs = o.optLong(JSON_PARKING_MS),
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
            engineStartCount = o.optInt(JSON_ENGINE_START_COUNT),
            fuelRefueledLiters = o.optDouble(JSON_FUEL_REFUELED_LITERS, 0.0).toFloat(),
            fuelRefueledCostRub = o.optDouble(JSON_FUEL_REFUELED_COST_RUB, 0.0).toFloat(),
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

/**
 * JSON snapshot of trips for backup export only: the current active trip (last in array order among
 * those without [JSON_END]) is written with [JSON_END] = [endTimeEpochMs]. Does not modify storage
 * or [TripRepository].
 */
internal fun tripsJsonForBackupExport(raw: String, endTimeEpochMs: Long): String {
    if (raw.isBlank()) return raw
    return try {
        val arr = JSONArray(raw)
        var lastActiveIndex = -1
        var lastActiveStart = Long.MIN_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.has(JSON_END) || o.isNull(JSON_END)) {
                val start = o.optLong(JSON_START, Long.MIN_VALUE)
                if (start >= lastActiveStart) {
                    lastActiveStart = start
                    lastActiveIndex = i
                }
            }
        }
        if (lastActiveIndex < 0) return raw
        val obj = arr.getJSONObject(lastActiveIndex)
        val start = obj.optLong(JSON_START)
        obj.put(JSON_END, endTimeEpochMs.coerceAtLeast(start))
        arr.toString()
    } catch (_: Exception) {
        raw
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
