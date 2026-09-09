package vad.dashing.tbox.mbcan

/**
 * CEM high-beam headlights status (`MBCanLightStatus.nHighBeamSts` /
 * `R_0404_CEM_2_HighBeamSts`).
 *
 * CEM 1-bit: **1** on / **0** off; any other raw → unknown.
 * Binary trigger state only, not the 1…4 [MbCanKnownVehiclePropertyId.LIGHTCONTROL] mode.
 */
object HighBeamDomain {
    fun decodeOn(raw: Int): Boolean? = when (raw) {
        1 -> true
        0 -> false
        else -> null
    }
}
