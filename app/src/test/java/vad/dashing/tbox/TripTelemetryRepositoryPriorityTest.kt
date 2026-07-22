package vad.dashing.tbox

import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TripTelemetryRepositoryPriorityTest {
    @Before
    fun setUp() {
        TripTelemetryRepository.stop()
        TripTelemetryRepository.resetFreshnessForTest()
    }

    @Test
    fun tboxAcceptedWhenHuAbsent() {
        val now = SystemClock.elapsedRealtime()
        assertTrue(
            TripTelemetryRepository.acceptTboxHuPriority(TripTelemetryRepository.Signal.Rpm, now)
        )
    }

    @Test
    fun tboxBlockedWhileHuFresh() {
        val now = SystemClock.elapsedRealtime()
        TripTelemetryRepository.noteHuForTest(TripTelemetryRepository.Signal.Fuel, now)
        assertFalse(
            TripTelemetryRepository.acceptTboxHuPriority(
                TripTelemetryRepository.Signal.Fuel,
                now + 1_000L,
            )
        )
    }

    @Test
    fun debugSnapshotShowsHuWhenFresh() {
        val now = SystemClock.elapsedRealtime()
        TripTelemetryRepository.noteHuForTest(TripTelemetryRepository.Signal.Fuel, now)
        TripTelemetryRepository.noteHuForTest(TripTelemetryRepository.Signal.Rpm, now)
        val snap = TripTelemetryRepository.buildAccountingDebugSnapshot(now + 500L)
        assertTrue(snap.contains("Fuel=HU("))
        assertTrue(snap.contains("Rpm=HU("))
        assertTrue(snap.contains("values["))
    }

    @Test
    fun debugSnapshotShowsTboxWhenHuAbsent() {
        val now = SystemClock.elapsedRealtime()
        assertTrue(
            TripTelemetryRepository.acceptTboxHuPriority(TripTelemetryRepository.Signal.Fuel, now)
        )
        val snap = TripTelemetryRepository.buildAccountingDebugSnapshot(now + 100L)
        assertTrue(snap.contains("Fuel=TBox("))
    }

    @Test
    fun a9RejectsHuEngineTempEvenWhenTboxAbsent() {
        TripTelemetryRepository.setA9EngineTempTboxOnlyForTest(true)
        var written = false
        TripTelemetryRepository.tryWriteHuForTest(TripTelemetryRepository.Signal.EngineTemp) {
            written = true
        }
        assertFalse(written)
    }

    @Test
    fun a10AllowsHuEngineTempWhenTboxAbsent() {
        TripTelemetryRepository.setA9EngineTempTboxOnlyForTest(false)
        var written = false
        TripTelemetryRepository.tryWriteHuForTest(TripTelemetryRepository.Signal.EngineTemp) {
            written = true
        }
        assertTrue(written)
    }

    @Test
    fun a10BlocksHuEngineTempWhileTboxFresh() {
        TripTelemetryRepository.setA9EngineTempTboxOnlyForTest(false)
        val now = SystemClock.elapsedRealtime()
        TripTelemetryRepository.noteTboxTempPriority(
            TripTelemetryRepository.Signal.EngineTemp,
            now,
        )
        var written = false
        TripTelemetryRepository.tryWriteHuForTest(TripTelemetryRepository.Signal.EngineTemp) {
            written = true
        }
        assertFalse(written)
    }

    @Test
    fun accountingAccessorsNullWhenStaleButCachedValueRemains() {
        TripTelemetryRepository.applyTboxFuelPercent(55u)
        TripTelemetryRepository.updateFuelLevelPercentageFiltered(55u)
        TripTelemetryRepository.applyTboxOdometer(12_000u)
        TripTelemetryRepository.applyTboxSpeed(40f)
        TripTelemetryRepository.applyTboxRpm(1500f)
        val now = SystemClock.elapsedRealtime()

        assertEquals(55u, TripTelemetryRepository.accountingFuelLevelPercentageFiltered(now))
        assertEquals(12_000u, TripTelemetryRepository.accountingOdometerKm(now))
        assertEquals(40f, TripTelemetryRepository.accountingCarSpeed(now))
        assertEquals(1500f, TripTelemetryRepository.accountingEngineRpm(now))

        val staleAt = now + TripTelemetryRepository.FRESHNESS_MS + 1L
        assertNull(TripTelemetryRepository.accountingFuelLevelPercentageFiltered(staleAt))
        assertNull(TripTelemetryRepository.accountingOdometerKm(staleAt))
        assertNull(TripTelemetryRepository.accountingCarSpeed(staleAt))
        assertNull(TripTelemetryRepository.accountingEngineRpm(staleAt))
        // Cached StateFlows keep last values for UI / disk (CDR untouched by this gate).
        assertEquals(55u, TripTelemetryRepository.fuelLevelPercentageFiltered.value)
        assertEquals(12_000u, TripTelemetryRepository.odometerKm.value)
        assertEquals(40f, TripTelemetryRepository.carSpeed.value)
        assertEquals(1500f, TripTelemetryRepository.engineRpm.value)
    }

    @Test
    fun accountingFuelNullAfterDiskRestoreWithoutLiveFuelSample() {
        TripTelemetryRepository.updateFuelLevelPercentageFiltered(70u)
        TripTelemetryRepository.updateFuelLevelCalibratedLiters(35f)
        val now = SystemClock.elapsedRealtime()
        assertNull(TripTelemetryRepository.accountingFuelLevelPercentageFiltered(now))
        assertNull(TripTelemetryRepository.accountingFuelLevelCalibratedLiters(now))
        assertEquals(70u, TripTelemetryRepository.fuelLevelPercentageFiltered.value)
        assertEquals(35f, TripTelemetryRepository.fuelLevelCalibratedLiters.value)
    }

    @Test
    fun accountingGearboxOilNullWhenTboxTempStale() {
        val t0 = SystemClock.elapsedRealtime()
        TripTelemetryRepository.noteTboxTempPriority(
            TripTelemetryRepository.Signal.GearboxOilTemp,
            t0,
        )
        assertEquals(
            90,
            TripTelemetryRepository.accountingGearboxOilTemperature(90, t0 + 100L),
        )
        assertNull(
            TripTelemetryRepository.accountingGearboxOilTemperature(
                90,
                t0 + TripTelemetryRepository.FRESHNESS_MS + 1L,
            ),
        )
    }
}
