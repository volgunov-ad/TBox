package vad.dashing.tbox.wifimodem

/**
 * Preferred goform multi-read `cmd=` lists for ZTE MF79U status polls.
 * Field names taken from the real device HAR (home + device-info batches).
 */
object ZteGoformStatusCmds {

    /** Home-screen style poll (signal, PPP, SIM, operator). */
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
        "rssi",
        "lte_rsrp",
        "lte_rsrq",
        "lte_snr",
        "wan_ipaddr",
        "lan_ipaddr",
        "battery_vol_percent",
        "battery_charging",
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
}
