package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Steering-wheel calibration for mock DR heading: center zero and a **single**
 * scale + sign (symmetric left/right — unlike gyro dual L/R).
 *
 * Applied as: centered = raw − zeroDeg;
 * Δheading_nav ≈ −sign · scale · Δcentered
 * (same nav convention as [YawIntegrator]: left+ decreases bearing when sign = +1).
 *
 * Road calibration absorbs steering ratio (wheel ≫ road wheels) into [scale].
 */
data class SteerCalibrationOffsets(
    /** Steering angle (°) when wheels are straight. */
    val zeroDeg: Float = 0f,
    /** Magnitude scale steering-wheel ° → heading ° (same both sides). */
    val scale: Float = 1f,
    /** +1 keeps left+/right−; −1 flips. */
    val sign: Int = 1,
    val calibratedAtEpochMs: Long = 0L,
    val scaleEstimated: Boolean = false,
) {
    val isDefault: Boolean
        get() = zeroDeg == 0f &&
            scale == 1f &&
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
     * Map a centered steering-angle delta (°) to a nav bearing delta (°).
     */
    fun applyDeltaToBearingDelta(centeredDeltaDeg: Float): Float {
        val s = if (offsets.sign < 0) -1 else 1
        val k = offsets.scale.takeIf { it.isFinite() && it > 0f } ?: 1f
        // Nav: left+ (positive centered Δ with sign=+1) decreases bearing.
        return -s * k * centeredDeltaDeg
    }
}
