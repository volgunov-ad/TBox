package vad.dashing.tbox.mbcan

/**
 * Accelerator and brake pedals from head-unit backends.
 *
 * **Gas:** EMS pedal position as percent 0…100 plus an invalid flag (`0` = valid).
 * Missing, non-finite, out of range, or invalid → `null`.
 *
 * **Brake:** Dashing BCM / VHAL `BrakePedalSts` is **not** the usual CEM 1-bit:
 * **2** = pressed, **1** = released; other encodings (including 0) → `null`.
 */
object PedalDomain {
    fun decodeGasPedalPercent(position: Float?, invalidRaw: Int?): Float? {
        if (invalidRaw != null && invalidRaw != 0) return null
        val percent = position ?: return null
        if (!percent.isFinite() || percent < 0f || percent > 100f) return null
        return percent
    }

    fun decodeBrakePressed(raw: Int): Boolean? = when (raw) {
        2 -> true
        1 -> false
        else -> null
    }
}
