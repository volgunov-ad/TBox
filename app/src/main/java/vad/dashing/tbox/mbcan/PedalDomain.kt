package vad.dashing.tbox.mbcan

/**
 * Accelerator and brake pedals from head-unit backends.
 *
 * **Gas:** EMS pedal position as percent 0…100 plus an invalid flag (`0` = valid).
 * Missing, non-finite, out of range, or invalid → `null`.
 *
 * **Brake:** CEM 1-bit ([TurnSignalsDomain.decodeCemBinaryActive]):
 * **1** = pressed, **0** = released; other encodings → `null`.
 * Same polarity as other CEM binary lights — not the inverted reverse-gear exception.
 */
object PedalDomain {
    fun decodeGasPedalPercent(position: Float?, invalidRaw: Int?): Float? {
        if (invalidRaw != null && invalidRaw != 0) return null
        val percent = position ?: return null
        if (!percent.isFinite() || percent < 0f || percent > 100f) return null
        return percent
    }

    fun decodeBrakePressed(raw: Int): Boolean? = TurnSignalsDomain.decodeCemBinaryActive(raw)
}
