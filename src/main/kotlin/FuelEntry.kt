data class FuelEntry (
    val odometerKm: Double,  // 1. Текущий пробег машины (км)
    val engineHours: Double,   // Добавили моточасы, подтянуть из tbox
    val sensorBefore: Double, // 2. Сколько литров видел датчик ДО заправки
    val sensorAfter: Double, // 3. Сколько литров увидел датчик ПОСЛЕ заправки
    val litersByCheck: Double, // 4. Реальные литры из чека АЗС
    val ambientTemp: Double = 15.0, // Температура за бортом
    // ---- НОВЫЕ ПОЛЯ ДЛЯ ГИБРИДНОГО РАСЧЕТА (v2.1) ----
    val isEngineRunning: Boolean = true,        // Флаг из CAN-шины: заведен ли двигатель автомобиля в данный момент
    val currentSpeedKmH: Double = 60.0,         // Текущая скорость автомобиля из CAN-шины (км/ч)
    val dashboardAvgConsumption: Double? = 11.0 // то что приходит со штатного ГУ, я иммитирую тут 11,0 л/100км, суда надо подставить значение, которое приходит из штатного ГУ
)