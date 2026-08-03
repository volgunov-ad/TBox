package vad.dashing.tbox.location

/**
 * Gyro static bias (deg/s for rates).
 * Applied as raw minus bias before UI / DR.
 */
data class GyroBiasOffsets(
    val yawDegPerSec: Float = 0f,
    val pitchDegPerSec: Float = 0f,
    val rollDegPerSec: Float = 0f,
) {
    companion object {
        val ZERO = GyroBiasOffsets()
    }
}

object GyroBiasStore {
    @Volatile
    var offsets: GyroBiasOffsets = GyroBiasOffsets.ZERO
        private set

    fun update(offsets: GyroBiasOffsets) {
        this.offsets = offsets
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

    /** Max peak-to-peak (deg/s) allowed during 3 s static calibration. */
    const val MAX_STATIC_RANGE_DEG_PER_SEC = 1.5f
    const val CALIBRATION_DURATION_MS = 3_000L
}
