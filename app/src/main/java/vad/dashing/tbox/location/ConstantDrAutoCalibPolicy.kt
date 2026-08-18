package vad.dashing.tbox.location

/**
 * Which channels [ConstantDrAutoCalibJob] collects, depending on the heading source.
 *
 * Speed (CAN↔GNSS) is always useful for the CONSTANT shadow path.
 * Gyro yaw L/R is only required when heading uses the gyro.
 * Steer scale is collected in parallel when heading uses the wheel — independently
 * vs GNSS, never fitted to the gyro.
 */
object ConstantDrAutoCalibPolicy {
    fun shouldCalibrateSteer(heading: MockHeadingSource): Boolean = heading.usesSteer

    fun driveRequiresGyro(heading: MockHeadingSource): Boolean = heading.usesGyro

    fun driveRequiresYaw(heading: MockHeadingSource): Boolean = heading.usesGyro

    /**
     * In STEER-only heading the bicycle model is the course channel: finishing
     * a steer profile may clear [GeoCalibrationState.needsCalibration].
     * In GYRO / GYRO_STEER only the drive (speed + yaw) session clears the flag.
     */
    fun markSuccessOnSteerFinish(heading: MockHeadingSource): Boolean =
        heading == MockHeadingSource.STEER

    /** Keep an open steer session after drive saved / the need flag cleared. */
    fun keepSteerAfterNeedCleared(heading: MockHeadingSource): Boolean = heading.usesSteer
}
