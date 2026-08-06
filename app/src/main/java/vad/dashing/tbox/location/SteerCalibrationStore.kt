package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Steering-wheel calibration for mock DR heading: center zero and L/R scale + sign.
 *
 * Applied as: centered = raw − zeroDeg;
 * Δheading_nav ≈ −sign · scaleFor(Δcentered) · Δcentered
 * (same nav convention as [YawIntegrator]: left+ decreases bearing when sign = +1).
 */
data class SteerCalibrationOffsets(
    /** Steering angle (°) when wheels are straight. */
    val zeroDeg: Float = 0f,
    /** Magnitude scale for left (positive centered Δ) turns. */
    val scaleLeft: Float = 1f,
    /** Magnitude scale for right (negative centered Δ) turns. */
    val scaleRight: Float = 1f,
    /** +1 keeps left+/right−; −1 flips. */
    val sign: Int = 1,
    val calibratedAtEpochMs: Long = 0L,
    val scaleEstimated: Boolean = false,
) {
    val scale: Float
        get() {
            val l = scaleLeft.takeIf { it.isFinite() && it > 0f } ?: 1f
            val r = scaleRight.takeIf { it.isFinite() && it > 0f } ?: 1f
            return (l + r) * 0.5f
        }

    val isDefault: Boolean
        get() = zeroDeg == 0f &&
            scaleLeft == 1f &&
            scaleRight == 1f &&
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
     * L/R scale is chosen from the sign of [centeredDeltaDeg] before [sign].
     */
    fun applyDeltaToBearingDelta(centeredDeltaDeg: Float): Float {
        val k = scaleForCenteredDelta(centeredDeltaDeg, offsets)
        val s = if (offsets.sign < 0) -1 else 1
        val scale = if (k.isFinite() && k > 0f) k else 1f
        // Nav: left+ (positive centered Δ with sign=+1) decreases bearing.
        return -s * scale * centeredDeltaDeg
    }

    fun scaleForCenteredDelta(
        centeredDeltaDeg: Float,
        off: SteerCalibrationOffsets = offsets,
    ): Float {
        val k = if (centeredDeltaDeg >= 0f) off.scaleLeft else off.scaleRight
        return if (k.isFinite() && k > 0f) k else 1f
    }

    fun leftRightAsymmetry(off: SteerCalibrationOffsets = offsets): Float {
        val l = off.scaleLeft.takeIf { it.isFinite() && it > 0f } ?: 1f
        val r = off.scaleRight.takeIf { it.isFinite() && it > 0f } ?: 1f
        if (r < 1e-6f) return 0f
        return abs(l / r - 1f)
    }
}
