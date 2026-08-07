package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * On-road calibration: CAN speed scale and gyro yaw scale/sign for mock DR.
 *
 * Applied as: mockCanSpeed = can * speedScale;
 * yaw' = yaw * scaleFor(yaw) * yawSign, where [yawScaleLeft] / [yawScaleRight]
 * are chosen by the sign of bias-corrected yaw (left +, right −) before [yawSign].
 */
data class DriveCalibrationOffsets(
    val speedScale: Float = 1f,
    /** Magnitude scale for left turns (debiased yaw ≥ 0 before sign). */
    val yawScaleLeft: Float = 1f,
    /** Magnitude scale for right turns (debiased yaw < 0 before sign). */
    val yawScaleRight: Float = 1f,
    val yawSign: Int = 1,
    val lagMs: Long = 0L,
    val calibratedAtEpochMs: Long = 0L,
    /** Last session produced a usable speed estimate (informational after load). */
    val speedEstimated: Boolean = false,
    /** Last session produced a usable yaw estimate (informational after load). */
    val yawEstimated: Boolean = false,
) {
    /** Mean of L/R — UI / legacy / geo-debug single-field view. */
    val yawScale: Float
        get() {
            val l = yawScaleLeft.takeIf { it.isFinite() && it > 0f } ?: 1f
            val r = yawScaleRight.takeIf { it.isFinite() && it > 0f } ?: 1f
            return (l + r) * 0.5f
        }

    val isDefault: Boolean
        get() = speedScale == 1f &&
            yawScaleLeft == 1f &&
            yawScaleRight == 1f &&
            yawSign == 1 &&
            calibratedAtEpochMs == 0L &&
            lagMs == 0L

    val isReliable: Boolean
        get() = speedEstimated && yawEstimated

    companion object {
        val DEFAULT = DriveCalibrationOffsets()

        /**
         * Build offsets from a legacy single [yawScale] (both sides equal).
         */
        fun fromLegacyYawScale(
            speedScale: Float = 1f,
            yawScale: Float = 1f,
            yawSign: Int = 1,
            lagMs: Long = 0L,
            calibratedAtEpochMs: Long = 0L,
            speedEstimated: Boolean = false,
            yawEstimated: Boolean = false,
        ): DriveCalibrationOffsets = DriveCalibrationOffsets(
            speedScale = speedScale,
            yawScaleLeft = yawScale,
            yawScaleRight = yawScale,
            yawSign = yawSign,
            lagMs = lagMs,
            calibratedAtEpochMs = calibratedAtEpochMs,
            speedEstimated = speedEstimated,
            yawEstimated = yawEstimated,
        )
    }
}

object DriveCalibrationStore {
    private val _offsets = MutableStateFlow(DriveCalibrationOffsets.DEFAULT)
    val offsetsFlow: StateFlow<DriveCalibrationOffsets> = _offsets.asStateFlow()

    val offsets: DriveCalibrationOffsets
        get() = _offsets.value

    fun update(offsets: DriveCalibrationOffsets) {
        _offsets.value = offsets
    }

    fun reset() {
        _offsets.value = DriveCalibrationOffsets.DEFAULT
    }

    fun applyCanSpeed(canKmh: Float): Float {
        val k = offsets.speedScale
        if (!k.isFinite() || k <= 0f) return canKmh
        return canKmh * k
    }

    /**
     * Scale and optionally invert bias-corrected yaw (deg/s) before DR integrate.
     * [yawSign] +1 keeps left+/right−; −1 flips.
     * L/R scale is chosen from the sign of [yawDegPerSec] before applying [yawSign].
     */
    fun applyYawRate(yawDegPerSec: Float): Float {
        val k = scaleForDebiasedYaw(yawDegPerSec, offsets)
        val sign = if (offsets.yawSign < 0) -1 else 1
        if (!k.isFinite() || k <= 0f) return yawDegPerSec * sign
        return yawDegPerSec * k * sign
    }

    fun scaleForDebiasedYaw(
        debiasedYawDegPerSec: Float,
        off: DriveCalibrationOffsets = offsets,
    ): Float {
        val k = if (debiasedYawDegPerSec >= 0f) off.yawScaleLeft else off.yawScaleRight
        return if (k.isFinite() && k > 0f) k else 1f
    }

    /** Relative L/R asymmetry |left/right − 1|; 0 = symmetric. */
    fun leftRightAsymmetry(off: DriveCalibrationOffsets = offsets): Float {
        val l = off.yawScaleLeft.takeIf { it.isFinite() && it > 0f } ?: 1f
        val r = off.yawScaleRight.takeIf { it.isFinite() && it > 0f } ?: 1f
        if (r < 1e-6f) return 0f
        return abs(l / r - 1f)
    }
}
