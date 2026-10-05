package vad.dashing.tbox.mbcan

/**
 * Accelerator and brake pedals from head-unit backends.
 *
 * **Gas (mbCAN / A9):** EMS already delivers percent 0…100 plus an invalid flag (`0` = valid).
 * Missing, non-finite, out of range, or invalid → `null`.
 *
 * **Gas (VHAL / A10):** `R_0900_EMS_1_GasPedalPosition` is an **8-bit raw** (0…255), not percent.
 * Convert with `% = raw × 100 / 255`, then apply the same invalid / range checks.
 *
 * **Brake (mbCAN / A9):** Dashing BCM `BrakePedalSts` is **not** the usual CEM 1-bit:
 * **2** = pressed, **1** = released; other encodings (including 0) → `null`.
 *
 * **Brake (VHAL / A10):** `R_0400_CEM_2_BrakePedalSts` uses **0** = released, **1** = pressed
 * (field log showed `last=0` at rest; A9 1/2 scale does not apply).
 */
object PedalDomain {
    private const val VHAL_GAS_PEDAL_RAW_MAX = 255f

    fun decodeGasPedalPercent(position: Float?, invalidRaw: Int?): Float? {
        if (invalidRaw != null && invalidRaw != 0) return null
        val percent = position ?: return null
        if (!percent.isFinite() || percent < 0f || percent > 100f) return null
        return percent
    }

    /**
     * A10 VHAL: scale raw EMS pedal position (0…255) to percent 0…100.
     * Field evidence: property `289414943` pushes integers up to ~253 while mbCAN (A9) is already %.
     */
    fun decodeVhalGasPedalPercent(rawPosition: Float?, invalidRaw: Int?): Float? {
        if (invalidRaw != null && invalidRaw != 0) return null
        val raw = rawPosition ?: return null
        if (!raw.isFinite() || raw < 0f || raw > VHAL_GAS_PEDAL_RAW_MAX) return null
        return decodeGasPedalPercent(raw * 100f / VHAL_GAS_PEDAL_RAW_MAX, invalidRaw = 0)
    }

    /** A9 mbCAN: 2 = pressed, 1 = released. */
    fun decodeBrakePressed(raw: Int): Boolean? = when (raw) {
        2 -> true
        1 -> false
        else -> null
    }

    /** A10 VHAL: 1 = pressed, 0 = released. */
    fun decodeVhalBrakePressed(raw: Int): Boolean? = when (raw) {
        1 -> true
        0 -> false
        else -> null
    }
}
