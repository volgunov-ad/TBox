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
     * CEM reverse gear switch on **A9 mbCAN** (`getReverseGearSwitch`).
     *
     * Jetour Dashing: raw **0** and **2** = reverse engaged, raw **1** = not reverse.
     * **0** stays from the earlier CEM observation (geo log 2026-08-05: the old
     * 1→true decode stayed true on D/P). **2** is the value seen in R on 2026-10-10
     * (`tbox_app_log_20261010_115553`, `tbox_app_log_20261010_162750`): raw 2 while
     * `nGear=2`, raw 1 otherwise. Until 2 was engaged, [isReverseEngaged] saw a
     * missing switch exactly in reverse when HU PRND was absent.
     *
     * A10 VHAL uses [decodeVhalReverseGearSwitch] — opposite 0/1 polarity.
     */
    fun decodeReverseGearSwitch(raw: Int): Boolean? = when (raw) {
        0, 2 -> true
        1 -> false
        else -> null
    }

    /**
     * A10 VHAL `R_0400_CEM_2_ReverseGearSwitch` (**289412135**): classic CEM 1-bit
     * **1** = reverse engaged, **0** = not reverse.
     *
     * Field log `tbox_app_log_20261102_192540` (P→R→D): raw **1** only in the reverse
     * window, **0** on P/D — opposite of A9 [decodeReverseGearSwitch] for 0/1.
     * Raw **2** was not observed; keep **2→engaged** as a safe rare-enum mirror of
     * the A9 domain so an unexpected CEM ternary does not become null mid-R.
     */
    fun decodeVhalReverseGearSwitch(raw: Int): Boolean? = when (raw) {
        1, 2 -> true
        0 -> false
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
