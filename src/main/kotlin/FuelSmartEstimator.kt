class FuelSmartEstimator(
    val tankCapacity: Double = 50.0,
    private val sensorMin: Double = 2.0,
    private val sensorMax: Double = 48.0,
    private val zoneCount: Int = 5
) {
    private val zoneSize = tankCapacity / zoneCount
    private val maturityThreshold = 80.0
    private val thermalExpansionCoeff = 0.0011

    private val realLitersPerZone = DoubleArray(zoneCount) { 0.0 }
    private val sensorLitersPerZone = DoubleArray(zoneCount) { 0.0 }

    // Для детектора слива
    private var lastStableLevel: Double = -1.0
    private val maxIdleConsumptionPerMinute = 0.04 // ~2.4 л/час

    /**
     * ОБУЧЕНИЕ: Накопление данных из чеков
     */
    fun train(entry: FuelEntry): String {
        val standardizedLiters = entry.litersByCheck / (1 + thermalExpansionCoeff * (entry.ambientTemp - 15))
        val sensorDelta = entry.sensorAfter - entry.sensorBefore

        if (sensorDelta <= 0) return "Ошибка: нет изменения уровня"

        // Собираем информацию о затронутых зонах
        val affectedZones = mutableListOf<Int>()
        val sensorTotal = entry.sensorAfter - entry.sensorBefore

        for (i in 0 until zoneCount) {
            val zoneStart = i * zoneSize
            val zoneEnd = (i + 1) * zoneSize
            val overlapStart = maxOf(entry.sensorBefore, zoneStart)
            val overlapEnd = minOf(entry.sensorAfter, zoneEnd)

            if (overlapStart < overlapEnd) {
                affectedZones.add(i + 1) // Добавляем номер зоны (1-5)
                val overlapShare = (overlapEnd - overlapStart) / sensorTotal
                realLitersPerZone[i] += standardizedLiters * overlapShare
                sensorLitersPerZone[i] += (overlapEnd - overlapStart)
            }
        }

        return "Заправка: с ${entry.sensorBefore}л до ${entry.sensorAfter}л " +
                "(Итого: ${"%.1f".format(standardizedLiters)}л станд.). " +
                "Затронуты зоны: ${affectedZones.joinToString(", ")}"
    }

    private fun updateZones(before: Double, after: Double, realTotal: Double) {
        val sensorTotal = after - before
        for (i in 0 until zoneCount) {
            val zoneStart = i * zoneSize
            val zoneEnd = (i + 1) * zoneSize
            val overlapStart = maxOf(before, zoneStart)
            val overlapEnd = minOf(after, zoneEnd)

            if (overlapStart < overlapEnd) {
                val overlapShare = (overlapEnd - overlapStart) / sensorTotal
                realLitersPerZone[i] += realTotal * overlapShare
                sensorLitersPerZone[i] += (overlapEnd - overlapStart)
            }
        }
    }

    /**
     * ПРЕДСКАЗАНИЕ: Получение реальных литров с уверенностью
     */
    fun getCorrectedLiters(currentSensorValue: Double, currentTemp: Double): EstimationResult {
        val safeSensorValue = currentSensorValue.coerceIn(0.0, tankCapacity)
        val zoneIdx = (safeSensorValue / zoneSize).toInt().coerceIn(0, zoneCount - 1)

        val matureCoefficients = sensorLitersPerZone.indices
            .filter { sensorLitersPerZone[it] >= 5.0 }
            .map { realLitersPerZone[it] / sensorLitersPerZone[it] }

        val kGlobal = if (matureCoefficients.isNotEmpty()) matureCoefficients.average() else 1.0
        val sensorInZone = sensorLitersPerZone[zoneIdx]
        val kLocal = if (sensorInZone > 0) realLitersPerZone[zoneIdx] / sensorInZone else kGlobal
        val confidence = (sensorInZone / maturityThreshold).coerceAtMost(1.0)

        val kFinal = (kLocal * confidence) + (kGlobal * (1.0 - confidence))
        val baseLiters = safeSensorValue * kFinal
        val actualVolume = baseLiters * (1 + thermalExpansionCoeff * (currentTemp - 15))

        val finalLiters = actualVolume.coerceAtMost(tankCapacity + 5)
        val isAtLimit = currentSensorValue >= sensorMax || currentSensorValue <= sensorMin

        return EstimationResult(finalLiters, if (isAtLimit) confidence * 0.7 else confidence)
    }

    /**
     * РЕЗЕРВ: Расчет запаса хода
     */
    fun calculateRange(currentLiters: Double, avgConsumption: Double): Double {
        if (avgConsumption <= 0) return 0.0
        return (currentLiters / avgConsumption) * 100
    }

    /**
     * БЕЗОПАСНОСТЬ: Детектор аномалий (слива)
     */
    fun detectFuelAnomaly(currentLiters: Double, isEngineRunning: Boolean): String {
        if (lastStableLevel < 0) {
            lastStableLevel = currentLiters
            return "Мониторинг запущен"
        }

        val delta = lastStableLevel - currentLiters
        lastStableLevel = currentLiters

        return when {
            !isEngineRunning && delta > 0.15 -> "⚠️ ТРЕВОГА: Возможен слив! (-${"%.2f".format(delta)}л)"
            isEngineRunning && delta > maxIdleConsumptionPerMinute -> "⚠️ ВНИМАНИЕ: Аномальный расход!"
            else -> "✅ Уровень стабилен"
        }
    }

    fun getCalibrationReport(): List<String> {
        return (0 until zoneCount).map { i ->
            val sensorInZone = sensorLitersPerZone[i]
            val k = if (sensorInZone > 0) realLitersPerZone[i] / sensorInZone else 1.0

            // Считаем уверенность конкретно для этой зоны (0..100%)
            val confidence = (sensorInZone / maturityThreshold).coerceAtMost(1.0) * 100

            val zoneStart = i * zoneSize.toInt()
            val zoneEnd = (i + 1) * zoneSize.toInt()

            "Зона $zoneStart-${zoneEnd}л: K=${"%.3f".format(k)} | Данных: ${"%.1f".format(sensorInZone)}л | Уверенность: ${confidence.toInt()}%"
        }
    }
}