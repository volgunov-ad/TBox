package vad.dashing.tbox.mbcan

/**
 * CEM high-beam headlights status (`MBCanLightStatus.nHighBeamSts`, A9 BCM type 21).
 *
 * OEM enum scale confirmed on car: **2** on / **1** off — same CEM switch scale as
 * HDC / ESP off / TurnLight; **0** treated as off, any other raw → unknown.
 *
 * A flash on 2026-10-10 16:29:10 pulsed this byte and `nLowBeamSts` together
 * **1→2→1** for one second. The gesture was this flash. That low-beam pulse is
 * not a separate low-beam mode.
 */
object HighBeamDomain {
    fun decodeOn(raw: Int): Boolean? = when (raw) {
        2 -> true
        0, 1 -> false
        else -> null
    }
}
