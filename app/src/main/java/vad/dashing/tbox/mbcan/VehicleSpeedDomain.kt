package vad.dashing.tbox.mbcan

/**
 * A10 VHAL vehicle speed: `км/ч = UINT16(raw) / 16` for both
 * [FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID] and
 * [FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_DISPLAY_PROPERTY_ID].
 *
 * Prefer VSOSig when its raw > 0; otherwise Display (see [resolvePreferredKmh]).
 */
object VehicleSpeedDomain {
    private const val SCALE = 1f / 16f

    fun decodeVhalRaw(raw: Number): Float? {
        val numeric = raw.toFloat()
        if (!numeric.isFinite() || numeric < 0f) return null
        return numeric * SCALE
    }

    /**
     * Dual-source select: if [vsoRaw] is present and > 0, use its decoded km/h;
     * otherwise use [displayRaw] (including when VSOSig is 0 / missing).
     */
    fun resolvePreferredKmh(vsoRaw: Number?, displayRaw: Number?): Float? {
        val vsoNumeric = vsoRaw?.toFloat()?.takeIf { it.isFinite() && it >= 0f }
        if (vsoNumeric != null && vsoNumeric > 0f) {
            return decodeVhalRaw(vsoNumeric)
        }
        val displayNumeric = displayRaw?.toFloat()?.takeIf { it.isFinite() && it >= 0f }
        if (displayNumeric != null) {
            return decodeVhalRaw(displayNumeric)
        }
        return vsoNumeric?.let { decodeVhalRaw(it) }
    }
}
