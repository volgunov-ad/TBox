package vad.dashing.tbox.fuellevelcalibration

/**
 * Результат оценки остатка топлива после коррекции по калибровке.
 */
data class EstimationResult(
    /** Скорректированный объём в баке, л. */
    val liters: Double,
    /** Уверенность в данных зоны, 0.0…1.0 (в UI пока не выводится). */
    val confidence: Double,
)
