import kotlin.math.abs

class FuelFilter(val maxDeviationPercent: Double = 0.6) {
    // Минимальный порог, который мы считаем "Заправкой" для обучения
    val minRefillLimit: Double = 3.0

    fun calculateDeviation(entry: FuelEntry): Double {
        val sensorDelta = abs(entry.sensorAfter - entry.sensorBefore)
        if (entry.litersByCheck <= 0) return 0.0
        // Считаем отклонение факта от чека
        return abs(sensorDelta - entry.litersByCheck) / entry.litersByCheck
    }

    fun isValid(entry: FuelEntry): Boolean {
        val sensorDelta = entry.sensorAfter - entry.sensorBefore

        // Добавляем проверку: если в чеке меньше 3 литров — игнорируем для обучения
        if (entry.litersByCheck < minRefillLimit) return false

        if (entry.litersByCheck <= 0 || sensorDelta <= 0) return false
        return calculateDeviation(entry) <= maxDeviationPercent
    }
}