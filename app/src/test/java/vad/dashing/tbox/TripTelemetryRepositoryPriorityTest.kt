package vad.dashing.tbox

import android.os.SystemClock
import org.junit.Assert.assertFalse
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
}
