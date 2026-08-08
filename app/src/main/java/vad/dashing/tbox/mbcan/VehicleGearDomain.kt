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
     *
     * On Jetour Dashing HU the CEM polarity is inverted vs stock AAOS docs:
     * raw **1** = not reverse, raw **0** = reverse engaged.
     * (Geo log 2026-08-05: constant `true` on D/P with old 1→true decode; in R the
     * property often goes null — PRND `R` still covers that via [isReverseEngaged].)
     */
    fun decodeReverseGearSwitch(raw: Int): Boolean? = when (raw) {
        0 -> true
        1 -> false
        else -> null
    }

    /** Trim + uppercase PRND letter, or null if missing/blank. */
    fun normalizePrnd(mode: String?): String? =
        mode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()

    /**
     * Reverse for DR / mock location — priority ladder:
     * 1. HU PRND `R` → engaged
     * 2. HU PRND known and not `R` (P/N/D/…) → not engaged (ignore switch)
     * 3. HU PRND absent (e.g. MT) → CEM [reverseGearSwitch] == true
     * 4. else TBox PRND `R`
     *
     * [tboxGearBoxMode] is only consulted when HU PRND is unknown.
     */
    fun isReverseEngaged(
        reverseGearSwitch: Boolean?,
        huGearBoxMode: String?,
        tboxGearBoxMode: String? = null,
    ): Boolean {
        when (normalizePrnd(huGearBoxMode)) {
            "R" -> return true
            null -> Unit
            else -> return false
        }
        if (reverseGearSwitch == true) return true
        return normalizePrnd(tboxGearBoxMode) == "R"
    }
}
