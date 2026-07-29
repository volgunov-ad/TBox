package vad.dashing.tbox.fuellevelcalibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.trip.TripRecord
import vad.dashing.tbox.trip.TripRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FuelLevelStableApplyTest {

    @Before
    fun setUp() {
        TripRepository.resetForUnitTests()
        TripTelemetryRepository.clearFuelLevelsForTest()
        TripTelemetryRepository.resetFreshnessForTest()
        FuelLevelStableApply.resetDwell()
        FuelCalibrationLive.reset()
        FuelCalibrationLive.configure(trackRefuels = false, tankLiters = 50)
    }

    @Test
    fun huOnly_oneRaw_seedAndTick_publishesFilteredAfterDwell() {
        // Raw arrives before trip (HU push); StableApply must ignore until trip is active.
        TripTelemetryRepository.updateFuelLevelPercentage(70u)
        FuelLevelStableApply.onRawFuelPercent(70u, nowElapsedMs = 0L)
        assertNull(TripTelemetryRepository.fuelLevelPercentageFiltered.value)

        TripRepository.startTrip(TripRecord(startTimeEpochMs = 1_000L))
        FuelLevelStableApply.seedFromCurrentRawIfTripActive(nowElapsedMs = 0L)
        assertNull(TripTelemetryRepository.fuelLevelPercentageFiltered.value)

        FuelLevelStableApply.tick(nowElapsedMs = 14_999L)
        assertNull(TripTelemetryRepository.fuelLevelPercentageFiltered.value)

        FuelLevelStableApply.tick(nowElapsedMs = 15_000L)
        assertEquals(70u, TripTelemetryRepository.fuelLevelPercentageFiltered.value)
        assertEquals(35f, TripTelemetryRepository.fuelLevelCalibratedLiters.value!!, 1e-3f)

        // No double-publish / re-apply side effects required; filtered stays 70.
        FuelLevelStableApply.tick(nowElapsedMs = 20_000L)
        assertEquals(70u, TripTelemetryRepository.fuelLevelPercentageFiltered.value)
    }

    @Test
    fun tboxLike_twoSamplesWithoutTick_stillAccepts() {
        TripRepository.startTrip(TripRecord(startTimeEpochMs = 1_000L))
        TripTelemetryRepository.updateFuelLevelPercentage(55u)
        FuelLevelStableApply.onRawFuelPercent(55u, nowElapsedMs = 0L)
        assertNull(TripTelemetryRepository.fuelLevelPercentageFiltered.value)
        FuelLevelStableApply.onRawFuelPercent(55u, nowElapsedMs = 15_000L)
        assertEquals(55u, TripTelemetryRepository.fuelLevelPercentageFiltered.value)
        FuelLevelStableApply.tick(nowElapsedMs = 16_000L)
        assertEquals(55u, TripTelemetryRepository.fuelLevelPercentageFiltered.value)
    }
}
