package vad.dashing.tbox.mbcan

/**
 * Instantaneous fuel consumption (λ/100 κμ) from ICM FuelRollingCounter / engine telemetry.
 *
 * Stock: A9 `MBOilWearView` uses `getFuelRollingCounter() / 10`; A10 CarSettings
 * `NumberConvertUtils.convertOilInteger` uses `raw * 0.1`. Values ? 0 shown as "---".
 */
object InstantFuelConsumptionDomain {
    fun decodeRawCounter(raw: Int): Float? {
        if (raw <= 0) return null
        val litersPer100Km = raw / 10f
        return litersPer100Km.takeIf { it.isFinite() && it > 0f }
    }

    fun decodeRawCounter(raw: Short): Float? = decodeRawCounter(raw.toInt() and 0xFFFF)
}
