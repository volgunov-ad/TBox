package vad.dashing.tbox.fuellevelcalibration

import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.utils.FuelLevelDwellFilter

/**
 * Shared HU + TBox path: dwell-filter raw fuel % and apply calibration while a trip is active.
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
        FuelCalibrationLive.applyFromStableFilteredPercent(accepted)
        CanDataRepository.updateFuelLevelPercentageFiltered(accepted)
    }

    fun resetDwell() {
        dwellFilter.reset()
    }
}
