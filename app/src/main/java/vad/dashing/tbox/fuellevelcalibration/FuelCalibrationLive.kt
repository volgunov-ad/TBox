package vad.dashing.tbox.fuellevelcalibration

import vad.dashing.tbox.TripTelemetryRepository

/**
 * Единственный живой [FuelSmartEstimator] и синхронное обновление литров из
 * стабильного отфильтрованного % ([TripTelemetryRepository.fuelLevelPercentageFiltered]).
 * Пересборка экземпляра — при смене настроек в [vad.dashing.tbox.BackgroundService].
 *
 * При выключенном «Учитывать заправки» литры считаются линейно, без [FuelSmartEstimator].
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
    private var useCalibratedEstimator: Boolean = true
    private var linearTankLiters: Int = 50

    fun currentEstimator(): FuelSmartEstimator? = synchronized(lock) { estimator }

    fun configure(trackRefuels: Boolean, tankLiters: Int) {
        synchronized(lock) {
            useCalibratedEstimator = trackRefuels
            linearTankLiters = tankLiters.coerceAtLeast(1)
        }
    }

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
     * Вызывать до [TripTelemetryRepository.updateFuelLevelPercentageFiltered], когда буфер датчика
     * выдал очередное стабильное значение %.
     */
    fun applyFromStableFilteredPercent(percent: UInt) {
        if (!useCalibratedEstimator) {
            applyLinear(percent)
            return
        }
        val est = synchronized(lock) { estimator } ?: return
        applyWithEstimator(est, percent)
    }

    /**
     * После смены настроек калибровки, температуры или режима учёта заправок — пересчитать литры
     * по текущему сохранённому отфильтрованному %.
     */
    fun reapplyFromRepositoryFilteredPercentOrClear() {
        val p = TripTelemetryRepository.fuelLevelPercentageFiltered.value
        if (p == null) {
            clearCalibratedOutputs()
            return
        }
        if (!useCalibratedEstimator) {
            applyLinear(p)
            return
        }
        val est = synchronized(lock) { estimator }
        if (est == null) {
            clearCalibratedOutputs()
            return
        }
        applyWithEstimator(est, p)
    }

    private fun applyLinear(percent: UInt) {
        val tankL = synchronized(lock) { linearTankLiters }.toFloat()
        val liters = FuelLevelMath.linearLitersFromFilteredPercent(percent.toFloat(), tankL)
        TripTelemetryRepository.updateFuelLevelCalibratedLiters(liters)
        TripTelemetryRepository.updateFuelLevelCalibratedLitersActual(liters)
        TripTelemetryRepository.updateFuelCalibrationConfidence(null)
    }

    private fun applyWithEstimator(est: FuelSmartEstimator, percent: UInt) {
        val tankL = est.tankCapacity.toFloat().coerceAtLeast(1f)
        val sensorLiters = percent.toFloat() / 100f * tankL
        val temp = (TripTelemetryRepository.outsideTemperature.value ?: 15f).toDouble()
        val result = est.getCorrectedLiters(sensorLiters.toDouble(), temp)
        TripTelemetryRepository.updateFuelLevelCalibratedLiters(result.litersStandard.toFloat())
        TripTelemetryRepository.updateFuelLevelCalibratedLitersActual(result.litersActual.toFloat())
        TripTelemetryRepository.updateFuelCalibrationConfidence(result.confidence.toFloat())
    }

    private fun clearCalibratedOutputs() {
        TripTelemetryRepository.updateFuelLevelCalibratedLiters(null)
        TripTelemetryRepository.updateFuelLevelCalibratedLitersActual(null)
        TripTelemetryRepository.updateFuelCalibrationConfidence(null)
    }
}
