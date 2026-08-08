package vad.dashing.tbox.mbcan

/**
 * Distance-to-empty (κμ) from fuel-level / ICM telemetry.
 *
 * A9: [MBCanVehicleFuelLevel.getDistenceToEmpty] float km as-is (`MBVehicleFuelLevelView`).
 * A10: VHAL `R_0900_ICM_4_DistenceToEmpty_Km` int km as-is (`unitMul=1`).
 * Stock UI treats ? 0 as no data.
 */
object DistanceToEmptyDomain {
    fun decodeKm(value: Float): Float? {
        if (!value.isFinite() || value <= 0f) return null
        return value
    }

    fun decodeKm(raw: Int): UInt? {
        if (raw <= 0) return null
        return raw.toUInt()
    }
}
