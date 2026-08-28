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
    fun decode(raw: Int): WiperOperatingMode? = when (raw) {
        0 -> WiperOperatingMode.Off
        1 -> WiperOperatingMode.Intermittent
        2 -> WiperOperatingMode.Low
        3 -> WiperOperatingMode.High
        else -> null
    }
}
