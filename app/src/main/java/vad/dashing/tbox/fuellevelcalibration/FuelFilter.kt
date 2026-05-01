package vad.dashing.tbox.fuellevelcalibration

import kotlin.math.abs

/**
 * Отсев аномальных заправок по расхождению дельты датчика и литров по чеку.
 */
class FuelFilter(val maxDeviationPercent: Double = 0.6) {

    fun calculateDeviation(entry: FuelEntry): Double {
        val sensorDelta = abs(entry.sensorAfter - entry.sensorBefore)
        if (entry.litersByCheck <= 0) return 0.0
        return abs(sensorDelta - entry.litersByCheck) / entry.litersByCheck
    }

    fun isValid(entry: FuelEntry): Boolean {
        val sensorDelta = entry.sensorAfter - entry.sensorBefore
        if (entry.litersByCheck <= 0 || sensorDelta <= 0) return false
        return calculateDeviation(entry) <= maxDeviationPercent
    }
}
