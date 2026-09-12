package vad.dashing.tbox.wifimodem

/**
 * Active source for cellular / modem state shown in the Modem tab and net widgets.
 * Mirrors [vad.dashing.tbox.esp.LocationSource] for geoposition.
 */
enum class ModemSource {
    /** Built-in TBox modem via MDC UDP (default). */
    TBOX,

    /** External MiFi / 4G router over Wi‑Fi HTTP API (model-specific driver). */
    WIFI_HTTP;

    companion object {
        fun fromStorage(raw: String?): ModemSource {
            return when (raw?.trim()?.uppercase()) {
                "WIFI_HTTP", "WIFI", "HTTP" -> WIFI_HTTP
                else -> TBOX
            }
        }
    }
}
