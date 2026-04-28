class FuelSystemSimulator(
    private val estimator: FuelSmartEstimator,
    private val filter: FuelFilter
) {
    private val efficiencyCalculator = FuelEfficiencyCalculator()

    fun runTestScenario() {
        // Тестовые данные: добавим побольше моточасов для наглядности
        val history = listOf(
            FuelEntry(1000.0, 100.0, 5.0, 25.0, 22.0, 20.0), // +20 град, 100 мч
            FuelEntry(1500.0, 110.0, 10.0, 45.0, 38.0, -5.0)  // -5 град, 110 мч (проехали 500км за 10ч)
        )

        // ПЕРЕМЕННЫЕ ДЛЯ СБОРА ИТОГОВ
        var totalLiters = 0.0
        var totalDistance = 0.0
        var totalHours = 0.0

        println("=== 1. Имитация обучения (Заправки) ===")

        // Перебираем историю парами, чтобы посчитать разницу между заправками
        for (i in 0 until history.size) {
            val entry = history[i]
            if (filter.isValid(entry)) {
                println(estimator.train(entry))

                // Накапливаем данные (начиная со второй заправки, когда есть дельта)
                if (i > 0) {
                    val prev = history[i-1]
                    totalLiters += entry.litersByCheck
                    totalDistance += (entry.odometerKm - prev.odometerKm)
                    totalHours += (entry.engineHours - prev.engineHours)
                }
            }
        }

        displayResults(totalLiters, totalDistance, totalHours)

        // Вызываем наш "тяжелый тест" вручную для проверки логики пробок
        runHardModeTest()
    }

    private fun displayResults(totalLiters: Double, totalDistance: Double, totalHours: Double) {
        println("\n=== 2. Состояние калибровки зон ===")
        estimator.getCalibrationReport().forEach { println(it) }

        println("\n=== 3. Проверка точности и Резерва (Smart Check) ===")
        val testSensorValue = 12.0 // Имитируем, что датчик сейчас показывает 12л
        val currentTemp = 10.0   // На улице +10
        val corrected = estimator.getCorrectedLiters(testSensorValue, currentTemp)

        // Берем для прогноза средний расход из нашего калькулятора (общий)
        val avgCons = efficiencyCalculator.calculateSmartConsumption(totalLiters, totalDistance, totalHours)
        val range = estimator.calculateRange(corrected.liters, if(avgCons > 0) avgCons else 8.5) // 8.5 как заглушка

        println("Датчик видит: $testSensorValue л | Реально в баке: ${"%.2f".format(corrected.liters)} л (Уверенность: ${(corrected.confidence * 100).toInt()}%)")
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
    }


    fun runHardModeTest() {
        println("\n=== ТЕСТ-КЕЙС: Тяжелые пробки / Зима ===")
        val report = efficiencyCalculator.getSummary(8.0, 10.0, 5.0)
        println(report)
    }
}