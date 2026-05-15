import kotlin.math.abs

class FuelSystemSimulator(
    private val estimator: FuelSmartEstimator,
    private val filter: FuelFilter
) {
    private val efficiencyCalculator = FuelEfficiencyCalculator()

    fun runTestScenario() {
        // Тестовая история с заполненными новыми параметрами
        val history = listOf(
            FuelEntry(
                odometerKm = 1100.0,
                engineHours = 105.0,
                sensorBefore = 36.5,
                sensorAfter = 50.0,
                litersByCheck = 13.11,
                ambientTemp = 8.0,
                isEngineRunning = true,
                currentSpeedKmH = 0.0, // Машина стоит на АЗС
                dashboardAvgConsumption = 11.0
            )
        )

        var totalLiters = 0.0
        var totalDistance = 0.0
        var totalHours = 0.0

        println("\n=== 1. Обработка заправок и обучение ===")

        history.forEachIndexed { index, entry ->
            // 1. Извлекаем предыдущую запись для расчета дельты
            val prevEntry = history.getOrNull(index - 1)

            // 2. Считаем реальную скорость и статус двигателя (если их нет в CAN)
            val distanceDelta = if (prevEntry != null) { entry.odometerKm - prevEntry.odometerKm } else 0.0
            val hoursDelta = if (prevEntry != null) { entry.engineHours - prevEntry.engineHours } else 0.0
            val isEngineRunning = hoursDelta > 0.0 || entry.engineHours > 0.0
            val currentSpeedKmH = if (hoursDelta > 0.0) { distanceDelta / hoursDelta } else 0.0

            // Пересобираем объект с точными дельтами для передачи в эстиматор
            val currentEntry = entry.copy(
                isEngineRunning = isEngineRunning,
                currentSpeedKmH = currentSpeedKmH
            )

            // 3. Вызываем обновленный метод (передаем объект целиком)
            val corrected = estimator.getCorrectedLiters(currentEntry)

            if (filter.isValid(currentEntry)) {
                val report = estimator.train(currentEntry)

                // ⛽ ЛОГИКА ФИЛЬТРАЦИИ ЗАПРАВОК (v2.1)
                val isRefueled = currentEntry.sensorAfter > currentEntry.sensorBefore + 2.0
                if (isRefueled) {
                    val refillPercent = currentEntry.litersByCheck / estimator.tankCapacity
                    if (refillPercent > 0.20) { // Существенная заправка (>20% бака)
                        totalLiters = 0.0
                        totalDistance = 0.0
                        totalHours = 0.0
                        efficiencyCalculator.resetTripStats()
                        println("[Запись #${index + 1}] ⛽ Новый cycle расчета: существенная заправка (>20%), статистика сброшена.")
                    } else {
                        println("[Запись #${index + 1}] ⛽ Зафиксирована микро-доливка топлива. Статистика продолжается.")
                    }
                }

                // Накапливаем путевую статистику
                prevEntry?.let { prev ->
                    totalLiters += currentEntry.litersByCheck
                    totalDistance += (currentEntry.odometerKm - prev.odometerKm)
                    totalHours += (currentEntry.engineHours - prev.engineHours)
                }

                // Расчет гибридного расхода
                val finalConsumption = efficiencyCalculator.getHybridConsumption(
                    headUnitConsumption = currentEntry.dashboardAvgConsumption,
                    isSmartCalculationValid = corrected.isSmartCalculationValid,
                    totalLiters = totalLiters,
                    distance = totalDistance,
                    hours = totalHours
                )

                val sourceMarker = if (corrected.isSmartCalculationValid) "⚡ [НАШ АЛГОРИТМ]" else "🚗 [ШТАТНОЕ ГУ]"
                println("[Запись #${index + 1}] ✅ Расход: ${"%.1f".format(finalConsumption)} л/100км | Источник: $sourceMarker | $report")
            } else {
                printRejectionDetails(currentEntry, index)
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
                val deviation = filter.calculateDeviation(entry)
                "Аномальное расхождение: ${"%.1f".format(deviation * 100)}% " +
                        "(порог ${"%.1f".format(filter.maxDeviationPercent * 100)}%). " +
                        "Датчик: ${"%.1f".format(sensorDelta)} л, Чек: ${entry.litersByCheck} л"
            }
        }
        println(" -> Причина: $reason")
    }

    private fun displayResults(totalLiters: Double, totalDistance: Double, totalHours: Double) {
        println("\n=== 2. Состояние калибровки зон ===")
        estimator.getCalibrationReport().forEach { println(it) }

        println("\n=== 3. Проверка точности и Резерва (Smart Check) ===")
        val testSensorValue = 45.0
        val currentTemp = 10.0

        // Создаем фиктивный FuelEntry для проверки
        val checkEntry = FuelEntry(0.0, 0.0, testSensorValue, testSensorValue, 0.0, currentTemp)
        val corrected = estimator.getCorrectedLiters(checkEntry)

        val avgCons = efficiencyCalculator.calculateSmartConsumption(totalLiters, totalDistance, totalHours)
        val range = estimator.calculateRange(corrected.litersStandard, if (avgCons > 0) avgCons else 8.5)

        println("Датчик видит: $testSensorValue л | Реально в баке: ${"%.2f".format(corrected.litersStandard)} л (Уверенность: ${(corrected.confidence * 100).toInt()}%)")
        println("Прогноз запаса хода при текущем расходе: ${"%.0f".format(range)} км")

        println("\n=== 4. Итоговая эффективность (по истории заправок) ===")
        if (totalDistance > 0) {
            println(efficiencyCalculator.getSummary(totalLiters, totalDistance, totalHours))
        } else {
            println("Для расчета эффективности нужно минимум две заправки в истории.")
        }

        println("\n=== Тест температурного дрейфа (Сравнение) ===")
        val sensorValue = 49.6
        val temp = 0.0
        val winterEntry = FuelEntry(0.0, 0.0, sensorValue, sensorValue, 0.0, temp, isEngineRunning = true, currentSpeedKmH = 0.0)
        val result = estimator.getCorrectedLiters(winterEntry)

        println("Датчик показывает: $sensorValue л")
        println("Стабильный остаток (базовый): ${"%.2f".format(result.litersStandard)} л")
        println("Фактический объем (при $temp°C): ${"%.2f".format(result.litersActual)} л")
        println("Сжатие из-за холода: ${"%.2f".format(result.litersStandard - result.litersActual)} л")

        val sensorValue2 = 47.07
        val hotTemp = 16.0
        val summerEntry = FuelEntry(0.0, 0.0, sensorValue2, sensorValue2, 0.0, hotTemp, isEngineRunning = true, currentSpeedKmH = 60.0)
        val res = estimator.getCorrectedLiters(summerEntry)

        println("\nДатчик показывает: $sensorValue2 л")
        println("Стабильный остаток (базовый): ${"%.2f".format(res.litersStandard)} л")
        println("Фактический объем (при +$hotTemp°C): ${"%.2f".format(res.litersActual)} л")
        println("Расширение из-за жары: ${"%.2f".format(res.litersActual - res.litersStandard)} л")

        println("\n=======================================================")
        println("🔬 КОМПЛЕКСНЫЙ ТЕСТ АЛГОРИТМА НА СИСТЕМНЫЕ ОШИБКИ")
        println("=======================================================")

        val normalThreshold = 0.3
        val testTemp = 15.0

        // -----------------------------------------------------------------
        // СЦЕНАРИЙ 1: Проверка "Холодного старта"
        // -----------------------------------------------------------------
        println("\n[Сценарий 1] Машина поехала, но зона бака сырая (confidence < 30%):")
        val sc1EntryA = FuelEntry(0.0, 0.0, 45.0, 45.0, 0.0, testTemp, isEngineRunning = true, currentSpeedKmH = 60.0)
        val sc1EntryB = FuelEntry(0.0, 0.0, 44.5, 44.5, 0.0, testTemp, isEngineRunning = true, currentSpeedKmH = 60.0)

        estimator.getCorrectedLiters(sc1EntryA, normalThreshold)
        val stepScenario1 = estimator.getCorrectedLiters(sc1EntryB, normalThreshold)

        val res1 = efficiencyCalculator.getHybridConsumption(9.5, stepScenario1.isSmartCalculationValid, 12.0, 150.0, 2.5)
        val src1 = if (stepScenario1.isSmartCalculationValid) "⚡ [НАШ АЛГОРИТМ]" else "🚗 [ШТАТНОЕ ГУ]"
        println("  -> Отображаемый расход: $res1 л/100км | Источник: $src1")

        // -----------------------------------------------------------------
        // СЦЕНАРИЙ 2: Проверка защиты от "Моргания"
        // -----------------------------------------------------------------
        println("\n[Сценарий 2] Проверка сглаживания плескания (Таймер удержания):")
        val sc2EntryA = FuelEntry(0.0, 0.0, 45.0, 45.0, 0.0, testTemp, isEngineRunning = true, currentSpeedKmH = 60.0)
        val sc2EntryB = FuelEntry(0.0, 0.0, 44.5, 44.5, 0.0, testTemp, isEngineRunning = true, currentSpeedKmH = 60.0)
        val sc2EntryWave = FuelEntry(0.0, 0.0, 44.8, 44.8, 0.0, testTemp, isEngineRunning = true, currentSpeedKmH = 60.0)

        estimator.getCorrectedLiters(sc2EntryA, minConfidenceThreshold = 0.0)
        estimator.getCorrectedLiters(sc2EntryB, minConfidenceThreshold = 0.0)
        val waveStep = estimator.getCorrectedLiters(sc2EntryWave, minConfidenceThreshold = 0.0)

        val res2 = efficiencyCalculator.getHybridConsumption(9.5, waveStep.isSmartCalculationValid, 12.0, 150.0, 2.5)
        val src2 = if (waveStep.isSmartCalculationValid) "⚡ [НАШ АЛГОРИТМ]" else "🚗 [ШТАТНОЕ ГУ]"
        println("  -> Датчик прыгнул вверх! Расход: $res2 л/100км | Источник: $src2")

        // -----------------------------------------------------------------
        // СЦЕНАРИЙ 3: Проверка "Пробки / Холостого хода"
        // -----------------------------------------------------------------
        println("\n[Сценарий 3] Машина долго стоит мертвой пробке (Скорость = 0):")
        var idleStep = waveStep
        val idleEntry = FuelEntry(0.0, 0.0, 44.0, 44.0, 0.0, testTemp, isEngineRunning = true, currentSpeedKmH = 0.0)
        for (i in 1..55) {
            idleStep = estimator.getCorrectedLiters(idleEntry, minConfidenceThreshold = 0.0)
        }
        val res3 = efficiencyCalculator.getHybridConsumption(9.5, idleStep.isSmartCalculationValid, 12.0, 150.0, 2.5)
        val src3 = if (idleStep.isSmartCalculationValid) "⚡ [НАШ АЛГОРИТМ]" else "🚗 [ШТАТНОЕ ГУ]"
        println("  -> Стоим больше 30 секунд. Расход: $res3 л/100км | Источник: $src3")

        // -----------------------------------------------------------------
        // СЦЕНАРИЙ 4: Проверка режима "Трасса"
        // -----------------------------------------------------------------
        println("\n[Сценарий 4] Езда по трассе (Скорость = 90, уровень падает очень редко):")
        var highwayStep = waveStep
        val highwayEntry = FuelEntry(0.0, 0.0, 44.0, 44.0, 0.0, testTemp, isEngineRunning = true, currentSpeedKmH = 90.0)
        for (i in 1..40) {
            highwayStep = estimator.getCorrectedLiters(highwayEntry, minConfidenceThreshold = 0.0)
        }
        val res4 = efficiencyCalculator.getHybridConsumption(9.5, highwayStep.isSmartCalculationValid, 12.0, 150.0, 2.5)
        val src4 = if (highwayStep.isSmartCalculationValid) "⚡ [НАШ АЛГОРИТМ]" else "🚗 [ШТАТНОЕ ГУ]"
        println("  -> Едем по трассе 40 сек. Расход: $res4 л/100км | Источник: $src4")
    }

    fun runHardModeTest() {
        println("\n=== ТЕСТ-КЕЙС: Тяжелые пробки / Зима ===")
        val report = efficiencyCalculator.getSummary(8.0, 10.0, 5.0)
        println(report)
    }
}