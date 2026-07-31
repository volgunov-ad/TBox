package vad.dashing.tbox.mbcan

/**
 * PM2.5 concentration (µg/m? style density) for inside/outside air-quality tiles.
 *
 * A9: `MBCanPM25` Indensity / outdensity as-is (`MBPM25View`).
 * A10: VHAL `R_0400_PM2_5_Indensity` / `Outdensity` shown as raw int (HVAC `updatePm25Txt`).
 * Matches TBox gate: density must be in 1…65534.
 */
object Pm25AirQualityDomain {
    fun decodeDensity(raw: Int): UInt? {
        if (raw <= 0 || raw >= 65535) return null
        return raw.toUInt()
    }

    fun decodeDensity(raw: Short): UInt? = decodeDensity(raw.toInt())
}
