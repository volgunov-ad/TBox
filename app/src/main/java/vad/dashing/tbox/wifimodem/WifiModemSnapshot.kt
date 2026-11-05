package vad.dashing.tbox.wifimodem

import vad.dashing.tbox.APNState
import vad.dashing.tbox.NetState
import vad.dashing.tbox.NetValues

/**
 * Rich status from a Wi‑Fi modem HTTP poll, plus projections into existing UI models.
 */
data class WifiModemSnapshot(
    val netState: NetState = NetState(),
    val netValues: NetValues = NetValues(),
    val apnState: APNState = APNState(),
    val apnStatus: Boolean = false,
    /** Firmware string (`cr_version` / `wa_inner_version`) when known. */
    val firmware: String = "",
    val rssiDbm: Int? = null,
    val rsrpDbm: Int? = null,
    val rsrqDb: Int? = null,
    val sinrDb: Int? = null,
    val lteBand: String = "",
    val cellId: String = "",
    val networkTypeRaw: String = "",
    val pppStatusRaw: String = "",
    val modemMainStateRaw: String = "",
) {
    /**
     * After CONNECT/DISCONNECT, seed PPP so a following blank HOME poll does not
     * reinstate the previous [apnStatus] via mapper retention.
     */
    fun withOptimisticMobileData(enabled: Boolean): WifiModemSnapshot = copy(
        apnStatus = enabled,
        apnState = apnState.copy(
            apnStatus = enabled,
            apnType = if (enabled) apnState.apnType.ifBlank { "LTE" } else "",
        ),
        pppStatusRaw = if (enabled) "ppp_connected" else "ppp_disconnected",
    )
}
