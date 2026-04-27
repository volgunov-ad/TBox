data class FuelEntry (
    val odometerKm: Double,  // 1. Текущий пробег машины (км)
    val engineHours: Double,   // Добавили моточасы, подтянуть из tbox
    val sensorBefore: Double, // 2. Сколько литров видел датчик ДО заправки
    val sensorAfter: Double, // 3. Сколько литров увидел датчик ПОСЛЕ заправки
    val litersByCheck: Double, // 4. Реальные литры из чека АЗС
    val ambientTemp: Double = 15.0 // Температура за бортом
)