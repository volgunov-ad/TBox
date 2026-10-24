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
     * or stale. Same-sign steer that leads gyro pulls toward the bicycle-model
     * delta (blend + cap), never a sum and never past steer. Wheel angle is ignored
     * only when older than [SteerHeadingIntegrator.MAX_ANGLE_SAMPLE_AGE_MS] (1 s);
     * hybrid → gyro. Standstill does not rotate from a held wheel.
     */
    GYRO_STEER,

    /** Rear-wheel pulse differential via [WheelHeadingIntegrator] ((ΔsRR − ΔsRL) / B). */
    WHEELS,

    /** Rear wheels primary, steering wheel at low speed (<5 km/h) and for slip/turn confirmation. */
    WHEELS_STEER,

    /** Phone gyroscope projected onto gravity vector over direct RFCOMM (`YAW` packets). */
    PHONE_GYRO,

    /** Phone magnetometer absolute heading over direct RFCOMM (`MAG` packets). */
    PHONE_COMPASS,

    /** Phone gyroscope for turns + phone compass for slow absolute heading anchor. */
    PHONE_GYRO_COMPASS,
    ;

    /** Needs CAN steering interest / sample collection as primary/hybrid channel. */
    val usesSteer: Boolean
        get() = this == STEER || this == GYRO_STEER || this == WHEELS_STEER

    /** Uses HU gyro samples. */
    val usesGyro: Boolean
        get() = this == GYRO || this == GYRO_STEER

    /** Uses rear-wheel differential pulses. */
    val usesWheels: Boolean
        get() = this == WHEELS || this == WHEELS_STEER

    /** Uses phone gyroscope (`YAW` packets). */
    val usesPhoneGyro: Boolean
        get() = this == PHONE_GYRO || this == PHONE_GYRO_COMPASS

    /** Uses phone magnetometer (`MAG` packets). */
    val usesPhoneCompass: Boolean
        get() = this == PHONE_COMPASS || this == PHONE_GYRO_COMPASS

    /** Uses any phone heading sensor over RFCOMM. */
    val usesPhone: Boolean
        get() = usesPhoneGyro || usesPhoneCompass

    companion object {
        fun fromStorage(raw: String?): MockHeadingSource {
            if (raw.isNullOrBlank()) return GYRO
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: GYRO
        }
    }
}
