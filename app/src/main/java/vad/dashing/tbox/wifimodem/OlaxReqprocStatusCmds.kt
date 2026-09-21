package vad.dashing.tbox.wifimodem

/**
 * Preferred `/reqproc/proc_get` multi-read `cmd=` lists for Olax F95 status polls.
 * Field names taken from real-device HAR (`OlaxF95.har`).
 *
 * Home fills signal bars / PPP / realtime thrpt; device/radio fills RSSI/RSRP/IMEI.
 */
object OlaxReqprocStatusCmds {

    const val GET_PATH: String = "/reqproc/proc_get"
    const val POST_PATH: String = "/reqproc/proc_post"

    /** Home-screen poll (bars, PPP, operator, realtime thrpt). */
    val HOME: List<String> = listOf(
        "modem_main_state",
        "pin_status",
        "loginfo",
        "signalbar",
        "network_type",
        "sub_network_type",
        "network_provider",
        "ppp_status",
        "simcard_roam",
        "lan_ipaddr",
        "realtime_tx_bytes",
        "realtime_rx_bytes",
        "realtime_time",
        "realtime_tx_thrpt",
        "realtime_rx_thrpt",
    )

    /** Radio / net-info batch (RSSI/RSRP/SINR). */
    val RADIO: List<String> = listOf(
        "network_type",
        "sub_network_type",
        "rssi",
        "rscp",
        "lte_rscp",
        "lte_rsrp",
        "nv_rsrq",
        "nv_sinr",
        "lte_band",
        "cell_id",
    )

    /** Device / About identifiers + radio duplicates. */
    val DEVICE: List<String> = listOf(
        "imei",
        "imsi",
        "sim_imsi",
        "ziccid",
        "iccid",
        "msisdn",
        "cr_version",
        "hw_version",
        "network_type",
        "sub_network_type",
        "rssi",
        "rscp",
        "lte_rsrp",
        "nv_rsrq",
        "nv_sinr",
        "ppp_status",
        "wan_ipaddr",
        "lan_ipaddr",
        "cell_id",
        "lte_band",
    )

    /** Later non-blank values win; blank overlays do not erase an earlier value. */
    fun mergePreferNonBlank(vararg maps: Map<String, String>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (map in maps) {
            for ((key, value) in map) {
                val prev = out[key]
                if (prev.isNullOrBlank() || value.trim().isNotEmpty()) {
                    out[key] = value
                }
            }
        }
        return out
    }
}
