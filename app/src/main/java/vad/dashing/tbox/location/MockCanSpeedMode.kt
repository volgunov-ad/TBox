package vad.dashing.tbox.location

/**
 * How mock location enhances the GNSS stream (CAN speed, retention, dead-reckoning).
 * Modes are mutually exclusive (single stored value).
 *
 * [NONE] — push live GNSS as-is (no CAN override, retention, or gyro DR).
 * [ALWAYS] / [WHEN_FIX_LOST] — enable those enhancements (see [MockLocationJob]).
 */
enum class MockCanSpeedMode {
    /** Live GNSS only; no retention / DR / CAN speed. */
    NONE,
    /** Enhance always: CAN speed when live; retention+DR (+ CAN) when fix lost. */
    ALWAYS,
    /** Enhance only while retaining after fix loss (CAN speed, DR); live GNSS otherwise. */
    WHEN_FIX_LOST;

    /** True when retention / DR / CAN overrides may run. */
    val enhancesMock: Boolean get() = this != NONE

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
