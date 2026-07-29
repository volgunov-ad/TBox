package vad.dashing.tbox.esp

/**
 * Active source for geoposition shown in UI / widgets / mock location.
 */
enum class LocationSource {
    TBOX,
    ESP32,
    ANDROID,
    /** Direct USB CDC / UART-bridge GNSS (NMEA), user-selected device. */
    USB;

    companion object {
        fun fromStorage(raw: String?): LocationSource {
            return when (raw?.trim()?.uppercase()) {
                "TBOX" -> TBOX
                "ESP32" -> ESP32
                "ANDROID" -> ANDROID
                "USB" -> USB
                else -> TBOX
            }
        }

        /** Migrate legacy boolean [getLocData]: true → TBOX, false → ANDROID. */
        fun fromLegacyGetLocData(getLocData: Boolean?): LocationSource {
            return if (getLocData == false) ANDROID else TBOX
        }
    }
}
