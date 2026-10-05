package vad.dashing.tbox.mbcan

/**
 * ICM driver-info warning lamps (`MBCanVehicleIcmDriverInfo` / A10 ICM_4).
 *
 * A9 OEM Chinese prompts treat [nICM_EngineOil] / [nICM_Brakefluid] as warning text
 * (“please turn off engine check oil level” / “please add brake fluid”) — i.e. **active =
 * problem present**. Assumed CEM 1-bit scale (TBD on car): **1** warning / **0** ok;
 * any other raw → unknown. Do **not** use HighBeam-style 2=on unless evidence appears.
 */
object IcmWarningLampDomain {
    fun decodeWarningActive(raw: Int): Boolean? = when (raw) {
        1 -> true
        0 -> false
        else -> null
    }
}
