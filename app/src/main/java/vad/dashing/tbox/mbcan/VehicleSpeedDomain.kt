package vad.dashing.tbox.mbcan

/**
 * A10 VHAL vehicle speed.
 *
 * Full-width signals (VSOSig): `км/ч = raw / 16`.
 *
 * ICM [FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_DISPLAY_PROPERTY_ID] on Dashing often delivers
 * only the low byte of that scaled raw (values `0,16,…,240`). Plain `/16` then yields
 * `speed mod 16`. [decodeDisplayTruncatedRaw] unwraps that residue across 0↔15 jumps.
 *
 * Prefer VSOSig when its raw > 0; otherwise Display (see [resolvePreferredKmh]).
 */
object VehicleSpeedDomain {
    private const val SCALE = 1f / 16f
    /** Modulo window of truncated Display residue after `/16`. */
    const val DISPLAY_WRAP_KMH = 16f
    private const val WRAP_HALF = DISPLAY_WRAP_KMH / 2f
    /** Raw above this is treated as full-width (not a single truncated byte). */
    private const val TRUNCATED_RAW_MAX = 255f

    data class DisplaySpeedUnwrapState(
        val lastResidue: Float? = null,
        val unwrapOffset: Float = 0f,
    ) {
        companion object {
            fun fromAbsoluteKmh(kmh: Float): DisplaySpeedUnwrapState {
                if (!kmh.isFinite() || kmh < 0f) return DisplaySpeedUnwrapState()
                val residue = kmh % DISPLAY_WRAP_KMH
                return DisplaySpeedUnwrapState(
                    lastResidue = residue,
                    unwrapOffset = kmh - residue,
                )
            }
        }
    }

    data class PreferredSpeedResult(
        val kmh: Float?,
        val unwrapState: DisplaySpeedUnwrapState,
    )

    fun decodeVhalRaw(raw: Number): Float? {
        val numeric = raw.toFloat()
        if (!numeric.isFinite() || numeric < 0f) return null
        return numeric * SCALE
    }

    /**
     * Decode truncated Display raw (`≤255`, typically multiples of 16) with phase unwrap.
     * Raw `>255` is treated as full-width `raw/16` and resyncs unwrap state.
     */
    fun decodeDisplayTruncatedRaw(
        raw: Number,
        state: DisplaySpeedUnwrapState,
    ): PreferredSpeedResult {
        val numeric = raw.toFloat()
        if (!numeric.isFinite() || numeric < 0f) {
            return PreferredSpeedResult(kmh = null, unwrapState = state)
        }
        if (numeric > TRUNCATED_RAW_MAX) {
            val kmh = numeric * SCALE
            return PreferredSpeedResult(kmh = kmh, unwrapState = DisplaySpeedUnwrapState.fromAbsoluteKmh(kmh))
        }
        val residue = numeric * SCALE
        val prev = state.lastResidue
        var offset = state.unwrapOffset
        if (prev != null) {
            val diff = residue - prev
            if (diff > WRAP_HALF) offset -= DISPLAY_WRAP_KMH
            if (diff < -WRAP_HALF) offset += DISPLAY_WRAP_KMH
        }
        val kmh = residue + offset
        return PreferredSpeedResult(
            kmh = kmh.coerceAtLeast(0f),
            unwrapState = DisplaySpeedUnwrapState(lastResidue = residue, unwrapOffset = offset),
        )
    }

    /**
     * Dual-source select: if [vsoRaw] is present and > 0, use full-width `/16` and resync unwrap;
     * otherwise decode [displayRaw] with truncation unwrap.
     */
    fun resolvePreferredKmh(
        vsoRaw: Number?,
        displayRaw: Number?,
        unwrapState: DisplaySpeedUnwrapState = DisplaySpeedUnwrapState(),
    ): PreferredSpeedResult {
        val vsoNumeric = vsoRaw?.toFloat()?.takeIf { it.isFinite() && it >= 0f }
        if (vsoNumeric != null && vsoNumeric > 0f) {
            val kmh = decodeVhalRaw(vsoNumeric)!!
            return PreferredSpeedResult(
                kmh = kmh,
                unwrapState = DisplaySpeedUnwrapState.fromAbsoluteKmh(kmh),
            )
        }
        if (displayRaw != null) {
            return decodeDisplayTruncatedRaw(displayRaw, unwrapState)
        }
        if (vsoNumeric != null) {
            // Explicit zero from VSOSig while Display missing.
            return PreferredSpeedResult(
                kmh = 0f,
                unwrapState = DisplaySpeedUnwrapState.fromAbsoluteKmh(0f),
            )
        }
        return PreferredSpeedResult(kmh = null, unwrapState = unwrapState)
    }
}
