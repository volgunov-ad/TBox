package vad.dashing.tbox.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WheelPulseCalibration(
    /** Meters per one wheel pulse; 0 = uncalibrated. */
    val metersPerPulse: Float = 0f,
    val confidence: Float = 0f,
    /** Use pulse between odometer km ticks for trip distance. */
    val tripsEnabled: Boolean = false,
    /** Use pulse as primary Δs for mock DR (fallback CAN speed). */
    val mockDrEnabled: Boolean = false,
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

    /** Trip hybrid distance: integer odo + pulse fraction of current km. */
    fun isTripsPulseEnabled(): Boolean =
        isUsableForDistance() && _calibration.value.tripsEnabled

    /** Mock DR may take [WheelPulseOdometer.flushDrDistanceM]. */
    fun isMockDrPulseEnabled(): Boolean =
        isUsableForDistance() && _calibration.value.mockDrEnabled
}
