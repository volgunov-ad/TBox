import kotlin.math.abs

class FuelFilter(private val maxDeviationPercent: Double = 0.6) {
    fun isValid(entry: FuelEntry): Boolean {
        val sensorDelta = entry.sensorAfter - entry.sensorBefore

        // 1. Проверка на логику (топлива не стало меньше)
        if (sensorDelta <= 0) return false

        // 2. Проверка на аномальный шум (ошибки датчика или ввода)
        val deviation = abs(sensorDelta - entry.litersByCheck) / entry.litersByCheck
        return deviation <= maxDeviationPercent
    }
}