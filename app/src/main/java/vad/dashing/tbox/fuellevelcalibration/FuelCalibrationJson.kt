package vad.dashing.tbox.fuellevelcalibration

import org.json.JSONArray
import org.json.JSONObject

/**
 * Сериализация [CalibrationData] в JSON для DataStore (без Gson, как в остальном приложении).
 */
object FuelCalibrationJson {

    private const val KEY_REAL = "realLiters"
    private const val KEY_SENSOR = "sensorLiters"

    fun encode(data: CalibrationData): String {
        val o = JSONObject()
        o.put(KEY_REAL, JSONArray(data.realLiters.toList()))
        o.put(KEY_SENSOR, JSONArray(data.sensorLiters.toList()))
        return o.toString()
    }

    fun decode(raw: String): CalibrationData? {
        if (raw.isBlank()) return null
        return try {
            val o = JSONObject(raw)
            val ra = o.optJSONArray(KEY_REAL) ?: return null
            val sa = o.optJSONArray(KEY_SENSOR) ?: return null
            if (ra.length() != sa.length() || ra.length() == 0) return null
            val n = ra.length()
            val real = DoubleArray(n) { ra.optDouble(it, 0.0) }
            val sensor = DoubleArray(n) { sa.optDouble(it, 0.0) }
            CalibrationData(real, sensor)
        } catch (_: Exception) {
            null
        }
    }
}
