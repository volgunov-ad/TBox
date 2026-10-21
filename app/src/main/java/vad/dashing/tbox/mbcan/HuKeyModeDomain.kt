package vad.dashing.tbox.mbcan

/**
 * Key position from Android 9 `MBCanVehicleAccStatus.getIgnSts()` and Android 10
 * `R_0400_CEM_2_KeySts`. Same 2-bit CEM scale.
 */
object IgnitionKeyDomain {
    const val OFF = "off"
    const val ACC = "acc"
    const val ON = "on"
    const val CRANK = "crank"

    val OPTIONS: List<String> = listOf(OFF, ACC, ON, CRANK)

    fun decode(raw: Int): String? = when (raw) {
        0 -> OFF
        1 -> ACC
        2 -> ON
        3 -> CRANK
        else -> null
    }
}

/**
 * Head-unit power mode from Android 9 `eMBCAN_SYSTEMMODE` (`getSystemMode()`).
 * Factory `15_min_mode` is the HU hold after ACC/ignition drops, not remote start.
 * Android 10 has no equivalent property.
 */
object HuSystemModeDomain {
    const val STANDBY = "standby"
    const val ACTIVE = "active"
    const val HOLD_15M = "hold_15m"
    const val SLEEP = "sleep"

    val OPTIONS: List<String> = listOf(STANDBY, ACTIVE, HOLD_15M, SLEEP)

    fun decode(raw: Int): String? = when (raw) {
        1 -> STANDBY
        2 -> ACTIVE
        6 -> HOLD_15M
        7 -> SLEEP
        else -> null
    }
}
