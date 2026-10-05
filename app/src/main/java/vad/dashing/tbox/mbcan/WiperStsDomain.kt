package vad.dashing.tbox.mbcan

/**
 * Live front-wiper operating mode from BCM `WiperSts`.
 *
 * TTG (`Dl.a` / `getWiperSts()`): **0** Off, **1** INT (AUTO icon on some trims),
 * **2** Low, **3** High; any other raw → unknown (base icon). Wash is not in TTG.
 * Value **1** is always decoded as [WiperOperatingMode.Intermittent] here;
 * AUTO vs INT is a TTG equipment overlay, not a distinct CAN value.
 */
enum class WiperOperatingMode {
    Off,
    Intermittent,
    Low,
    High,
}

object WiperStsDomain {
    const val STATE_OFF = "off"
    const val STATE_INT = "int"
    const val STATE_LOW = "low"
    const val STATE_HIGH = "high"

    val STATE_OPTIONS: List<String> = listOf(STATE_OFF, STATE_INT, STATE_LOW, STATE_HIGH)

    fun decode(raw: Int): WiperOperatingMode? = when (raw) {
        0 -> WiperOperatingMode.Off
        1 -> WiperOperatingMode.Intermittent
        2 -> WiperOperatingMode.Low
        3 -> WiperOperatingMode.High
        else -> null
    }

    fun toAutomationState(mode: WiperOperatingMode): String = when (mode) {
        WiperOperatingMode.Off -> STATE_OFF
        WiperOperatingMode.Intermittent -> STATE_INT
        WiperOperatingMode.Low -> STATE_LOW
        WiperOperatingMode.High -> STATE_HIGH
    }
}
