package vad.dashing.tbox.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WheelPulseCalibration(
    /** Meters per one wheel pulse; 0 = uncalibrated. */
    val metersPerPulse: Float = 0f,
    val confidence: Float = 0f,
    val tripsEnabled: Boolean = false,
)

object WheelPulseCalibrationStore {
    /** Minimum confidence before pulse may drive trip / DR distance. */
    const val CONFIDENCE_USE_THRESHOLD = 0.7f

    private val _calibration = MutableStateFlow(WheelPulseCalibration())
    val calibration: StateFlow<WheelPulseCalibration> = _calibration.asStateFlow()

    fun update(next: WheelPulseCalibration) {
        _calibration.value = next
    }

    /**
     * True only after a persisted-quality calibration (k > 0 and confidence high enough).
     * Until then trips stay on odometer fallback and DR on CAN speed.
     */
    fun isUsableForDistance(): Boolean {
        val c = _calibration.value
        return c.metersPerPulse > 0f &&
            c.confidence >= CONFIDENCE_USE_THRESHOLD &&
            c.metersPerPulse.isFinite()
    }

    /** When true, trip tick owns [WheelPulseOdometer.flushDistanceM]; DR uses CAN speed. */
    fun tripOwnsPulseDistance(): Boolean {
        if (!isUsableForDistance()) return false
        if (!_calibration.value.tripsEnabled) return false
        val rpm = vad.dashing.tbox.TripTelemetryRepository.accountingEngineRpm() ?: 0f
        if (rpm <= 0f) return false
        return vad.dashing.tbox.trip.TripRepository.activeTrip.value != null ||
            vad.dashing.tbox.trip.TripRepository.persistentTrip() != null
    }
}
