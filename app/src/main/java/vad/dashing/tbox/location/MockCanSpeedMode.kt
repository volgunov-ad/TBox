package vad.dashing.tbox.location

/**
 * How mock location enhances the GNSS stream (CAN speed, retention, dead-reckoning).
 * Modes are mutually exclusive (single stored value).
 *
 * [NONE] — push live GNSS as-is (no CAN override, retention, or gyro DR).
 * [ALWAYS] / [WHEN_FIX_LOST] / [CONSTANT] — enable those enhancements (see [MockLocationJob]).
 */
enum class MockCanSpeedMode {
    /** Live GNSS only; no retention / DR / CAN speed. */
    NONE,
    /** Enhance always: CAN speed when live; retention+DR (+ CAN) when fix lost. */
    ALWAYS,
    /** Enhance only while retaining after fix loss (CAN speed, DR); live GNSS otherwise. */
    WHEN_FIX_LOST,
    /**
     * Continuous position calculation: always DR by CAN speed + yaw; every ~5 s snap to
     * trustworthy GNSS; unlimited retention after fix loss; always spoof CAN speed + calculated course.
     */
    CONSTANT;

    /** True when retention / DR / CAN overrides may run. */
    val enhancesMock: Boolean get() = this != NONE

    /** Continuous DR mode (unlimited retention; indicator same as other enhance modes). */
    val isConstantCalc: Boolean get() = this == CONSTANT

    companion object {
        fun fromStorage(raw: String?): MockCanSpeedMode {
            return when (raw?.trim()?.uppercase()) {
                "ALWAYS" -> ALWAYS
                "WHEN_FIX_LOST", "HOLDOVER_ONLY" -> WHEN_FIX_LOST
                "CONSTANT", "CONTINUOUS", "CONSTANT_DR" -> CONSTANT
                else -> NONE
            }
        }
    }
}
