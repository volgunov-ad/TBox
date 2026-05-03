package vad.dashing.tbox.fuellevelcalibration

import kotlin.math.min

/**
 * Оценка остатка топлива и обучение по заправкам (логика ветки tochBak; сохранение — через колбэк в DataStore).
 */
class FuelSmartEstimator(
    val tankCapacity: Double = 50.0,
    private val sensorMin: Double = 2.0,
    private val sensorMax: Double = 48.0,
    zoneCount: Int = 5,
    /** Литры датчика по зоне для полной уверенности в локальном K (см. [CalibrationStore.getConfidence]). */
    maturityThreshold: Double = 80.0,
    initialCalibration: CalibrationData? = null,
    /** Вызывается после каждого успешного [train], чтобы записать калибровку в настройки. */
    private val onCalibrationPersist: (CalibrationData) -> Unit,
) {

    private val physics = FuelPhysics()
    private val store = CalibrationStore(zoneCount, tankCapacity, maturityThreshold = maturityThreshold)

    init {
        initialCalibration?.let { store.restoreFrom(it) }
    }

    /**
     * Обучение: распределение литров по чеку по зонам бака с учётом температуры окружения.
     * @return текстовый отчёт для лога или UI
     */
    fun train(entry: FuelEntry): String {
        val stdLiters = physics.toStandard(entry.litersByCheck, entry.ambientTemp)
        val sensorDelta = entry.sensorAfter - entry.sensorBefore
        if (sensorDelta <= 0) return "Ошибка: нет изменения уровня"

        val affectedZones = mutableListOf<Int>()

        for (i in 0 until store.zoneCount) {
            val zoneStart = i * store.zoneSize
            val zoneEnd = (i + 1) * store.zoneSize

            val overlapStart = maxOf(entry.sensorBefore, zoneStart)
            val overlapEnd = minOf(entry.sensorAfter, zoneEnd)

            if (overlapStart < overlapEnd) {
                affectedZones.add(i + 1)
                val overlapShare = (overlapEnd - overlapStart) / sensorDelta
                store.addData(i, stdLiters * overlapShare, overlapEnd - overlapStart)
            }
        }

        onCalibrationPersist(store.snapshot())

        return "Заправка: с ${entry.sensorBefore}л до ${entry.sensorAfter}л " +
            "(Итого: ${"%.1f".format(stdLiters)}л станд.). " +
            "Затронуты зоны: ${affectedZones.joinToString(", ")}"
    }

    /**
     * Преобразование «кривого» показания датчика (л) в скорректированный объём при текущей температуре.
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

        // 1. Стабильный объем (при +15°C) — то, что не зависит от текущей жары/холода
        val stdVolume = safeSensor * kFinal

        // 2. Фактический объем (при текущей T) — сколько места бензин занимает сейчас
        val actualVolume = physics.fromStandard(stdVolume, currentTemp)

        // Порог сглаживания (например, 49.0 л)
        val smoothThreshold = tankCapacity * 0.98

        // 1. Сглаживаем Standard: если выше 49, рисуем 50
        val finalStd = if (stdVolume >= smoothThreshold) tankCapacity else stdVolume

        // 2. Сглаживаем Actual ПЛАВНО:
        // Если фактический объем (с учетом T) выше порога, мы тоже его подтягиваем,
        // НО оставляем ему возможность "дышать" от температуры.
        val finalActual = if (actualVolume >= smoothThreshold) {
            // Если база (stdVolume) уже полная, то и Actual должен
            // вращаться вокруг 50 литров, а не вокруг 52.6
            val ratio = actualVolume / stdVolume
            tankCapacity * ratio
        } else {
            actualVolume
        }

        val isAtLimit = currentSensorValue >= sensorMax || currentSensorValue <= sensorMin

        return EstimationResult(
            litersActual = finalActual.coerceAtMost(tankCapacity + 5),
            litersStandard = finalStd.coerceAtMost(tankCapacity + 5),
            confidence = if (isAtLimit) confidence * 0.7 else confidence
        )
    }

    fun calculateRange(liters: Double, avgCons: Double): Double =
        if (avgCons > 0) (liters / avgCons) * 100 else 0.0

    /** Строки для отображения состояния калибровки по зонам. */
    fun getCalibrationReport(): List<String> =
        (0 until store.zoneCount).map { i ->
            val zoneStart = (i * store.zoneSize).toInt()
            val zoneEnd = ((i + 1) * store.zoneSize).toInt()
            "Зона $zoneStart-$zoneEnd л: K=${"%.3f".format(store.getZoneK(i))} | Уверенность: ${(store.getConfidence(i) * 100).toInt()}%"
        }

    fun snapshotCalibration(): CalibrationData = store.snapshot()

    fun clearCalibration() {
        store.clear()
        onCalibrationPersist(store.snapshot())
    }
}
