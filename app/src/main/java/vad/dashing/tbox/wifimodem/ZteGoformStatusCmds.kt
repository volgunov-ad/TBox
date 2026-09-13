package vad.dashing.tbox.wifimodem

/**
 * Preferred goform multi-read `cmd=` lists for ZTE MF79U status polls.
 * Field names taken from the real device HAR (home + network-info / device batches).
 *
 * Home and device pages populate different keys: realtime thrpt lives on home,
 * RSSI/RSCP/RSRP on the network-info / about batch. Poll them separately and merge.
 */
object ZteGoformStatusCmds {

    /** Home-screen style poll (signal bars, PPP, SIM, operator, realtime thrpt). */
    val HOME: List<String> = listOf(
        "modem_main_state",
        "pin_status",
        "opms_wan_mode",
        "loginfo",
        "signalbar",
        "network_type",
        "network_provider",
        "ppp_status",
        "simcard_roam",
        "wan_ipaddr",
        "lan_ipaddr",
        "battery_vol_percent",
        "battery_charging",
        // Companion counters — some firmwares fill thrpt only when these are requested too.
        "realtime_tx_bytes",
        "realtime_rx_bytes",
        "realtime_time",
        "realtime_tx_thrpt",
        "realtime_rx_thrpt",
    )

    /**
     * Radio / network-info batch (RSSI/RSCP/RSRP). Matches the About / network
     * page cmd set from the MF79U HAR — home multi_data often leaves these blank.
     */
    val RADIO: List<String> = listOf(
        "network_type",
        "signalbar",
        "rssi",
        "rscp",
        "lte_rssi",
        "lte_rsrp",
        "lte_rsrq",
        "lte_snr",
        "Z5g_rsrp",
        "Z5g_snr",
        "cell_id",
        "lte_band",
        "wan_active_band",
        "ppp_status",
        "wan_ipaddr",
    )

    /** Device / About identifiers. */
    val DEVICE: List<String> = listOf(
        "imei",
        "imsi",
        "sim_imsi",
        "iccid",
        "msisdn",
        "hardware_version",
        "web_version",
        "wa_inner_version",
        "cr_version",
        "network_type",
        "rssi",
        "rscp",
        "lte_rssi",
        "lte_rsrp",
        "ppp_status",
        "wan_ipaddr",
        "lan_ipaddr",
        "cell_id",
        "lte_band",
        "wan_active_band",
    )

    fun homeQuery(): String = HOME.joinToString(",")
    fun deviceQuery(): String = DEVICE.joinToString(",")
    fun radioQuery(): String = RADIO.joinToString(",")

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
