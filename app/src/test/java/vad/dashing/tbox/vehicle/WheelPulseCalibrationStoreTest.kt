package vad.dashing.tbox.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WheelPulseCalibrationStoreTest {

    @Before
    fun reset() {
        WheelPulseCalibrationStore.update(WheelPulseCalibration())
    }

    @Test
    fun featureDefaultsOff_andGatesUsableTripsAndDr() {
        assertFalse(WheelPulseCalibrationStore.isFeatureEnabled())
        WheelPulseCalibrationStore.update(
            WheelPulseCalibration(
                metersPerPulse = 0.025f,
                confidence = 0.95f,
                featureEnabled = false,
                tripsEnabled = true,
                mockDrEnabled = true,
            ),
        )
        assertFalse(WheelPulseCalibrationStore.isUsableForDistance())
        assertFalse(WheelPulseCalibrationStore.isTripsPulseEnabled())
        assertFalse(WheelPulseCalibrationStore.isMockDrPulseEnabled())
    }

    @Test
    fun featureOn_allowsUsableWhenCalibrated() {
        WheelPulseCalibrationStore.update(
            WheelPulseCalibration(
                metersPerPulse = 0.025f,
                confidence = 0.95f,
                featureEnabled = true,
                tripsEnabled = true,
                mockDrEnabled = true,
            ),
        )
        assertTrue(WheelPulseCalibrationStore.isFeatureEnabled())
        assertTrue(WheelPulseCalibrationStore.isUsableForDistance())
        assertTrue(WheelPulseCalibrationStore.isTripsPulseEnabled())
        assertTrue(WheelPulseCalibrationStore.isMockDrPulseEnabled())
    }

    @Test
    fun tripsAndDrRequireTheirOwnFlags() {
        WheelPulseCalibrationStore.update(
            WheelPulseCalibration(
                metersPerPulse = 0.025f,
                confidence = 0.95f,
                featureEnabled = true,
                tripsEnabled = false,
                mockDrEnabled = false,
            ),
        )
        assertTrue(WheelPulseCalibrationStore.isUsableForDistance())
        assertFalse(WheelPulseCalibrationStore.isTripsPulseEnabled())
        assertFalse(WheelPulseCalibrationStore.isMockDrPulseEnabled())
    }
}
