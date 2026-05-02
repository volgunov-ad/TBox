import kotlin.math.abs

class FuelSystemSimulator(
    private val estimator: FuelSmartEstimator,
    private val filter: FuelFilter
) {
    private val efficiencyCalculator = FuelEfficiencyCalculator()

    fun runTestScenario() {
        // Тестовые данные: добавим побольше моточасов для наглядности
        val history = listOf(
            //FuelEntry(1000.0, 100.0, 5.0, 25.0, 22.0, 20.0), // +20 град, 100 мч
            //FuelEntry(1500.0, 110.0, 10.0, 45.0, 38.0, -5.0), // -5 град, 110 мч (проехали 500км за 10ч)

            // ТЕСТ ШУМА: В чеке 50л, а датчик поднялся всего на 5л (с 5 до 10)
            // Разница колоссальная (90%), фильтр должен это "забанить"
            //FuelEntry(2000.0, 100.0, 10.0, 30.0, 21.0, 15.0),

            FuelEntry(1000.0, 100.0, 10.0, 48.0, 40.0, 25.0),

            FuelEntry(1000.0, 100.0, 48.0, 48.0, 0.0, 10.0)


        )

        history.forEachIndexed { index, entry ->
            val sensorDelta = entry.sensorAfter - entry.sensorBefore // Реальная разница
            val deviation = filter.calculateDeviation(entry)
            val threshold = filter.maxDeviationPercent

            if (filter.isValid(entry)) {
                val report = estimator.train(entry)
                println("[Запись #${index + 1}] $report (Отклонение: ${"%.1f".format(deviation * 100)}%)")
            } else {
                println("[Запись #${index + 1}] ⚠️ ОТКЛОНЕНО ФИЛЬТРОМ!")

                if (sensorDelta <= 0) {
                    println("   -> Причина: Уровень топлива не поднялся (Дельта: $sensorDelta л). Заправка невозможна.")
                } else {
                    println("   -> Отклонение: ${"%.1f".format(deviation * 100)}% (Порог: ${"%.1f".format(threshold * 100)}%)")
                    println("   -> Датчик увидел: $sensorDelta л, а в чеке: ${entry.litersByCheck} л")
                }
            }
        }


        // ПЕРЕМЕННЫЕ ДЛЯ СБОРА ИТОГОВ
        var totalLiters = 0.0
        var totalDistance = 0.0
        var totalHours = 0.0

        println("\n=== 1. Имитация обучения (Заправки) ===")

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