package vad.dashing.tbox.fuellevelcalibration

/**
 * Термокомпенсация объёма топлива (как во внешней ветке tochBak).
 */
class FuelPhysics(private val expansionCoeff: Double = 0.0011) {

    /** Приводим литры к стандарту +15 °C. */
    fun toStandard(liters: Double, temp: Double): Double =
        liters / (1.0 + expansionCoeff * (temp - 15))

    /** Из стандарта +15 °C в реальный объём при текущей температуре. */
    fun fromStandard(standardLiters: Double, temp: Double): Double =
        standardLiters * (1.0 + expansionCoeff * (temp - 15))
}
