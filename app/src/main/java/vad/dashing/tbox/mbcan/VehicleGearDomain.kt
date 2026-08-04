package vad.dashing.tbox.mbcan

/**
 * PRND / reverse-switch decode for HU backends, matching stock AAOS / VirtualCar constants:
 * `GEAR_NEUTRAL=1`, `GEAR_REVERSE=2`, `GEAR_PARK=4`, `GEAR_DRIVE=8`.
 *
 * Letters match TBox [vad.dashing.tbox.utils.CanFramesProcess] `gearBoxMode` strings.
 */
object VehicleGearDomain {
    /** Stock bitmask from mbCAN `MBCanVehicleSpeed.getGear()` / VHAL `CURRENT_GEAR` / `GEAR_SELECTION`. */
    fun decodePrndBitmask(raw: Int): String? = when (raw) {
        1 -> "N"
        2 -> "R"
        4 -> "P"
        8 -> "D"
        else -> null
    }

    /**
     * CEM reverse gear switch (`getReverseGearSwitch` / `R_0400_CEM_2_ReverseGearSwitch`).
     * Stock delivers **0** = not reverse, **1** = reverse engaged.
     */
    fun decodeReverseGearSwitch(raw: Int): Boolean? = when (raw) {
        0 -> false
        1 -> true
        else -> null
    }

    /**
     * Reverse is engaged if either the CEM switch is true or the PRND mode is `R`.
     * Unknown sources are ignored.
     */
    fun isReverseEngaged(
        reverseGearSwitch: Boolean?,
        gearBoxMode: String?,
    ): Boolean {
        if (reverseGearSwitch == true) return true
        return gearBoxMode?.trim()?.uppercase() == "R"
    }
}
