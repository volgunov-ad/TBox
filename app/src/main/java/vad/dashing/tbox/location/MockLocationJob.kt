package vad.dashing.tbox.location

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.esp.LocationSource

/**
 * Periodically pushes the latest [TboxRepository.locValues] into the Android mock
 * location provider when mock is enabled and the source is not [LocationSource.ANDROID].
 *
 * After a fix is lost, the last valid point is retained for [FIX_RETENTION_MS] so HU apps
 * keep receiving updates; optional CAN speed can replace GNSS speed always or only then.
 */
class MockLocationJob(
    private val scope: CoroutineScope,
    private val locationMockManager: LocationMockManager,
    private val mockLocation: StateFlow<Boolean>,
    private val locationSource: StateFlow<LocationSource>,
    private val periodMs: StateFlow<Long>,
    private val canSpeedMode: StateFlow<MockCanSpeedMode>,
) {
    companion object {
        /** Keep last valid coordinates in mock after fix loss. */
        const val FIX_RETENTION_MS = 120_000L

        fun shouldPushMock(mockEnabled: Boolean, source: LocationSource): Boolean =
            mockEnabled && source != LocationSource.ANDROID

        fun hasValidCoordinates(loc: LocValues): Boolean =
            loc.latitude != 0.0 || loc.longitude != 0.0
    }

    private var job: Job? = null
    private var collectJob: Job? = null
    private var lastSig: String? = null
    private var lastGoodLoc: LocValues? = null
    private var lastGoodAtElapsedMs: Long = 0L

    fun start() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            while (isActive) {
                restartInner()
                delay(500)
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        job?.cancel()
        job = null
        lastSig = null
        lastGoodLoc = null
        lastGoodAtElapsedMs = 0L
        locationMockManager.stopMockLocation()
    }

    private fun restartInner() {
        val enabled = shouldPushMock(mockLocation.value, locationSource.value)
        val period = periodMs.value.coerceAtLeast(200L)
        val mode = canSpeedMode.value
        val sig = "$enabled:$period:${locationSource.value}:$mode"

        if (sig == lastSig) {
            if (!enabled) return
            if (job?.isActive == true) return
        }

        lastSig = sig
        job?.cancel()
        job = null
        if (!enabled) {
            locationMockManager.stopMockLocation()
            return
        }
        job = scope.launch {
            while (isActive) {
                pushOnce(mode)
                delay(period)
            }
        }
    }

    private fun pushOnce(mode: MockCanSpeedMode) {
        val now = SystemClock.elapsedRealtime()
        val live = TboxRepository.locValues.value
        val retaining: Boolean
        val base: LocValues

        if (live.locateStatus && hasValidCoordinates(live)) {
            lastGoodLoc = live
            lastGoodAtElapsedMs = now
            base = live
            retaining = false
        } else {
            val good = lastGoodLoc
            if (good != null &&
                hasValidCoordinates(good) &&
                now - lastGoodAtElapsedMs <= FIX_RETENTION_MS
            ) {
                base = good
                retaining = true
            } else {
                return
            }
        }

        val canKmh = TripTelemetryRepository.accountingCarSpeed(now)
        val useCan = when (mode) {
            MockCanSpeedMode.ALWAYS -> canKmh != null
            MockCanSpeedMode.WHEN_FIX_LOST -> retaining && canKmh != null
            MockCanSpeedMode.NONE -> false
        }
        val speedKmh = when {
            useCan -> canKmh!!
            retaining -> 0f
            else -> base.speed
        }
        val out = base.copy(
            speed = speedKmh,
            locateStatus = true,
        )
        locationMockManager.setMockLocation(
            locValues = out,
            retainingFix = retaining,
            hasReliableSpeed = useCan || (!retaining && base.speed > 0f),
            hasReliableBearing = !retaining && base.trueDirection != 0f,
        )
    }
}
