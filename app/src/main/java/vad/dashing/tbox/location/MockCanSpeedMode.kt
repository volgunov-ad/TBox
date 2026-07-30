package vad.dashing.tbox.location

/**
 * How mock location mixes vehicle speed from [vad.dashing.tbox.TripTelemetryRepository].
 * Modes are mutually exclusive (single stored value).
 */
enum class MockCanSpeedMode {
    /** GNSS / retained speed only. */
    NONE,
    /** Always use CAN speed when usable. */
    ALWAYS,
    /** Use CAN speed only while retaining the last fix after geoposition is lost. */
    WHEN_FIX_LOST;

    companion object {
        fun fromStorage(raw: String?): MockCanSpeedMode {
            return when (raw?.trim()?.uppercase()) {
                "ALWAYS" -> ALWAYS
                "WHEN_FIX_LOST", "HOLDOVER_ONLY" -> WHEN_FIX_LOST
                else -> NONE
            }
        }
    }
}
