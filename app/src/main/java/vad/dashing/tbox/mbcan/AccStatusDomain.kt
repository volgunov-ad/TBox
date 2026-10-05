package vad.dashing.tbox.mbcan

/**
 * Head-unit AccStatus for automations.
 *
 * Android 9 mbCAN `MBCanVehicleAccStatus.getAccStatus()` (type 6):
 * - **4** = ACC ON (extra-menu sunshade wait; Light Show «please use in ACC ON»)
 * - **5** = ON (HardKeyService maps 4 or 5 → KeySts=2)
 * - **0…3** = not ACC/ON
 *
 * Android 10 VHAL `MCU_REPLY_ACC_STATUS` (**557845540**) uses a different scale
 * (Launcher / CarSettings 1/2/3, not 4/5). See [decodeMcuReply].
 */
object AccStatusDomain {
    const val STATE_OFF = "off"
    const val STATE_ACC = "acc"
    const val STATE_IGN = "ign"

    val STATE_OPTIONS: List<String> = listOf(STATE_OFF, STATE_ACC, STATE_IGN)

    fun decodeMbCan(raw: Int): String? = when (raw) {
        4 -> STATE_ACC
        5 -> STATE_IGN
        in 0..3 -> STATE_OFF
        else -> null
    }

    /**
     * MCU_REPLY_ACC_STATUS on Adayo A10.
     *
     * CarSettings: **1** = settings available, **2** = 4 s transition, **3** = unavailable.
     * Launcher treats **2** and **3** as ACC-family for warning debounce — **3** is still
     * mapped to [STATE_OFF] here because CarSettings marks it unavailable.
     */
    fun decodeMcuReply(raw: Int): String? = when (raw) {
        0, 3 -> STATE_OFF
        1, 2 -> STATE_ACC
        else -> null
    }
}
