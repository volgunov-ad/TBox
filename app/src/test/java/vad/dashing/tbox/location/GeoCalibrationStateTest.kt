package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeoCalibrationStateTest {

    @Before
    fun reset() {
        GeoCalibrationState.load(needs = false, lastAtEpochMs = 0L)
    }

    @Test
    fun requestAndMarkCalibrated() {
        GeoCalibrationState.requestCalibration()
        assertTrue(GeoCalibrationState.needsCalibration.value)
        GeoCalibrationState.markCalibrated(1_700_000_000_000L)
        assertFalse(GeoCalibrationState.needsCalibration.value)
        assertEquals(1_700_000_000_000L, GeoCalibrationState.lastCalibratedAtEpochMs.value)
    }

    @Test
    fun loadRestoresPersisted() {
        GeoCalibrationState.load(needs = true, lastAtEpochMs = 42L)
        assertTrue(GeoCalibrationState.needsCalibration.value)
        assertEquals(42L, GeoCalibrationState.lastCalibratedAtEpochMs.value)
    }
}
