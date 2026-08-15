package vad.dashing.tbox.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstantDrAutoCalibPolicyTest {

    @Test
    fun gyroHeadingCollectsYawNotSteer() {
        val h = MockHeadingSource.GYRO
        assertFalse(ConstantDrAutoCalibPolicy.shouldCalibrateSteer(h))
        assertTrue(ConstantDrAutoCalibPolicy.driveRequiresGyro(h))
        assertTrue(ConstantDrAutoCalibPolicy.driveRequiresYaw(h))
        assertFalse(ConstantDrAutoCalibPolicy.markSuccessOnSteerFinish(h))
        assertFalse(ConstantDrAutoCalibPolicy.keepSteerAfterNeedCleared(h))
    }

    @Test
    fun steerHeadingCollectsSteerAndSpeedWithoutGyroGate() {
        val h = MockHeadingSource.STEER
        assertTrue(ConstantDrAutoCalibPolicy.shouldCalibrateSteer(h))
        assertFalse(ConstantDrAutoCalibPolicy.driveRequiresGyro(h))
        assertFalse(ConstantDrAutoCalibPolicy.driveRequiresYaw(h))
        assertTrue(ConstantDrAutoCalibPolicy.markSuccessOnSteerFinish(h))
        assertTrue(ConstantDrAutoCalibPolicy.keepSteerAfterNeedCleared(h))
    }

    @Test
    fun gyroSteerCollectsBothAndClearsNeedOnlyOnDrive() {
        val h = MockHeadingSource.GYRO_STEER
        assertTrue(ConstantDrAutoCalibPolicy.shouldCalibrateSteer(h))
        assertTrue(ConstantDrAutoCalibPolicy.driveRequiresGyro(h))
        assertTrue(ConstantDrAutoCalibPolicy.driveRequiresYaw(h))
        assertFalse(ConstantDrAutoCalibPolicy.markSuccessOnSteerFinish(h))
        assertTrue(ConstantDrAutoCalibPolicy.keepSteerAfterNeedCleared(h))
    }
}
