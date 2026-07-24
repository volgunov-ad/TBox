package vad.dashing.tbox.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.esp.LocationSource

/**
 * Periodically pushes the latest [TboxRepository.locValues] into the Android mock
 * location provider when mock is enabled and the source is not [LocationSource.ANDROID].
 */
class MockLocationJob(
    private val scope: CoroutineScope,
    private val locationMockManager: LocationMockManager,
    private val mockLocation: StateFlow<Boolean>,
    private val locationSource: StateFlow<LocationSource>,
    private val periodMs: StateFlow<Long>,
) {
    companion object {
        fun shouldPushMock(mockEnabled: Boolean, source: LocationSource): Boolean =
            mockEnabled && source != LocationSource.ANDROID
    }

    private var job: Job? = null
    private var collectJob: Job? = null
    private var lastSig: String? = null

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
        locationMockManager.stopMockLocation()
    }

    private fun restartInner() {
        val enabled = shouldPushMock(mockLocation.value, locationSource.value)
        val period = periodMs.value.coerceAtLeast(200L)
        val sig = "$enabled:$period:${locationSource.value}"

        // When disabled, sig equality alone is enough — do not call stop every poll.
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
                val loc = TboxRepository.locValues.value
                if (loc.locateStatus) {
                    locationMockManager.setMockLocation(loc)
                }
                delay(period)
            }
        }
    }
}
