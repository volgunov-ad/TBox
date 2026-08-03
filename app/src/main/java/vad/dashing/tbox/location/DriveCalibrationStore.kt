package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * On-road calibration: CAN speed scale and gyro yaw scale/sign for mock DR.
 * Applied as: mockCanSpeed = can * speedScale; yaw' = yaw * yawScale * yawSign.
 */
data class DriveCalibrationOffsets(
    val speedScale: Float = 1f,
    val yawScale: Float = 1f,
    val yawSign: Int = 1,
    val lagMs: Long = 0L,
    val calibratedAtEpochMs: Long = 0L,
    /** Last session produced a usable speed estimate (informational after load). */
    val speedEstimated: Boolean = false,
    /** Last session produced a usable yaw estimate (informational after load). */
    val yawEstimated: Boolean = false,
) {
    val isDefault: Boolean
        get() = speedScale == 1f &&
            yawScale == 1f &&
            yawSign == 1 &&
            calibratedAtEpochMs == 0L &&
            lagMs == 0L

    val isReliable: Boolean
        get() = speedEstimated && yawEstimated

    companion object {
        val DEFAULT = DriveCalibrationOffsets()
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
     */
    fun applyYawRate(yawDegPerSec: Float): Float {
        val k = offsets.yawScale
        val sign = if (offsets.yawSign < 0) -1 else 1
        if (!k.isFinite() || k <= 0f) return yawDegPerSec * sign
        return yawDegPerSec * k * sign
    }
}
