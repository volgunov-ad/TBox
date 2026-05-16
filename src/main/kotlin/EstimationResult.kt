data class EstimationResult(
    val litersActual: Double,    // Реальный объем при текущей температуре (плавает)
    val litersStandard: Double,  // Объем, приведенный к +15°C (стабильный для UI)
    val confidence: Double, // Уровень уверенности (0.0 - 1.0)
    val isSmartCalculationValid: Boolean = false, // Флаг валидности нашего умного расчета для текущей зоны бака
    val tankCapacity: Double // Передаем сюда текущий объем бака при создании
){
    // ЕДИНАЯ ПЕРЕМЕННАЯ ПРОЦЕНТА БАКА
    // Котлин автоматически посчитает её при создании этого объекта
    val fuelPercent: Double
        get() = (litersStandard / tankCapacity) * 100
}
