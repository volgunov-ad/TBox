package vad.dashing.tbox.wifimodem

/**
 * Gate for TBox auto modem-restart / TBox reboot.
 *
 * When the UI modem source is [ModemSource.WIFI_HTTP], shared net/APN sinks hold
 * external-router data. That must not drive TBox modem restart or CRT reboot.
 */
object ModemConnectionCheck {
    private val CELLULAR_NET_STATUSES = setOf("2G", "3G", "4G")

    /**
     * @return true when cellular looks healthy enough that auto-restart should not act.
     * For [ModemSource.WIFI_HTTP] always true (N/A — skip TBox restart based on Wi‑Fi modem).
     */
    fun isTboxCellularUp(
        modemSource: ModemSource,
        netStatus: String?,
        apnStatus: Boolean,
    ): Boolean {
        if (modemSource == ModemSource.WIFI_HTTP) return true
        return netStatus in CELLULAR_NET_STATUSES && apnStatus
    }
}
