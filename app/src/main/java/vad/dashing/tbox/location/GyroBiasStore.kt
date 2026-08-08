package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Static sensor bias: gyro rates (deg/s) and accelerometer (same units as DR snapshot).
 * Applied as raw minus bias before UI / DR logic.
 */
data class GyroBiasOffsets(
    val yawDegPerSec: Float = 0f,
    val pitchDegPerSec: Float = 0f,
    val rollDegPerSec: Float = 0f,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    /**
     * Gyro temperature (°C) when [yawDegPerSec] was last set (idle zero / online / UI).
     * Null when unknown (fallback SensorManager / never saved).
     */
    val yawCalibTempC: Float? = null,
) {
    companion object {
        val ZERO = GyroBiasOffsets()
        const val BIAS_RATE_EDIT_MIN = -5f
        const val BIAS_RATE_EDIT_MAX = 5f
        const val ACCEL_EDIT_MIN = -5f
        const val ACCEL_EDIT_MAX = 5f
    }
}

object GyroBiasStore {
    private val _offsets = MutableStateFlow(GyroBiasOffsets.ZERO)
    val offsetsFlow: StateFlow<GyroBiasOffsets> = _offsets.asStateFlow()

    val offsets: GyroBiasOffsets
        get() = _offsets.value

    fun update(offsets: GyroBiasOffsets) {
        _offsets.value = offsets
    }

    fun applyYaw(raw: Float?): Float? {
        val v = raw ?: return null
        return v - offsets.yawDegPerSec
    }

    fun applyPitch(raw: Float?): Float? {
        val v = raw ?: return null
        return v - offsets.pitchDegPerSec
    }

    fun applyRoll(raw: Float?): Float? {
        val v = raw ?: return null
        return v - offsets.rollDegPerSec
    }

    fun applyAccelX(raw: Float?): Float? {
        val v = raw ?: return null
        return v - offsets.accelX
    }

    fun applyAccelY(raw: Float?): Float? {
        val v = raw ?: return null
        return v - offsets.accelY
    }

    fun applyAccelZ(raw: Float?): Float? {
        val v = raw ?: return null
        return v - offsets.accelZ
    }
}

/**
 * Average samples over a window; reject if peak-to-peak exceeds [maxRange].
 */
object GyroCalibrationMath {
    data class Result(
        val mean: Float,
        val range: Float,
        val accepted: Boolean,
    )

    fun averageWithRangeCheck(samples: List<Float>, maxRange: Float): Result? {
        if (samples.isEmpty()) return null
        val min = samples.minOrNull() ?: return null
        val max = samples.maxOrNull() ?: return null
        val mean = samples.sum() / samples.size
        val range = max - min
        return Result(mean = mean, range = range, accepted = range <= maxRange)
    }

    /** Max peak-to-peak (deg/s) allowed during 3 s static gyro calibration. */
    const val MAX_STATIC_RANGE_DEG_PER_SEC = 1.5f

    /** Max peak-to-peak for accel axes while parked (same units as sensor snapshot). */
    const val MAX_STATIC_RANGE_ACCEL = 0.8f

    const val CALIBRATION_DURATION_MS = 3_000L
}
