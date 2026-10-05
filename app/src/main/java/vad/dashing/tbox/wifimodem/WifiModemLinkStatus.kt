package vad.dashing.tbox.wifimodem

/**
 * Last known HTTP link state for the selected Wi‑Fi modem.
 * Surfaced on the Modem tab when [ModemSource.WIFI_HTTP] is active.
 */
enum class WifiModemLinkStatus {
    /** Poller not running / TBox source selected. */
    IDLE,

    /** Last poll succeeded. */
    OK,

    /** Login rejected (wrong admin password or locked session). */
    AUTH_FAILED,

    /** Host unreachable / HTTP or network failure. */
    UNREACHABLE,

    /** Other parse / protocol error after reachability. */
    ERROR,
}
