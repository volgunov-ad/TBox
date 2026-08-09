package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * Steering-wheel calibration for mock DR heading: center zero, wheel→road scale,
 * sign, and soft deadzone near center (symmetric left/right — unlike gyro dual L/R).
 *
 * Kinematic model (bicycle):
 * centered = raw − zeroDeg;
 * δ_eff = softDeadzone(centered);
 * δ_road = scale · δ_eff;
 * Δheading_nav ≈ −sign · (v / L) · tan(δ_road) · dt
 */
data class SteerCalibrationOffsets(
    /** Steering angle (°) when wheels are straight. */
    val zeroDeg: Float = 0f,
    /**
     * Wheel→road scale (steering ratio inverse), same both sides.
     * Default ~1/16.
     */
    val scale: Float = SteerHeadingIntegrator.DEFAULT_SCALE,
    /** +1 keeps left+/right−; −1 flips. */
    val sign: Int = 1,
    /**
     * Soft deadzone near center (° of wheel). Effective angle =
     * `sign(δ)·max(|δ|−deadzone, 0)`. Default [DEFAULT_DEADZONE_DEG].
     */
    val deadzoneDeg: Float = DEFAULT_DEADZONE_DEG,
    val calibratedAtEpochMs: Long = 0L,
    val scaleEstimated: Boolean = false,
) {
    val isDefault: Boolean
        get() = zeroDeg == 0f &&
            scale == SteerHeadingIntegrator.DEFAULT_SCALE &&
            sign == 1 &&
            deadzoneDeg == DEFAULT_DEADZONE_DEG &&
            calibratedAtEpochMs == 0L

    companion object {
        val DEFAULT = SteerCalibrationOffsets()
        const val DEFAULT_DEADZONE_DEG = 2f
        const val DEADZONE_MIN_DEG = 0f
        const val DEADZONE_MAX_DEG = 15f
        const val SCALE_EDIT_MIN = 0.02f
        const val SCALE_EDIT_MAX = 0.35f
        const val ZERO_EDIT_MIN = -180f
        const val ZERO_EDIT_MAX = 180f
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

    /** Soft deadzone around zero (no hard cliff at the boundary). */
    fun softDeadzone(centeredWheelDeg: Float, deadzoneDeg: Float = offsets.deadzoneDeg): Float {
        if (!centeredWheelDeg.isFinite()) return 0f
        val dz = deadzoneDeg.coerceIn(
            SteerCalibrationOffsets.DEADZONE_MIN_DEG,
            SteerCalibrationOffsets.DEADZONE_MAX_DEG,
        )
        val a = abs(centeredWheelDeg)
        if (a <= dz) return 0f
        return sign(centeredWheelDeg) * (a - dz)
    }

    /**
     * Nav bearing delta (°) for held [centeredWheelDeg] over [dtSec] at [speedMps]
     * (signed; negative = reverse). Applies store deadzone/scale/sign.
     */
    fun yawDeltaDeg(centeredWheelDeg: Float, speedMps: Float, dtSec: Double): Float {
        return SteerHeadingIntegrator.yawDeltaDeg(
            centeredWheelDeg = softDeadzone(centeredWheelDeg),
            speedMps = speedMps,
            dtSec = dtSec,
            scale = offsets.scale,
            sign = offsets.sign,
            applyInternalDeadzone = false,
        )
    }
}
