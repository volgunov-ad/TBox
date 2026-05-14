import kotlin.math.abs

class FuelSystemSimulator(
    private val estimator: FuelSmartEstimator,
    private val filter: FuelFilter
) {
    private val efficiencyCalculator = FuelEfficiencyCalculator()

    fun runTestScenario() {
        val history = listOf(
            FuelEntry(1100.0, 105.0, 36.5, 50.0, 13.11, 8.0)
        )

        var totalLiters = 0.0
        var totalDistance = 0.0
        var totalHours = 0.0

        println("\n=== 1. Обработка заправок и обучение ===")

        history.forEachIndexed { index, entry ->
            if (filter.isValid(entry)) {
                val report = estimator.train(entry)
                println("[Запись #${index + 1}] ✅ $report")

                // Накопление статистики (с предыдущей записью)
                history.getOrNull(index - 1)?.let { prev ->
                    totalLiters += entry.litersByCheck
                    totalDistance += (entry.odometerKm - prev.odometerKm)
                    totalHours += (entry.engineHours - prev.engineHours)
                }
            } else {
                printRejectionDetails(entry, index)
            }
        }

        displayResults(totalLiters, totalDistance, totalHours)
        runHardModeTest()
    }

    private fun printRejectionDetails(entry: FuelEntry, index: Int) {
        val sensorDelta = entry.sensorAfter - entry.sensorBefore

        println("[Запись #${index + 1}] ⚠️ ОТКЛОНЕНО ФИЛЬТРОМ!")

        val reason = when {
            entry.litersByCheck < filter.minRefillLimit ->
                "Объём заправки (${entry.litersByCheck} л) меньше порога (${filter.minRefillLimit} л)"
            sensorDelta <= 0 ->
                "Датчик не зафиксировал рост уровня (Δ: ${"%.1f".format(sensorDelta)} л)"
            else -> {
                // calculateDeviation вызываем ТОЛЬКО здесь, когда реально нужен
                val deviation = filter.calculateDeviation(entry)
                "Аномальное расхождение: ${"%.1f".format(deviation * 100)}% " +
                        "(порог ${"%.1f".format(filter.maxDeviationPercent * 100)}%). " +
                        "Датчик: ${"%.1f".format(sensorDelta)} л, Чек: ${entry.litersByCheck} л"
            }
        }
        println("   -> Причина: $reason")
    }

    private fun displayResults(totalLiters: Double, totalDistance: Double, totalHours: Double) {
        println("\n=== 2. Состояние калибровки зон ===")
        estimator.getCalibrationReport().forEach { println(it) }

        println("\n=== 3. Проверка точности и Резерва (Smart Check) ===")
        val testSensorValue = 45.0 // Имитируем, что датчик сейчас показывает 12л
        val currentTemp = 10.0   // На улице +10
        val corrected = estimator.getCorrectedLiters(testSensorValue, currentTemp)

        // Берем для прогноза средний расход из нашего калькулятора (общий)
        val avgCons = efficiencyCalculator.calculateSmartConsumption(totalLiters, totalDistance, totalHours)
        val range = estimator.calculateRange(corrected.litersStandard, if(avgCons > 0) avgCons else 8.5) // 8.5 как заглушка

        println("Датчик видит: $testSensorValue л | Реально в баке: ${"%.2f".format(corrected.litersStandard)} л (Уверенность: ${(corrected.confidence * 100).toInt()}%)")
        println("Прогноз запаса хода при текущем расходе: ${"%.0f".format(range)} км")


        /*println("\n=== 4. Проверка безопасности (Детектор слива) ===")
        // Имитируем падение уровня на парковке
        println(estimator.detectFuelAnomaly(12.8, false)) // Базовая точка
        println(estimator.detectFuelAnomaly(12.0, false)) // Резкое падение */

        println("\n=== 4. Итоговая эффективность (по истории заправок) ===")
        if (totalDistance > 0) {
            // Выводим тот самый детальный отчет с разделением на трассу и простой
            println(efficiencyCalculator.getSummary(totalLiters, totalDistance, totalHours))
        } else {
            println("Для расчета эффективности нужно минимум две заправки в истории.")
        }

        println("\n=== Тест температурного дрейфа (Сравнение) ===")
        val sensorValue = 49.6
        val temp = 0.0 // мороз

        val result = estimator.getCorrectedLiters(sensorValue, temp)

        println("Датчик показывает: $sensorValue л")
        println("Стабильный остаток (базовый): ${"%.2f".format(result.litersStandard)} л")
        println("Фактический объем (при ${temp}:) ${"%.2f".format(result.litersActual)} л")
        println("Сжатие из-за холода: ${"%.2f".format(result.litersStandard - result.litersActual)} л")

        val sensorValue2 = 47.07
        val hotTemp = 16.0    // Жара

        val res = estimator.getCorrectedLiters(sensorValue2, hotTemp)

        println("\nДатчик показывает: $sensorValue2 л")
        println("Стабильный остаток (базовый): ${"%.2f".format(res.litersStandard)} л")
        println("Фактический объем (при +${hotTemp}:) ${"%.2f".format(res.litersActual)} л")
        println("Расширение из-за жары: ${"%.2f".format(res.litersActual - res.litersStandard)} л")
    }


    fun runHardModeTest() {
        println("\n=== ТЕСТ-КЕЙС: Тяжелые пробки / Зима ===")
        val report = efficiencyCalculator.getSummary(8.0, 10.0, 5.0)
        println(report)
    }
}