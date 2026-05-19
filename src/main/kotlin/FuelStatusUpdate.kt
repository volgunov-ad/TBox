data class FuelStatusUpdate(
    val finalConsumption: Double,   // Итоговый пересчитанный средний расход (л/100км)
    val fuelConsumedSinceLast: Double, // Сколько литров топлива израсходовано с ПРОШЛОГО вызова (л)
    val totalLitersThisTrip: Double, // Всего сожжено литров за этот цикл поездки/бака (л)
    val isSmartActive: Boolean,       // Горит ли сейчас наш алгоритм (true) или штатное ГУ (false)
    val remainingLiters: Double,       // ОСТАТОК В ЛИТРАХ: на экран текстом (например, 45.2 л)
    val remainingPercent: Int          // ОСТАТОК В ПРОЦЕНТАХ: для прогресс-бара/шкалы (например, 79%)
)