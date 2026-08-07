package vad.dashing.tbox.mbcan

/**
 * A10 VHAL steering angle for [FirmwareVehicleJsonMapper.VHAL_STEERING_WHEEL_ANGLE_PROPERTY_ID]
 * (`MCU_REPLY_STEERING_WHEEL_ANGLE`): **° = raw as-is** (finite Number).
 * No steering rate on this MCU property.
 */
object SteeringAngleDomain {
    /** Reject values that cannot be a physical multi-turn wheel angle. */
    private const val MAX_ABS_DEG = 2000f

    fun decodeMcuReplyDeg(raw: Number): Float? {
        val numeric = raw.toFloat()
        if (!numeric.isFinite()) return null
        if (kotlin.math.abs(numeric) > MAX_ABS_DEG) return null
        return numeric
    }
}
