package vad.dashing.tbox.mbcan

/**
 * CEM high-beam headlights status (`MBCanLightStatus.nHighBeamSts`, A9 BCM type 21).
 *
 * OEM enum scale confirmed on car: **2** on / **1** off — same CEM switch scale as
 * HDC / ESP off / TurnLight; **0** treated as off, any other raw → unknown.
 */
object HighBeamDomain {
    fun decodeOn(raw: Int): Boolean? = when (raw) {
        2 -> true
        0, 1 -> false
        else -> null
    }
}
