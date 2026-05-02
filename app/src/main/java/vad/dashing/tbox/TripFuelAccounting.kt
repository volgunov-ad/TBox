package vad.dashing.tbox

/**
 * Учёт топлива по поездке по **калиброванным литрам** (в репозитории — `litersStandard` с CAN, стабильно к +15 °C).
 * Заправка — резкий рост уровня; мелкие падения игнорируются как шум датчика.
 */
object TripFuelAccounting {

    /** Игнорировать падение уровня меньше этого (в п.п. процента), пересчитанное в литры по номинальному баку. */
    const val CONSUME_MIN_DELTA_PERCENT = 0.3f

    /** Рост за один шаг не меньше этого (п.п.) считается заправкой; порог в литрах = бак × доля. */
    const val REFUEL_RISE_PERCENT = 4f

    data class FuelCalibratedStepResult(
        val consumedLiters: Float,
        /** Последний принятый уровень в калиброванных литрах (стандарт). */
        val baselineCalibratedLiters: Float,
        /** Текущий отфильтрованный процент CAN (для записи в поездку и в заправку до/после). */
        val baselinePercent: Float,
        val refuelDetected: Boolean,
        /** Добавленные литры при [refuelDetected] (дельта калиброванных литров). */
        val refueledLitersThisStep: Float,
    )

    /**
     * @param currentConsumedLiters накопленный расход по поездке
     * @param lastCalibratedLiters предыдущий уровень в калиброванных л, или null на первом шаге
     * @param litersNow текущий уровень в калиброванных л (того же смысла, что last)
     * @param baselinePercentNow текущий отфильтрованный % с CAN
     * @param tankLiters номинальный объём бака для перевода порогов из % в литры
     */
    fun applyFuelCalibratedLitersStep(
        currentConsumedLiters: Float,
        lastCalibratedLiters: Float?,
        litersNow: Float,
        baselinePercentNow: Float,
        tankLiters: Float,
    ): FuelCalibratedStepResult {
        val refuelRiseLiters = tankLiters * (REFUEL_RISE_PERCENT / 100f)
        val consumeMinLiters = tankLiters * (CONSUME_MIN_DELTA_PERCENT / 100f)
        if (lastCalibratedLiters == null) {
            return FuelCalibratedStepResult(
                consumedLiters = currentConsumedLiters,
                baselineCalibratedLiters = litersNow,
                baselinePercent = baselinePercentNow,
                refuelDetected = false,
                refueledLitersThisStep = 0f,
            )
        }
        val delta = litersNow - lastCalibratedLiters
        val refuel = delta >= refuelRiseLiters
        val consumed = when {
            refuel -> currentConsumedLiters
            delta <= -consumeMinLiters -> currentConsumedLiters + (-delta)
            else -> currentConsumedLiters
        }
        val refueledStep = if (refuel) delta else 0f
        return FuelCalibratedStepResult(
            consumedLiters = consumed,
            baselineCalibratedLiters = litersNow,
            baselinePercent = baselinePercentNow,
            refuelDetected = refuel,
            refueledLitersThisStep = refueledStep,
        )
    }
}
