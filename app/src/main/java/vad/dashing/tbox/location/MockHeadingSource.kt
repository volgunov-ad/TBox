package vad.dashing.tbox.location

/**
 * Heading source for mock enhancement DR (not used in Direct / [MockCanSpeedMode.NONE]).
 */
enum class MockHeadingSource {
    /** Gyro yaw via [YawIntegrator] (default). */
    GYRO,

    /** Steering wheel via bicycle model [SteerHeadingIntegrator] (v·tan(δ)/L). */
    STEER,

    /**
     * Gyro primary with steering as turn confirmation / fallback when gyro is quiet
     * or stale. Does not sum both integrals blindly.
     */
    GYRO_STEER,
    ;

    /** Needs CAN steering interest / sample collection. */
    val usesSteer: Boolean
        get() = this == STEER || this == GYRO_STEER

    /** Uses HU gyro samples. */
    val usesGyro: Boolean
        get() = this == GYRO || this == GYRO_STEER

    companion object {
        fun fromStorage(raw: String?): MockHeadingSource {
            if (raw.isNullOrBlank()) return GYRO
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GYRO
        }
    }
}
