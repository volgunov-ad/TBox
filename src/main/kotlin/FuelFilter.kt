import kotlin.math.abs

class FuelFilter(val maxDeviationPercent: Double = 0.6) {

    fun calculateDeviation(entry: FuelEntry): Double {
        val sensorDelta = abs(entry.sensorAfter - entry.sensorBefore)
        if (entry.litersByCheck <= 0) return 0.0
        // Считаем отклонение факта от чека
        return abs(sensorDelta - entry.litersByCheck) / entry.litersByCheck
    }

    fun isValid(entry: FuelEntry): Boolean {
        val sensorDelta = entry.sensorAfter - entry.sensorBefore

        // 1. Если по чеку 0 или датчик не шевельнулся — это не заправка
        if (entry.litersByCheck <= 0 || sensorDelta <= 0) return false

        // 2. Проверка на аномальный шум
        return calculateDeviation(entry) <= maxDeviationPercent
    }
}