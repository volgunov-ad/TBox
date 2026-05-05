package vad.dashing.tbox.fuellevelcalibration

/**
 * Результат оценки остатка топлива после коррекции по калибровке.
 */
data class EstimationResult(
    val litersActual: Double,    // Реальный объем при текущей температуре (плавает)
    val litersStandard: Double,  // Объем, приведенный к +15°C (стабильный для UI)
    val confidence: Double // Уровень уверенности (0.0 - 1.0)
)
