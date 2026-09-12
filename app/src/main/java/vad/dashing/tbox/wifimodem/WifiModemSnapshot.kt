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
)
