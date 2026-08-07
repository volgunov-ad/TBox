package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Steering-wheel calibration for mock DR heading: center zero and a **single**
 * scale + sign (symmetric left/right — unlike gyro dual L/R).
 *
 * Kinematic model (bicycle):
 * centered = raw − zeroDeg;
 * δ_road = scale · centered;
 * Δheading_nav ≈ −sign · (v / L) · tan(δ_road) · dt
 *
 * [scale] is wheel→road ratio (≪ 1), not Δheading/Δsteer. Road calibration
 * estimates it vs GNSS; [SteerHeadingIntegrator.WHEELBASE_M] is fixed for Dashing.
 */
data class SteerCalibrationOffsets(
    /** Steering angle (°) when wheels are straight. */
    val zeroDeg: Float = 0f,
    /**
     * Wheel→road scale (steering ratio inverse), same both sides.
     * Default ~1/15.
     */
    val scale: Float = SteerHeadingIntegrator.DEFAULT_SCALE,
    /** +1 keeps left+/right−; −1 flips. */
    val sign: Int = 1,
    val calibratedAtEpochMs: Long = 0L,
    val scaleEstimated: Boolean = false,
) {
    val isDefault: Boolean
        get() = zeroDeg == 0f &&
            scale == SteerHeadingIntegrator.DEFAULT_SCALE &&
            sign == 1 &&
            calibratedAtEpochMs == 0L

    companion object {
        val DEFAULT = SteerCalibrationOffsets()
    }
}

object SteerCalibrationStore {
    private val _offsets = MutableStateFlow(SteerCalibrationOffsets.DEFAULT)
    val offsetsFlow: StateFlow<SteerCalibrationOffsets> = _offsets.asStateFlow()

    val offsets: SteerCalibrationOffsets
        get() = _offsets.value

    fun update(offsets: SteerCalibrationOffsets) {
        _offsets.value = offsets
    }

    fun reset() {
        _offsets.value = SteerCalibrationOffsets.DEFAULT
    }

    /** Centered steering angle (raw − zero). Null if [raw] is null/non-finite. */
    fun applyZero(rawDeg: Float?): Float? {
        val v = rawDeg ?: return null
        if (!v.isFinite()) return null
        return v - offsets.zeroDeg
    }

    /**
     * Nav bearing delta (°) for held [centeredWheelDeg] over [dtSec] at [speedMps]
     * (signed; negative = reverse).
     */
    fun yawDeltaDeg(centeredWheelDeg: Float, speedMps: Float, dtSec: Double): Float {
        return SteerHeadingIntegrator.yawDeltaDeg(
            centeredWheelDeg = centeredWheelDeg,
            speedMps = speedMps,
            dtSec = dtSec,
            scale = offsets.scale,
            sign = offsets.sign,
        )
    }
}
