package vad.dashing.tbox.wifimodem

/**
 * Stable English automation state keys for modem tab / Wi‑Fi modem sinks.
 * UI [NetState] strings stay Russian; automations never match those literals.
 */
object ModemAutomationStates {
    const val LINK_IDLE = "idle"
    const val LINK_OK = "ok"
    const val LINK_AUTH_FAILED = "auth_failed"
    const val LINK_UNREACHABLE = "unreachable"
    const val LINK_ERROR = "error"

    val LINK_OPTIONS = listOf(
        LINK_IDLE,
        LINK_OK,
        LINK_AUTH_FAILED,
        LINK_UNREACHABLE,
        LINK_ERROR,
    )

    const val NET_NONE = "none"
    const val NET_2G = "2g"
    const val NET_3G = "3g"
    const val NET_4G = "4g"

    val NET_TYPE_OPTIONS = listOf(NET_NONE, NET_2G, NET_3G, NET_4G)

    const val SIM_NONE = "none"
    const val SIM_READY = "ready"
    const val SIM_PIN = "pin"
    const val SIM_ERROR = "error"
    const val SIM_UNKNOWN = "unknown"

    val SIM_OPTIONS = listOf(SIM_NONE, SIM_READY, SIM_PIN, SIM_ERROR, SIM_UNKNOWN)

    fun linkStatusKey(status: WifiModemLinkStatus): String =
        when (status) {
            WifiModemLinkStatus.IDLE -> LINK_IDLE
            WifiModemLinkStatus.OK -> LINK_OK
            WifiModemLinkStatus.AUTH_FAILED -> LINK_AUTH_FAILED
            WifiModemLinkStatus.UNREACHABLE -> LINK_UNREACHABLE
            WifiModemLinkStatus.ERROR -> LINK_ERROR
        }

    fun mobileDataKey(apnStatus: Boolean): String =
        if (apnStatus) "on" else "off"

    fun netTypeKey(netStatus: String?): String =
        when (netStatus?.trim()?.uppercase()) {
            "2G" -> NET_2G
            "3G" -> NET_3G
            "4G" -> NET_4G
            else -> NET_NONE
        }

    fun simStatusKey(simStatus: String?): String {
        val raw = simStatus?.trim().orEmpty()
        if (raw.isEmpty() || raw == "-") return SIM_UNKNOWN
        return when {
            raw.contains("нет", ignoreCase = true) -> SIM_NONE
            raw.contains("PIN", ignoreCase = true) ||
                raw.contains("пин", ignoreCase = true) -> SIM_PIN
            raw.contains("ошиб", ignoreCase = true) ||
                raw.contains("error", ignoreCase = true) -> SIM_ERROR
            raw.contains("готов", ignoreCase = true) ||
                raw.contains("ready", ignoreCase = true) -> SIM_READY
            else -> SIM_UNKNOWN
        }
    }
}
