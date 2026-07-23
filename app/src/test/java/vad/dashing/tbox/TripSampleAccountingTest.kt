package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the moving/idle/parking deltas from [vad.dashing.tbox.BackgroundService.onTripPeriodicSample]
 * (production code not changed; these tests lock the current contract and the post-update hazard).
 *
 * Production formula (active trip, rpm > 0):
 * ```
 * val speed = CanDataRepository.carSpeed.value ?: 0f
 * val movingDelta = if (speed > 0f) dt else 0L
 * val idleDelta = if (speed > 0f) 0L else dt
 * ```
 * When rpm == 0: moving/idle do not grow; daily gets parking for dt.
 */
class TripSampleAccountingTest {

    data class DriveSampleDelta(
        val movingMs: Long,
        val idleMs: Long,
        val parkingMs: Long,
    )

    /**
     * Exact parity with BackgroundService sample branches for metrics time.
     * [speedKmh] null means CanDataRepository.carSpeed was never set (or cleared) — treated as 0.
     */
    private fun sampleDeltaAsInBackgroundService(
        rpm: Float?,
        speedKmh: Float?,
        dtMs: Long,
    ): DriveSampleDelta {
        val rpmOrZero = rpm ?: 0f
        if (rpmOrZero == 0f) {
            return DriveSampleDelta(movingMs = 0L, idleMs = 0L, parkingMs = dtMs.coerceAtLeast(0L))
        }
        val speed = speedKmh ?: 0f
        val moving = if (speed > 0f) dtMs else 0L
        val idle = if (speed > 0f) 0L else dtMs
        return DriveSampleDelta(movingMs = moving, idleMs = idle, parkingMs = 0L)
    }

    /** Safer contract for a future fix: unknown speed must not be billed as idle. */
    private fun sampleDeltaDesiredWhenSpeedUnknown(
        rpm: Float?,
        speedKmh: Float?,
        dtMs: Long,
    ): DriveSampleDelta {
        val rpmOrZero = rpm ?: 0f
        if (rpmOrZero == 0f) {
            return DriveSampleDelta(movingMs = 0L, idleMs = 0L, parkingMs = dtMs.coerceAtLeast(0L))
        }
        if (speedKmh == null) {
            return DriveSampleDelta(movingMs = 0L, idleMs = 0L, parkingMs = 0L)
        }
        val moving = if (speedKmh > 0f) dtMs else 0L
        val idle = if (speedKmh > 0f) 0L else dtMs
        return DriveSampleDelta(movingMs = moving, idleMs = idle, parkingMs = 0L)
    }

    @Test
    fun engineOn_speedPositive_countsMoving() {
        val d = sampleDeltaAsInBackgroundService(rpm = 800f, speedKmh = 40f, dtMs = 1_000L)
        assertEquals(DriveSampleDelta(1_000L, 0L, 0L), d)
    }

    @Test
    fun engineOn_speedZero_countsIdle() {
        val d = sampleDeltaAsInBackgroundService(rpm = 800f, speedKmh = 0f, dtMs = 1_000L)
        assertEquals(DriveSampleDelta(0L, 1_000L, 0L), d)
    }

    @Test
    fun engineOff_countsParkingNotIdle() {
        val d = sampleDeltaAsInBackgroundService(rpm = 0f, speedKmh = 0f, dtMs = 1_000L)
        assertEquals(DriveSampleDelta(0L, 0L, 1_000L), d)
    }

    @Test
    fun nullRpm_treatedAsEngineOff_parking() {
        val d = sampleDeltaAsInBackgroundService(rpm = null, speedKmh = 50f, dtMs = 1_000L)
        assertEquals(DriveSampleDelta(0L, 0L, 1_000L), d)
    }

    /**
     * Hazard after APK update / service cold start: RPM arrives from CAN, but carSpeed is still null.
     * Current code bills the whole second as idle (same as speed == 0).
     */
    @Test
    fun nullSpeed_withEngineOn_currentlyCountsAsIdle() {
        val d = sampleDeltaAsInBackgroundService(rpm = 900f, speedKmh = null, dtMs = 1_000L)
        assertEquals(
            "BackgroundService uses carSpeed ?: 0f, so missing speed becomes idle",
            DriveSampleDelta(0L, 1_000L, 0L),
            d,
        )
        // Document intended fix without changing production yet:
        assertEquals(
            DriveSampleDelta(0L, 0L, 0L),
            sampleDeltaDesiredWhenSpeedUnknown(rpm = 900f, speedKmh = null, dtMs = 1_000L),
        )
    }

    /**
     * User report: after update, only idle grew while driving; after engine off idle kept growing.
     * That matches: rpm stuck > 0 (stale / no RPM=0 frame) + speed null or 0 for every tick.
     */
    @Test
    fun postUpdateHazard_staleRpmAndMissingSpeed_accumulatesOnlyIdle() {
        var moving = 0L
        var idle = 0L
        var parking = 0L
        // Simulate ~60 s of driving with speed never arriving, then "engine off" with RPM still stale.
        repeat(60) {
            val d = sampleDeltaAsInBackgroundService(rpm = 850f, speedKmh = null, dtMs = 1_000L)
            moving += d.movingMs
            idle += d.idleMs
            parking += d.parkingMs
        }
        // Frames stop; last RPM stays > 0 because CanData is not cleared on TBox disconnect.
        repeat(30) {
            val d = sampleDeltaAsInBackgroundService(rpm = 850f, speedKmh = 0f, dtMs = 1_000L)
            moving += d.movingMs
            idle += d.idleMs
            parking += d.parkingMs
        }
        assertEquals(0L, moving)
        assertEquals(0L, parking)
        assertEquals(90_000L, idle)
        assertTrue(idle > moving)
    }

    @Test
    fun healthyStream_afterEngineOff_idleStopsAndParkingGrows() {
        var moving = 0L
        var idle = 0L
        var parking = 0L
        repeat(10) {
            val d = sampleDeltaAsInBackgroundService(rpm = 850f, speedKmh = 50f, dtMs = 1_000L)
            moving += d.movingMs
            idle += d.idleMs
            parking += d.parkingMs
        }
        repeat(5) {
            val d = sampleDeltaAsInBackgroundService(rpm = 850f, speedKmh = 0f, dtMs = 1_000L)
            moving += d.movingMs
            idle += d.idleMs
            parking += d.parkingMs
        }
        repeat(20) {
            val d = sampleDeltaAsInBackgroundService(rpm = 0f, speedKmh = 0f, dtMs = 1_000L)
            moving += d.movingMs
            idle += d.idleMs
            parking += d.parkingMs
        }
        assertEquals(10_000L, moving)
        assertEquals(5_000L, idle)
        assertEquals(20_000L, parking)
    }

    @Test
    fun firstSampleAfterResume_zeroDt_doesNotAdvanceTimes() {
        // BackgroundService: tripLastSampleElapsedMs == 0 → dt = 0 on first advance after align.
        val d = sampleDeltaAsInBackgroundService(rpm = 800f, speedKmh = null, dtMs = 0L)
        assertEquals(DriveSampleDelta(0L, 0L, 0L), d)
    }
}
