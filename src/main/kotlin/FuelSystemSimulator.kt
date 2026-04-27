class FuelSystemSimulator(
    private val estimator: FuelSmartEstimator,
    private val filter: FuelFilter
) {
    private val efficiencyCalculator = FuelEfficiencyCalculator()

    fun runTestScenario() {
        println("=== 1. Имитация обучения (Заправки) ===")
        val history = listOf(
            FuelEntry(1000.0, 50.0, 5.0, 25.0, 22.0, 20.0), // Заправка в +20
            FuelEntry(1500.0, 75.0, 10.0, 45.0, 38.0, -5.0)  // Заправка в -5
        )

        history.forEach { entry ->
            if (filter.isValid(entry)) {
                // Печатаем подробный отчет из метода train
                val report = estimator.train(entry)
                println(report)
            }
        }

        displayResults()
    }

    private fun displayResults() {
        println("\n=== 2. Состояние калибровки ===")
        estimator.getCalibrationReport().forEach { println(it) }

        println("\n=== 3. Проверка точности и Резерва ===")
        val testSensorValue = 12.0
        val currentTemp = 10.0
        val corrected = estimator.getCorrectedLiters(testSensorValue, currentTemp)

        // Допустим, средний расход у нас 8.5 л/100км
        val range = estimator.calculateRange(corrected.liters, 8.5)

        println("Датчик: $testSensorValue л | Реально: ${"%.2f".format(corrected.liters)} л (Уверенность: ${(corrected.confidence * 100).toInt()}%)")
        println("Прогноз запаса хода: ${"%.0f".format(range)} км")

        /*println("\n=== 4. Проверка безопасности (Детектор слива) ===")
        // Имитируем падение уровня на парковке
        println(estimator.detectFuelAnomaly(12.8, false)) // Базовая точка
        println(estimator.detectFuelAnomaly(12.0, false)) // Резкое падение */

        println("\n=== 5. Итоговая эффективность ===")
        println(efficiencyCalculator.getSummary(60.0, 700.0, 15.0))
    }
}