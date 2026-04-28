class FuelEfficiencyCalculator(
    private var baseIdleFactor: Double = 30.0 // Базовый: 1 час простоя = 30 км
) {
    // Средний расход на холостых для обычного мотора (литров в час)
    private val litersPerHourIdle = 0.8

    /**
     * Возвращает детальный отчет с разделением на трассу и простой
     */
    fun getSummary(totalLiters: Double, distance: Double, hours: Double): String {
        // 1. Адаптивный коэффициент пробок
        val avgSpeed = if (hours > 0) distance / hours else 0.0
        val adaptiveFactor = if (avgSpeed < 15.0 && avgSpeed > 0) {
            baseIdleFactor * 0.7 // В глухих пробках снижаем эквивалент пробега
        } else {
            baseIdleFactor
        }

        // 2. Вычисляем, сколько литров ушло чисто на стояние (холостой ход)
        val idleLiters = hours * litersPerHourIdle
        val drivingLiters = (totalLiters - idleLiters).coerceAtLeast(0.0)

        // 3. Эквивалентный расчет для общего понимания
        val eqDistance = distance + (hours * adaptiveFactor)
        val totalConsumption = if (eqDistance > 0) (totalLiters / eqDistance) * 100 else 0.0

        // 4. "Чистый" путевой расход (только когда колеса крутились)
        val pureDrivingConsumption = if (distance > 0) (drivingLiters / distance) * 100 else 0.0

        return """
            --- Итоги по расходу ---
            Пройдено: $distance км | Время: $hours ч | Ср. скорость: ${"%.1f".format(avgSpeed)} км/ч
            Всего сожжено: ${"%.1f".format(totalLiters)} л
              -> На холостых (стоя): ${"%.1f".format(idleLiters)} л
              -> В движении (чистое): ${"%.1f".format(drivingLiters)} л
            
            Средний расход (общий): ${"%.2f".format(totalConsumption)} л/100км
            Чистый путевой расход: ${"%.2f".format(pureDrivingConsumption)} л/100км
        """.trimIndent()
    }

    fun calculateSmartConsumption(liters: Double, distance: Double, hours: Double): Double {
        // Эквивалентный пробег: реальный путь + имитация пути за счет работы мотора
        val totalEquivalentDistance = distance + (hours * baseIdleFactor)

        return if (totalEquivalentDistance > 0) {
            (liters / totalEquivalentDistance) * 100
        } else 0.0
    }
}
