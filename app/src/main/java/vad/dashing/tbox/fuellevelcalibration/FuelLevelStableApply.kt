package vad.dashing.tbox.fuellevelcalibration

import android.os.SystemClock
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.utils.FuelLevelDwellFilter

/**
 * Shared HU + TBox path: dwell-filter raw fuel % and apply calibration while a trip is active.
 * Writes filtered % / liters into [TripTelemetryRepository] only.
 *
 * HU StateFlow may not re-emit an unchanged %; [tick] completes dwell by elapsed time.
 * [seedFromCurrentRawIfTripActive] starts the candidate when a trip begins after raw already arrived.
 */
object FuelLevelStableApply {
    private val dwellFilter = FuelLevelDwellFilter()

    fun onRawFuelPercent(percent: UInt, nowElapsedMs: Long? = null) {
        if (TripRepository.activeTrip.value == null) return
        val accepted = if (nowElapsedMs != null) {
            dwellFilter.onSample(percent, nowElapsedMs)
        } else {
            dwellFilter.onSample(percent)
        } ?: return
        publishAccepted(accepted)
    }

    /**
     * Completes a pending dwell without a second equal sample (HU-only). Call from the 1 s trip tick.
     */
    fun tick(nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        if (TripRepository.activeTrip.value == null) return
        val accepted = dwellFilter.tick(nowElapsedMs) ?: return
        publishAccepted(accepted)
    }

    /**
     * Feed current raw % into the dwell filter when a trip becomes / stays active.
     * Does not skip [FuelLevelDwellFilter] dwell — only starts (or refreshes) the candidate.
     */
    fun seedFromCurrentRawIfTripActive(nowElapsedMs: Long? = null) {
        if (TripRepository.activeTrip.value == null) return
        val raw = TripTelemetryRepository.fuelLevelPercentage.value ?: return
        onRawFuelPercent(raw, nowElapsedMs)
    }

    fun resetDwell() {
        dwellFilter.reset()
    }

    private fun publishAccepted(accepted: UInt) {
        FuelCalibrationLive.applyFromStableFilteredPercent(accepted)
        TripTelemetryRepository.updateFuelLevelPercentageFiltered(accepted)
    }
}
