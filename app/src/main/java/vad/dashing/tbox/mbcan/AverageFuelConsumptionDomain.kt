package vad.dashing.tbox.mbcan

/**
 * Cluster average fuel consumption (L/100 km) from ICM_4.
 *
 * A9: `MBCanVehicleIcmInfo.getICM_4_AverageFuelConsume()` is already a float in L/100 km.
 * A10: VHAL `R_0900_ICM_4_AverageFuelConsume` uses the same 0.1 L/100 km scale as
 * instant fuel (`FuelRollingCounter` / `convertOilInteger`). Values ≤ 0 → no data.
 */
object AverageFuelConsumptionDomain {
    fun decodeMbCanLitersPer100Km(raw: Float): Float? {
        if (!raw.isFinite() || raw <= 0f) return null
        return raw
    }

    fun decodeVhalRaw(raw: Int): Float? = InstantFuelConsumptionDomain.decodeRawCounter(raw)
}
