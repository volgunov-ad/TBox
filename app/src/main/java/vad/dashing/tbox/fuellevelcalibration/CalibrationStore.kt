package vad.dashing.tbox.fuellevelcalibration

/**
 * Хранение накопленных литров по зонам и расчёт коэффициентов K (логика ветки tochBak).
 */
class CalibrationStore(
    val zoneCount: Int,
    val tankCapacity: Double,
    private val maturityThreshold: Double = 80.0,
) {
    val zoneSize = tankCapacity / zoneCount
    var realLitersPerZone = DoubleArray(zoneCount) { 0.0 }
    var sensorLitersPerZone = DoubleArray(zoneCount) { 0.0 }

    /** Восстановление из снимка, сохранённого в настройках. */
    fun restoreFrom(data: CalibrationData) {
        if (data.realLiters.size == zoneCount) {
            this.realLitersPerZone = data.realLiters.copyOf()
            this.sensorLitersPerZone = data.sensorLiters.copyOf()
        }
    }

    fun addData(zoneIdx: Int, realStd: Double, sensor: Double) {
        realLitersPerZone[zoneIdx] += realStd
        sensorLitersPerZone[zoneIdx] += sensor
    }

    fun getZoneK(zoneIdx: Int): Double =
        if (sensorLitersPerZone[zoneIdx] > 0) {
            realLitersPerZone[zoneIdx] / sensorLitersPerZone[zoneIdx]
        } else {
            getGlobalK()
        }

    fun getGlobalK(): Double {
        val matureK = sensorLitersPerZone.indices
            .filter { sensorLitersPerZone[it] >= 5.0 }
            .map { realLitersPerZone[it] / sensorLitersPerZone[it] }
        return if (matureK.isNotEmpty()) matureK.average() else 1.0
    }

    fun getConfidence(zoneIdx: Int): Double =
        (sensorLitersPerZone[zoneIdx] / maturityThreshold).coerceIn(0.0, 1.0)

    fun getZoneDataVolume(zoneIdx: Int): Double = sensorLitersPerZone[zoneIdx]

    /** Сброс накопленных данных (при смене объёма бака / числа зон). */
    fun clear() {
        realLitersPerZone = DoubleArray(zoneCount) { 0.0 }
        sensorLitersPerZone = DoubleArray(zoneCount) { 0.0 }
    }

    fun snapshot(): CalibrationData = CalibrationData(
        realLitersPerZone.copyOf(),
        sensorLitersPerZone.copyOf(),
    )
}
