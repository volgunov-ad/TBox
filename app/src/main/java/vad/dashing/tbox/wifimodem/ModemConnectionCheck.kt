package vad.dashing.tbox.wifimodem

import vad.dashing.tbox.internet.HuInternetStatus

/**
 * Unified network health for auto modem-restart / device reboot.
 *
 * Channel health (2G/3G/4G + APN) applies to both [ModemSource.TBOX] and
 * [ModemSource.WIFI_HTTP] sinks. When the HU internet probe is enabled,
 * [HuInternetStatus.OFFLINE] alone escalates; [HuInternetStatus.UNKNOWN] and
 * [HuInternetStatus.CHECKING] do not.
 */
object ModemConnectionCheck {
    private val CELLULAR_NET_STATUSES = setOf("2G", "3G", "4G")

    fun isCellularChannelUp(
        netStatus: String?,
        apnStatus: Boolean,
    ): Boolean = netStatus in CELLULAR_NET_STATUSES && apnStatus

    /**
     * @return true when auto-restart should not act.
     */
    fun isNetworkHealthy(
        netStatus: String?,
        apnStatus: Boolean,
        internetProbeEnabled: Boolean,
        huInternetStatus: HuInternetStatus,
    ): Boolean {
        if (!isCellularChannelUp(netStatus, apnStatus)) return false
        if (internetProbeEnabled && huInternetStatus == HuInternetStatus.OFFLINE) return false
        return true
    }
}
