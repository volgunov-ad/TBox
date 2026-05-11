package vad.dashing.tbox.fuellevelcalibration

import vad.dashing.tbox.CanDataRepository

/**
 * Единственный живой [FuelSmartEstimator] и синхронное обновление откалиброванных литров из
 * стабильного отфильтрованного % с CAN ([CanDataRepository.fuelLevelPercentageFiltered] записывается
 * уже после этого). Пересборка экземпляра — при смене настроек в [vad.dashing.tbox.BackgroundService].
 */
object FuelCalibrationLive {

    data class EstimatorSettingsKey(
        val tankLiters: Int,
        val zoneCount: Int,
        val calibrationJson: String,
        val maturityThresholdLiters: Int,
    )

    private val lock = Any()
    private var cachedKey: EstimatorSettingsKey? = null
    private var estimator: FuelSmartEstimator? = null

    fun currentEstimator(): FuelSmartEstimator? = synchronized(lock) { estimator }

    /**
     * Пересоздаёт оценщик при изменении настроечного ключа; при совпадающем ключе возвращает текущий.
     */
    fun bindEstimatorIfChanged(
        key: EstimatorSettingsKey,
        factory: () -> FuelSmartEstimator,
    ): FuelSmartEstimator? {
        synchronized(lock) {
            if (cachedKey == key) return estimator
            cachedKey = key
            estimator = factory()
            return estimator
        }
    }

    fun reset() {
        synchronized(lock) {
            cachedKey = null
            estimator = null
        }
    }

    /**
     * CAN: вызывать до [CanDataRepository.updateFuelLevelPercentageFiltered], когда буфер датчика
     * выдал очередное стабильное значение %.
     */
    fun applyFromStableFilteredPercent(percent: UInt) {
        val est = synchronized(lock) { estimator } ?: return
        applyWithEstimator(est, percent)
    }

    /**
     * После смены настроек калибровки или температуры окружения — пересчитать литры по текущему
     * сохранённому отфильтрованному %.
     */
    fun reapplyFromRepositoryFilteredPercentOrClear() {
        val p = CanDataRepository.fuelLevelPercentageFiltered.value
        if (p == null) {
            clearCalibratedOutputs()
            return
        }
        val est = synchronized(lock) { estimator }
        if (est == null) {
            clearCalibratedOutputs()
            return
        }
        applyWithEstimator(est, p)
    }

    private fun applyWithEstimator(est: FuelSmartEstimator, percent: UInt) {
        val tankL = est.tankCapacity.toFloat().coerceAtLeast(1f)
        val sensorLiters = percent.toFloat() / 100f * tankL
        val temp = (CanDataRepository.outsideTemperature.value ?: 15f).toDouble()
        val result = est.getCorrectedLiters(sensorLiters.toDouble(), temp)
        CanDataRepository.updateFuelLevelCalibratedLiters(result.litersStandard.toFloat())
        CanDataRepository.updateFuelLevelCalibratedLitersActual(result.litersActual.toFloat())
        CanDataRepository.updateFuelCalibrationConfidence(result.confidence.toFloat())
    }

    private fun clearCalibratedOutputs() {
        CanDataRepository.updateFuelLevelCalibratedLiters(null)
        CanDataRepository.updateFuelLevelCalibratedLitersActual(null)
        CanDataRepository.updateFuelCalibrationConfidence(null)
    }
}
