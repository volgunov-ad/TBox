class FuelManager(
    private val estimator: FuelSmartEstimator,
    private val efficiencyCalculator: FuelEfficiencyCalculator
) {
    // Внутренние накопители путевой статистики (скрыты от внешнего кода)
    private var totalLiters: Double = 0.0
    private var totalDistance: Double = 0.0
    private var totalHours: Double = 0.0

    // Переменные для расчета дельты "с прошлого раза"
    private var lastEntry: FuelEntry? = null
    private var lastStandardLiters: Double = -1.0

    /**
     * Основной метод. Принимает текущую точку телематики,
     * автоматически обновляет счетчики и выдает готовый результат.
     */
    fun processTelemetry(entry: FuelEntry): FuelStatusUpdate {
        val prev = lastEntry
        lastEntry = entry

        // 1. Считаем путевые дельты расстояния и времени с прошлого раза
        val distanceDelta = if (prev != null) { entry.odometerKm - prev.odometerKm } else 0.0
        val hoursDelta = if (prev != null) { entry.engineHours - prev.engineHours } else 0.0

        // 2. Прогоняем бак через эстиматор (получаем отфильтрованные литры)
        val corrected = estimator.getCorrectedLiters(entry)

        // 3. Считаем, сколько израсходовано литров С ПРОШЛОГО РАЗА
        val fuelConsumedSinceLast = if (lastStandardLiters < 0) {
            lastStandardLiters = corrected.litersStandard
            0.0
        } else {
            val consumed = lastStandardLiters - corrected.litersStandard
            lastStandardLiters = corrected.litersStandard
            // Берем только положительный расход (если уровень упал). Если вырос — это заправка (0.0)
            if (consumed > 0.01) consumed else 0.0
        }

        // 4. ТРИГГЕР АЗС: Если зафиксирована заправка > 20% бака — сбрасываем цикл
        val isRefueled = entry.sensorAfter > entry.sensorBefore + 2.0
        if (isRefueled) {
            val refillPercent = entry.litersByCheck / estimator.tankCapacity
            if (refillPercent > 0.20) {
                totalLiters = 0.0
                totalDistance = 0.0
                totalHours = 0.0
                efficiencyCalculator.resetTripStats()
            }
        }

        // 5. Накапливаем общую статистику текущего цикла
        if (prev != null) {
            totalLiters += fuelConsumedSinceLast
            totalDistance += distanceDelta
            totalHours += hoursDelta
        }

        // 6. Получаем пересчитанный гибридный расход
        val finalCons = efficiencyCalculator.getHybridConsumption(
            headUnitConsumption = entry.dashboardAvgConsumption,
            isSmartCalculationValid = corrected.isSmartCalculationValid,
            totalLiters = totalLiters,
            distance = totalDistance,
            hours = totalHours
        )

        return FuelStatusUpdate(
            finalConsumption = finalCons,
            fuelConsumedSinceLast = fuelConsumedSinceLast,
            totalLitersThisTrip = totalLiters,
            isSmartActive = corrected.isSmartCalculationValid,
            remainingLiters = corrected.litersActual,       // Передаем фактический остаток в литрах
            remainingPercent = corrected.fuelPercent.toInt() // Передаем автоматический процент бака
        )
    }
}