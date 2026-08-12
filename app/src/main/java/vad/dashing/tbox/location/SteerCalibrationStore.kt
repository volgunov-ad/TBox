package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * Four fixed speed knots for the wheel→road scale. Runtime linearly interpolates
 * between 20/40/60/80 km/h and holds the nearest endpoint outside that range.
 *
 * Defaults fitted from the reference GNSS drive:
 * 20→0.072, 40→0.072, 60→0.042, 80→0.033.
 */
data class SteerScaleProfile(
    val at20Kmh: Float = DEFAULT_SCALE_20_KMH,
    val at40Kmh: Float = DEFAULT_SCALE_40_KMH,
    val at60Kmh: Float = DEFAULT_SCALE_60_KMH,
    val at80Kmh: Float = DEFAULT_SCALE_80_KMH,
) {
    fun scaleAt(speedKmh: Float): Float {
        val speed = abs(speedKmh.takeIf { it.isFinite() } ?: 0f)
        val values = floatArrayOf(at20Kmh, at40Kmh, at60Kmh, at80Kmh)
            .map { SteerCalibrationMath.migrateScale(it) }
        if (speed <= SPEED_KNOTS_KMH.first()) return values.first()
        if (speed >= SPEED_KNOTS_KMH.last()) return values.last()
        for (i in 0 until SPEED_KNOTS_KMH.lastIndex) {
            val lowSpeed = SPEED_KNOTS_KMH[i]
            val highSpeed = SPEED_KNOTS_KMH[i + 1]
            if (speed <= highSpeed) {
                val t = (speed - lowSpeed) / (highSpeed - lowSpeed)
                return values[i] + (values[i + 1] - values[i]) * t
            }
        }
        return values.last()
    }

    val values: List<Float>
        get() = listOf(at20Kmh, at40Kmh, at60Kmh, at80Kmh)

    companion object {
        val SPEED_KNOTS_KMH = listOf(20f, 40f, 60f, 80f)
        const val DEFAULT_SCALE_20_KMH = 0.072f
        const val DEFAULT_SCALE_40_KMH = 0.072f
        const val DEFAULT_SCALE_60_KMH = 0.042f
        const val DEFAULT_SCALE_80_KMH = 0.033f
        val DEFAULT = SteerScaleProfile()

        fun uniform(scale: Float): SteerScaleProfile {
            val safe = SteerCalibrationMath.migrateScale(scale)
            return SteerScaleProfile(safe, safe, safe, safe)
        }

        fun defaultAtKnotIndex(index: Int): Float = when (index) {
            0 -> DEFAULT_SCALE_20_KMH
            1 -> DEFAULT_SCALE_40_KMH
            2 -> DEFAULT_SCALE_60_KMH
            else -> DEFAULT_SCALE_80_KMH
        }
    }
}

/**
 * Steering-wheel calibration for mock DR heading: center zero, speed-dependent
 * wheel→road scale, sign, and soft deadzone near center (symmetric left/right —
 * unlike gyro dual L/R).
 *
 * Kinematic model (bicycle):
 * centered = raw − zeroDeg;
 * δ_eff = softDeadzone(centered);
 * δ_road = scale(|v|) · δ_eff;
 * Δheading_nav ≈ −sign · (v / L) · tan(δ_road) · dt
 */
data class SteerCalibrationOffsets(
    /** Steering angle (°) when wheels are straight. */
    val zeroDeg: Float = 0f,
    /** Piecewise-linear wheel→road scale (steering ratio inverse), same both sides. */
    val scaleProfile: SteerScaleProfile = SteerScaleProfile.DEFAULT,
    /** +1 keeps left+/right−; −1 flips. */
    val sign: Int = 1,
    /**
     * Soft deadzone near center (° of wheel). Effective angle =
     * `sign(δ)·max(|δ|−deadzone, 0)`. Default [DEFAULT_DEADZONE_DEG].
     */
    val deadzoneDeg: Float = DEFAULT_DEADZONE_DEG,
    /**
     * Vehicle wheelbase L (m) for bicycle model `ψ̇ = (v/L)·tan(δ_road)`.
     * Default [SteerHeadingIntegrator.DEFAULT_WHEELBASE_M] (Jetour Dashing).
     */
    val wheelbaseM: Float = SteerHeadingIntegrator.DEFAULT_WHEELBASE_M,
    val calibratedAtEpochMs: Long = 0L,
    val scaleEstimated: Boolean = false,
) {
    /** Compatibility/display value at the 40 km/h knot. */
    val scale: Float
        get() = scaleProfile.at40Kmh

    val isDefault: Boolean
        get() = zeroDeg == 0f &&
            scaleProfile == SteerScaleProfile.DEFAULT &&
            sign == 1 &&
            deadzoneDeg == DEFAULT_DEADZONE_DEG &&
            wheelbaseM == SteerHeadingIntegrator.DEFAULT_WHEELBASE_M &&
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
        const val WHEELBASE_EDIT_MIN = 1.5f
        const val WHEELBASE_EDIT_MAX = 4.5f
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
     * (signed; negative = reverse). Applies store deadzone/profile/sign.
     */
    fun yawDeltaDeg(centeredWheelDeg: Float, speedMps: Float, dtSec: Double): Float {
        return SteerHeadingIntegrator.yawDeltaDeg(
            centeredWheelDeg = softDeadzone(centeredWheelDeg),
            speedMps = speedMps,
            dtSec = dtSec,
            scale = offsets.scaleProfile.scaleAt(speedMps * 3.6f),
            sign = offsets.sign,
            applyInternalDeadzone = false,
            wheelbaseM = offsets.wheelbaseM,
        )
    }
}
