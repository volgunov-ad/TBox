package vad.dashing.tbox.mbcan

/**
 * `MBCanWpcStatus` from the afternoon journal `tbox_app_log_20261010_162750`.
 *
 * A phone without wireless charging was already on the pad. The user did not place
 * another phone. `nWPC_PhoneDetection_Status` stayed **0**. `nChargSts` pulsed **9**
 * (16:29:51 and 16:30:33) and back to **1**, with `fWPC_Electricity` at 0.
 * **9** is not a charge. **1** also occurred while that phone was still on the pad,
 * so it is not decoded here.
 */
object WpcStatusDomain {
    /** `nWPC_PhoneDetection_Status` **0**: no wireless-charging handset. */
    fun decodeWirelessPhoneDetected(raw: Int): Boolean? = when (raw) {
        0 -> false
        else -> null
    }

    /**
     * `nChargSts` **9**: the pad is not charging.
     * Other raw values, including **1** from the same window, stay unknown.
     */
    fun decodeCharging(raw: Int): Boolean? = when (raw) {
        9 -> false
        else -> null
    }
}
