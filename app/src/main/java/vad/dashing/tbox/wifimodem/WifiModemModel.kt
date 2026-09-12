package vad.dashing.tbox.wifimodem

/**
 * User-selected Wi‑Fi modem / MiFi model. Each value maps to a concrete HTTP driver.
 */
enum class WifiModemModel(
    /** Stable id stored in DataStore. */
    val storageId: String,
    /** Short label for settings UI. */
    val displayName: String,
    /** Typical factory LAN IP (user may override). */
    val defaultHost: String,
) {
    OLAX_F95(
        storageId = "olax_f95",
        displayName = "Olax F95",
        defaultHost = "192.168.0.1",
    );

    companion object {
        fun fromStorage(raw: String?): WifiModemModel {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull {
                it.storageId == key || it.name.equals(key, ignoreCase = true)
            } ?: OLAX_F95
        }
    }
}
