class FuelSmartEstimator(
    val tankCapacity: Double = 50.0,      // Максимальный паспортный объем бака
    private val sensorMin: Double = 2.0,  // "Мертвая зона" снизу: уровень, ниже которого поплавок физически не опускается
    private val sensorMax: Double = 48.0, // "Мертвая зона" сверху: уровень, выше которого поплавок упирается в потолок
    zoneCount: Int = 5                   // Количество участков, на которые мы дробим бак для калибровки нелинейности
) {
    // ОБЪЕКТЫ-ПОМОЩНИКИ (ООП Делегаты)

    // Отвечает за физические свойства: термокомпенсацию и приведение литров к +15 градусам
    private val physics = FuelPhysics()

    // Отвечает за хранение "опыта": массивы данных по каждой зоне и расчет их коэффициентов
    private val store = CalibrationStore(zoneCount, tankCapacity, maturityThreshold = 80.0)

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
            println("--- Данные калибровки успешно загружены из JSON ---")
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
    fun getCorrectedLiters(currentSensorValue: Double, currentTemp: Double): EstimationResult {
        // Защита: не даем значению датчика выйти за пределы физического бака
        val safeSensor = currentSensorValue.coerceIn(0.0, tankCapacity)

        // Определяем индекс текущей зоны бака
        val zoneIdx = (safeSensor / store.zoneSize).toInt().coerceIn(0, store.zoneCount - 1)

        // Получаем коэффициенты: локальный (для этой зоны) и глобальный (средний по баку)
        val kLocal = store.getZoneK(zoneIdx)
        val kGlobal = store.getGlobalK()

        // Насколько мы доверяем данным именно в этой зоне (от 0.0 до 1.0)
        val confidence = store.getConfidence(zoneIdx)

        // СМЕШИВАНИЕ: если уверенность низкая, берем больше от глобального коэффициента
        val kFinal = (kLocal * confidence) + (kGlobal * (1.0 - confidence))

        // Считаем стандартный объем (+15), а затем расширяем его до реального при текущей температуре
        val stdVolume = safeSensor * kFinal
        val realVolume = physics.fromStandard(stdVolume, currentTemp)

        // Флаг: находимся ли мы в "мертвой зоне" датчика (в самом верху или внизу)
        val isAtLimit = currentSensorValue >= sensorMax || currentSensorValue <= sensorMin

        return EstimationResult(
            realVolume.coerceAtMost(tankCapacity + 5), // +5л "запас" на горловину
            if (isAtLimit) confidence * 0.7 else confidence // Снижаем уверенность на краях бака
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