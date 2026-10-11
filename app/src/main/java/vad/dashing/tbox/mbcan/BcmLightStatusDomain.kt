package vad.dashing.tbox.mbcan

/**
 * BCM light bytes from the 2026-10-10 and 2026-10-11 journals.
 *
 * DRL (`nDRLSts`) sat at **2** while driving. It went to **1** when the trunk opened
 * (afternoon `162750`) and again while the hazard button was held
 * (`tbox_app_log_20261011_132614`, 13:26:54–13:27:03), then back to **2**.
 *
 * The hazard button and the trunk-open flash share the BCM pair `nTurnSts=3` +
 * `nDirectionLightSts=1`. Clear is **0+0**. Journal 132614 held **3+1** for the whole
 * flash. The turn-light object blinked both sides **2↔1** in that window, so it is
 * not the stable hazard source. `nHazardLightSts` stayed **0**.
 * Pairs **2+2** and **1+1** are not this flash.
 *
 * A high-beam flash at 16:29:10 pulsed `nHighBeamSts` and `nLowBeamSts` together
 * **1→2→1** for one second. The gesture was the high-beam flash ([HighBeamDomain]);
 * that one-second low-beam pulse is not a separate low-beam mode.
 *
 * Front fog, rear fog and park/tail stayed at **1**, and the user did not switch them.
 * `nLaserLightSts` went **1→2** for the trunk-open stop and back to **1** when the car
 * drove off. The lamp was not identified.
 */
object BcmLightStatusDomain {
    /** `nDRLSts`: **2** lit / **1** out. Other raw values are unknown. */
    fun decodeDrlOn(raw: Int): Boolean? = when (raw) {
        2 -> true
        1 -> false
        else -> null
    }

    /**
     * Trunk-open hazard on the BCM status bytes.
     * **3 + 1** is the flash; **0 + 0** is clear.
     * Other pairs (turn 2 with direction 2, turn 1 with direction 1) are not this flash.
     */
    fun decodeTrunkOpenHazard(turnSts: Int, directionLightSts: Int): Boolean? = when {
        turnSts == 3 && directionLightSts == 1 -> true
        turnSts == 0 && directionLightSts == 0 -> false
        else -> null
    }

    /**
     * Hazard for the live automation signal.
     * **3 + 1** stays on for the whole flash (hazard button and trunk-open).
     * Observed non-flash pairs **0+0**, **2+2** and **1+1** are off, so a turn
     * does not read as hazard. Any other pair is unknown.
     */
    fun decodeHazardActive(turnSts: Int, directionLightSts: Int): Boolean? = when {
        turnSts == 3 && directionLightSts == 1 -> true
        (turnSts == 0 && directionLightSts == 0) ||
            (turnSts == 2 && directionLightSts == 2) ||
            (turnSts == 1 && directionLightSts == 1) -> false
        else -> null
    }
}
