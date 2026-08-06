package vad.dashing.tbox.location

/**
 * Heading source for mock enhancement DR (not used in Direct / [MockCanSpeedMode.NONE]).
 */
enum class MockHeadingSource {
    /** Gyro yaw via [YawIntegrator] (default). */
    GYRO,

    /** Steering-wheel angle via [SteerHeadingIntegrator]. */
    STEER,
    ;

    companion object {
        fun fromStorage(raw: String?): MockHeadingSource {
            if (raw.isNullOrBlank()) return GYRO
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GYRO
        }
    }
}
