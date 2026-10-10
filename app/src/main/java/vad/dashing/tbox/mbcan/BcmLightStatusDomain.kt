package vad.dashing.tbox.mbcan

/**
 * BCM light bytes from the 2026-10-10 journals (`tbox_app_log_20261010_115553`, `162750`).
 *
 * DRL (`nDRLSts`) sat at **2** while driving. When the trunk opened, the user said the
 * running lights go out and the hazard lamps flash: DRL went to **1**, and the BCM pair
 * `nTurnSts=3` + `nDirectionLightSts=1` came up. The turn-light object showed both sides
 * **2** in that window, which [TurnSignalsDomain] already treats as hazard.
 * `nHazardLightSts` stayed **0** through that flash.
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
    /** `nDRLSts`: **2** lit / **1** out (trunk open). */
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
}
