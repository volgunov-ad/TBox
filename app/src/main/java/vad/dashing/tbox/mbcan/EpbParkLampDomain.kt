package vad.dashing.tbox.mbcan

/**
 * EPB park lamp status (`MBCanVehicleBcmStatus.getEPBParkLampSts`, A9 BCM type 21).
 *
 * Assumed CEM-switch scale (TBD on car): **2** on / **0,1** off — same pattern as
 * [HighBeamDomain]; any other raw → unknown.
 */
object EpbParkLampDomain {
    fun decodeOn(raw: Int): Boolean? = when (raw) {
        2 -> true
        0, 1 -> false
        else -> null
    }
}
