package vad.dashing.tbox

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val JSON_REFUEL_ID = "id"
private const val JSON_REFUEL_TRIP_ID = "tripId"
private const val JSON_REFUEL_TIME = "time"
private const val JSON_REFUEL_ODOMETER_KM = "odometerKm"
private const val JSON_REFUEL_LATITUDE = "latitude"
private const val JSON_REFUEL_LONGITUDE = "longitude"
private const val JSON_REFUEL_PERCENT_BEFORE = "fuelPercentBefore"
private const val JSON_REFUEL_PERCENT_AFTER = "fuelPercentAfter"
private const val JSON_REFUEL_ESTIMATED_LITERS = "estimatedLiters"
private const val JSON_REFUEL_ACTUAL_LITERS = "actualLiters"
private const val JSON_REFUEL_FUEL_ID = "fuelId"
private const val JSON_REFUEL_FUEL_NAME = "fuelName"
private const val JSON_REFUEL_PRICE_PER_LITER_RUB = "pricePerLiterRub"
private const val JSON_REFUEL_PRICE_SOURCE_NAME = "priceSourceName"
private const val JSON_REFUEL_COST_RUB = "costRub"

data class RefuelRecord(
    val id: String = UUID.randomUUID().toString(),
    val tripId: String? = null,
    val timeEpochMs: Long,
    val odometerKm: UInt? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fuelPercentBefore: Float? = null,
    val fuelPercentAfter: Float? = null,
    val estimatedLiters: Float = 0f,
    val actualLiters: Float = estimatedLiters,
    val fuelId: Int = FuelTypes.DEFAULT_FUEL_ID,
    val fuelName: String = FuelTypes.optionFor(fuelId).label,
    val pricePerLiterRub: Float? = null,
    val priceSourceName: String? = null,
    val costRub: Float? = pricePerLiterRub?.let { actualLiters * it },
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(JSON_REFUEL_ID, id)
        if (tripId != null) put(JSON_REFUEL_TRIP_ID, tripId)
        put(JSON_REFUEL_TIME, timeEpochMs)
        if (odometerKm != null) put(JSON_REFUEL_ODOMETER_KM, odometerKm.toLong())
        if (latitude != null) put(JSON_REFUEL_LATITUDE, latitude)
        if (longitude != null) put(JSON_REFUEL_LONGITUDE, longitude)
        if (fuelPercentBefore != null) put(JSON_REFUEL_PERCENT_BEFORE, fuelPercentBefore.toDouble())
        if (fuelPercentAfter != null) put(JSON_REFUEL_PERCENT_AFTER, fuelPercentAfter.toDouble())
        put(JSON_REFUEL_ESTIMATED_LITERS, estimatedLiters.toDouble())
        put(JSON_REFUEL_ACTUAL_LITERS, actualLiters.toDouble())
        put(JSON_REFUEL_FUEL_ID, fuelId)
        put(JSON_REFUEL_FUEL_NAME, fuelName)
        if (pricePerLiterRub != null) put(JSON_REFUEL_PRICE_PER_LITER_RUB, pricePerLiterRub.toDouble())
        if (!priceSourceName.isNullOrBlank()) put(JSON_REFUEL_PRICE_SOURCE_NAME, priceSourceName)
        if (costRub != null) put(JSON_REFUEL_COST_RUB, costRub.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): RefuelRecord {
            val fuelId = o.optInt(JSON_REFUEL_FUEL_ID, FuelTypes.DEFAULT_FUEL_ID)
            val actualLiters = o.optDouble(JSON_REFUEL_ACTUAL_LITERS, Double.NaN)
                .takeUnless { it.isNaN() }
                ?.toFloat()
            val estimatedLiters = o.optDouble(JSON_REFUEL_ESTIMATED_LITERS, 0.0).toFloat()
            val pricePerLiter = if (o.has(JSON_REFUEL_PRICE_PER_LITER_RUB) && !o.isNull(JSON_REFUEL_PRICE_PER_LITER_RUB)) {
                o.optDouble(JSON_REFUEL_PRICE_PER_LITER_RUB).toFloat()
            } else {
                null
            }
            val cost = if (o.has(JSON_REFUEL_COST_RUB) && !o.isNull(JSON_REFUEL_COST_RUB)) {
                o.optDouble(JSON_REFUEL_COST_RUB).toFloat()
            } else {
                pricePerLiter?.let { (actualLiters ?: estimatedLiters) * it }
            }
            return RefuelRecord(
                id = o.optString(JSON_REFUEL_ID).ifEmpty { UUID.randomUUID().toString() },
                tripId = o.optString(JSON_REFUEL_TRIP_ID).ifBlank { null },
                timeEpochMs = o.optLong(JSON_REFUEL_TIME),
                odometerKm = if (o.has(JSON_REFUEL_ODOMETER_KM) && !o.isNull(JSON_REFUEL_ODOMETER_KM)) {
                    o.optLong(JSON_REFUEL_ODOMETER_KM).takeIf { it >= 0 }?.toUInt()
                } else null,
                latitude = if (o.has(JSON_REFUEL_LATITUDE) && !o.isNull(JSON_REFUEL_LATITUDE)) {
                    o.optDouble(JSON_REFUEL_LATITUDE)
                } else null,
                longitude = if (o.has(JSON_REFUEL_LONGITUDE) && !o.isNull(JSON_REFUEL_LONGITUDE)) {
                    o.optDouble(JSON_REFUEL_LONGITUDE)
                } else null,
                fuelPercentBefore = if (o.has(JSON_REFUEL_PERCENT_BEFORE) && !o.isNull(JSON_REFUEL_PERCENT_BEFORE)) {
                    o.optDouble(JSON_REFUEL_PERCENT_BEFORE).toFloat()
                } else null,
                fuelPercentAfter = if (o.has(JSON_REFUEL_PERCENT_AFTER) && !o.isNull(JSON_REFUEL_PERCENT_AFTER)) {
                    o.optDouble(JSON_REFUEL_PERCENT_AFTER).toFloat()
                } else null,
                estimatedLiters = estimatedLiters,
                actualLiters = actualLiters ?: estimatedLiters,
                fuelId = fuelId,
                fuelName = o.optString(JSON_REFUEL_FUEL_NAME).ifBlank { FuelTypes.optionFor(fuelId).label },
                pricePerLiterRub = pricePerLiter,
                priceSourceName = o.optString(JSON_REFUEL_PRICE_SOURCE_NAME).ifBlank { null },
                costRub = cost,
            )
        }
    }
}

internal fun refuelsListToJson(refuels: List<RefuelRecord>): String {
    val arr = JSONArray()
    refuels.forEach { arr.put(it.toJson()) }
    return arr.toString()
}

internal fun refuelsListFromJson(raw: String): List<RefuelRecord> {
    if (raw.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(RefuelRecord.fromJson(o))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

object RefuelRepository {
    const val MAX_REFUELS = 30
    val lock = Any()

    private val _refuels = MutableStateFlow<List<RefuelRecord>>(emptyList())
    val refuels: StateFlow<List<RefuelRecord>> = _refuels.asStateFlow()
    private var lastPersistedRefuelsJson: String = ""

    internal fun resetForUnitTests() {
        synchronized(lock) {
            _refuels.value = emptyList()
            lastPersistedRefuelsJson = "[]"
        }
    }

    fun setRefuelsFromStore(refuels: List<RefuelRecord>) {
        synchronized(lock) {
            _refuels.value = refuels.takeLast(MAX_REFUELS)
            lastPersistedRefuelsJson = refuelsListToJson(_refuels.value)
        }
    }

    fun needsPersistence(): Boolean = synchronized(lock) {
        refuelsListToJson(_refuels.value) != lastPersistedRefuelsJson
    }

    fun markPersisted(refuelsJson: String) {
        synchronized(lock) {
            lastPersistedRefuelsJson = refuelsJson
        }
    }

    fun appendRefuel(refuel: RefuelRecord) {
        synchronized(lock) {
            _refuels.update { (it + refuel).takeLast(MAX_REFUELS) }
        }
    }

    fun replaceRefuel(updated: RefuelRecord) {
        synchronized(lock) {
            _refuels.update { current ->
                val idx = current.indexOfFirst { it.id == updated.id }
                if (idx < 0) current else current.toMutableList().apply { this[idx] = updated }
            }
        }
    }

    fun removeRefuel(id: String) {
        synchronized(lock) {
            _refuels.update { it.filterNot { refuel -> refuel.id == id } }
        }
    }

    fun updateActualLiters(id: String, liters: Float) {
        updateRefuelCostInput(id) { refuel ->
            val actual = liters.coerceAtLeast(0f)
            refuel.copy(
                actualLiters = actual,
                costRub = refuel.pricePerLiterRub?.let { actual * it },
            )
        }
    }

    fun updatePricePerLiter(id: String, pricePerLiterRub: Float?) {
        updateRefuelCostInput(id) { refuel ->
            val price = pricePerLiterRub?.coerceAtLeast(0f)
            refuel.copy(
                pricePerLiterRub = price,
                costRub = price?.let { refuel.actualLiters * it },
            )
        }
    }

    fun updateFuelType(id: String, fuel: FuelTypeOption) {
        updateRefuelCostInput(id) { refuel ->
            refuel.copy(
                fuelId = fuel.id,
                fuelName = fuel.label,
            )
        }
    }

    private fun updateRefuelCostInput(id: String, transform: (RefuelRecord) -> RefuelRecord) {
        synchronized(lock) {
            val current = _refuels.value.firstOrNull { it.id == id } ?: return
            replaceRefuel(transform(current))
        }
    }
}
