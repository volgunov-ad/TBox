class FuelPhysics(private val expansionCoeff: Double = 0.0011) {
    // Приводим к стандарту +15
    fun toStandard(liters: Double, temp: Double): Double =
        liters / (1 + expansionCoeff * (temp - 15))

    // Из стандарта в реальный объем при текущей темп.
    fun fromStandard(standardLiters: Double, temp: Double): Double =
        standardLiters * (1 + expansionCoeff * (temp - 15))
}