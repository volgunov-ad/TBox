class FuelSmartEstimator(
    val tankCapacity: Double = 57.0,      // Максимальный паспортный объем бака
    private val sensorMin: Double = 2.0,  // "Мертвая зона" снизу: уровень, ниже которого поплавок физически не опускается
    private val sensorMax: Double = 55.0, // "Мертвая зона" сверху: уровень, выше которого поплавок упирается в потолок
    zoneCount: Int = 11,                  // Количество участков, на которые мы дробим бак для калибровки нелинейности
    // 1. Добавляем в переменные состояния класса FuelSmartEstimator:
    private var lastDrivingLevel: Double = -1.0,
    private var smartCalculationHoldTimer: Int = 0, // Счетчик секунд для удержания флага
) {
    // ОБЪЕКТЫ-ПОМОЩНИКИ (ООП Делегаты)

    // Отвечает за физические свойства: термокомпенсацию и приведение литров к +15 градусам
    private val physics = FuelPhysics()

    // Отвечает за хранение "опыта": массивы данных по каждой зоне и расчет их коэффициентов
    private val store = CalibrationStore(zoneCount, tankCapacity, maturityThreshold = 100.0)

    private val storage = FuelStorage()

    // ПЕРЕМЕННЫЕ СОСТОЯНИЯ

    // Хранит уровень топлива с предыдущего замера для работы детектора слива
    private var lastStableLevel: Double = -1.0

    // Константа: сколько литров в минуту двигатель точно не может съесть (для отсечения аномалий)
    private val maxIdleConsumptionPerMinute = 0.04

    /**
     * ОБУЧЕНИЕ: Метод распределяет литры из чека по зонам бака,
     * учитывая температуру окружающей среды.
     */

    init {
        // Пытаемся загрузить данные при старте
        storage.load()?.let { savedData ->
            store.restoreFrom(savedData)
            println("\n--- Данные калибровки успешно загружены из JSON ---")
        }
    }

    fun train(entry: FuelEntry): String {
        // Приводим литры из чека к стандарту (+15 градусов) через класс физики
        val stdLiters = physics.toStandard(entry.litersByCheck, entry.ambientTemp)

        // Считаем, сколько литров "увидел" датчик
        val sensorDelta = entry.sensorAfter - entry.sensorBefore

        if (sensorDelta <= 0) return "Ошибка: нет изменения уровня"

        val affectedZones = mutableListOf<Int>()

        // Распределяем литры по зонам
        for (i in 0 until store.zoneCount) {
            val zoneStart = i * store.zoneSize
            val zoneEnd = (i + 1) * store.zoneSize

            val overlapStart = maxOf(entry.sensorBefore, zoneStart)
            val overlapEnd = minOf(entry.sensorAfter, zoneEnd)

            if (overlapStart < overlapEnd) {
                affectedZones.add(i + 1)
                val overlapShare = (overlapEnd - overlapStart) / sensorDelta

                // Сохраняем данные в наше хранилище store
                store.addData(i, stdLiters * overlapShare, overlapEnd - overlapStart)
            }
        }

        storage.save(store.realLitersPerZone, store.sensorLitersPerZone)

        // ВОЗВРАЩАЕМ ТОТ САМЫЙ ПОДРОБНЫЙ ОТЧЕТ
        return "Заправка: с ${entry.sensorBefore}л до ${entry.sensorAfter}л " +
                "(Итого: ${"%.1f".format(stdLiters)}л станд.). " +
                "Затронуты зоны: ${affectedZones.joinToString(", ")}"
    }

    /**
     * ПРЕДСКАЗАНИЕ: Превращает "кривой" литраж датчика в реальный объем.
     */
    // ВАЖНО: Эти две переменные нужно добавить в самый верх класса FuelSmartEstimator,
    // туда, где у вас объявлены tankCapacity, sensorMin и sensorMax.

    fun getCorrectedLiters(
        entry: FuelEntry,
        minConfidenceThreshold: Double = 0.3, // Порог уверенности (30%) для активации алгоритма
    ): EstimationResult {
        // 1. СТАРУЮ МАТЕМАТИКУ ОСТАВЛЯЕМ БЕЗ ИЗМЕНЕНИЙ (Данные берутся из entry)
        val safeSensor = entry.sensorBefore.coerceIn(0.0, tankCapacity)
        val zoneIdx = (safeSensor / store.zoneSize).toInt().coerceIn(0, store.zoneCount - 1)

        val kLocal = store.getZoneK(zoneIdx)
        val kGlobal = store.getGlobalK()
        val confidence = store.getConfidence(zoneIdx)

        val kFinal = (kLocal * confidence) + (kGlobal * (1.0 - confidence))
        val stdVolume = safeSensor * kFinal
        val actualVolume = physics.fromStandard(stdVolume, entry.ambientTemp)
        val smoothThreshold = sensorMax - 1.5

        // 2. Сглаживаем Standard: если бак полный по датчику, пишем максимум
        val finalStd = if (stdVolume >= smoothThreshold) tankCapacity else stdVolume
        // 3. ИСПРАВЛЕННОЕ СГЛАЖИВАНИЕ ACTUAL:
        // Если базовый объем дошел до полного бака (finalStd == tankCapacity),
        // то и фактический Actual принудительно равен tankCapacity.
        // Горловина заполнена до краев, расширяться бензину физически некуда.
        val finalActual = if (stdVolume >= smoothThreshold) {
            tankCapacity
        } else {
            actualVolume
        }

        val isAtLimit = entry.sensorBefore >= sensorMax || entry.sensorBefore <= sensorMin
        // Выносим финальную уверенность в отдельную переменную, чтобы использовать ниже
        val finalConfidence = if (isAtLimit) confidence * 0.7 else confidence

        // 2. ОБНОВЛЕННАЯ ЛОГИКА ТРИГГЕРА (С УЧЕТОМ ТРАССЫ, ЗАДНЕГО ХОДА И ПЕРЕДАЧИ ОБЪЕКТА FuelEntry)
        val absoluteSpeed = kotlin.math.abs(entry.currentSpeedKmH)
        val isMoving = absoluteSpeed > 5.0

        // Проверяем физическое изменение уровня топлива
        val (isFuelDroppingNow, isAbruptRefuel) = if (lastDrivingLevel < 0) {
            lastDrivingLevel = entry.sensorBefore
            Pair(false, false)
        } else {
            val delta = lastDrivingLevel - entry.sensorBefore // Положительная — падает, отрицательная — растет
            lastDrivingLevel = entry.sensorBefore

            val dropping = entry.isEngineRunning && delta > 0.01
            val abruptRefuel = delta < -0.5 // Если уровень резко вырос более чем на 0.5л (заправка на АЗС)
            Pair(dropping, abruptRefuel)
        }

        // Защита трассы и светофоров + форс-мажор заправки на ходу с работающим мотором
        if (isAbruptRefuel) {
            smartCalculationHoldTimer = 0 // Принудительно отдаем OEM-расход, пока бак заливается пистолетом
        } else if (entry.isEngineRunning && isMoving) {
            smartCalculationHoldTimer = 45 // Удерживаем наш алгоритм активным при стабильном движении
        } else if (smartCalculationHoldTimer > 0) {
            smartCalculationHoldTimer--
        }

        val isDropActive = smartCalculationHoldTimer > 0
        val isConfidenceOk = finalConfidence >= minConfidenceThreshold
        val shouldAnimateSmartConsumption = entry.isEngineRunning && isDropActive && isConfidenceOk

        // 3. ВОЗВРАЩАЕМ РЕЗУЛЬТАТ В UI
        return EstimationResult(
            litersActual = finalActual.coerceAtMost(tankCapacity + 5),
            litersStandard = finalStd.coerceAtMost(tankCapacity + 5),
            confidence = finalConfidence,
            isSmartCalculationValid = shouldAnimateSmartConsumption,
            tankCapacity = tankCapacity
        )
    }

    // --- Вспомогательные методы остаются без изменений ---
    fun calculateRange(liters: Double, avgCons: Double) = if (avgCons > 0) (liters / avgCons) * 100 else 0.0

    /*
    fun detectFuelAnomaly(currentLiters: Double, isEngineRunning: Boolean): String {
        if (lastStableLevel < 0) {
            lastStableLevel = currentLiters
            return "Мониторинг запущен"
        }

        val delta = lastStableLevel - currentLiters
        lastStableLevel = currentLiters

        return when {
            // Если мотор заглушен, а топливо уходит (больше чем на 0.15л)
            !isEngineRunning && delta > 0.15 -> "⚠️ ТРЕВОГА: Возможен слив! (-${"%.2f".format(delta)}л)"

            // ВОТ ЗДЕСЬ используем нашу переменную:
            // Если мотор заведен, но топливо уходит быстрее, чем 0.04 л/мин
            isEngineRunning && delta > maxIdleConsumptionPerMinute -> "⚠️ ВНИМАНИЕ: Аномальный расход! (-${"%.2f".format(delta)}л/мин)"

            else -> "✅ Уровень стабилен"
        }
    }*/

    fun getCalibrationReport(): List<String> {
        return (0 until store.zoneCount).map { i ->
            val zoneStart = (i * store.zoneSize).toInt()
            val zoneEnd = ((i + 1) * store.zoneSize).toInt()
            "Зона $zoneStart-$zoneEnd л: K=${"%.3f".format(store.getZoneK(i))} | Уверенность: ${(store.getConfidence(i)*100).toInt()}%"
        }
    }
}