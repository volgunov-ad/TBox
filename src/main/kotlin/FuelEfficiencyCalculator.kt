class FuelEfficiencyCalculator(
    private val idleToDistanceFactor: Double = 30.0 // 1 час простоя = 30 км пути
) {
    fun getSummary(totalLiters: Double, distance: Double, hours: Double): String {
        val eqDistance = distance + (hours * idleToDistanceFactor)
        val consumption = if (eqDistance > 0) (totalLiters / eqDistance) * 100 else 0.0

        return """
            --- Итоги по расходу ---
            Пройдено: $distance км
            Моточасы: $hours ч
            Эквивалентный пробег: ${"%.1f".format(eqDistance)} км
            Средний расход: ${"%.2f".format(consumption)} л/100км
        """.trimIndent()
    }
}