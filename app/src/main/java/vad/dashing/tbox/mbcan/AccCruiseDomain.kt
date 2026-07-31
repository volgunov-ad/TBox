package vad.dashing.tbox.mbcan

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_DEFAULT
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh

/**
 * ACC FRM / conventional CCS / MFS helpers shared by mbCAN and Android 10 VHAL.
 *
 * Engaged ACC modes follow A10 Launcher ({3,4,5}); standby SET path uses modes {2,6}.
 * Conventional CCS uses Gasped [nCruiseControlStatus] ? {1,2} (stock AIService CCS mode).
 * mbCAN [VSetDis] is already km/h; VHAL raw uses [decodeVhalVSetDisKmh].
 * CCS converge uses vehicle speed (˜ [CCS_SPEED_TOLERANCE_KMH]), not TBox cruiseSetSpeed.
 */
object AccCruiseDomain {
    const val MFS_PULSE_VALUE = 1

    val ENGAGED_ACC_MODES: Set<Int> = setOf(3, 4, 5)
    val STANDBY_SET_ACC_MODES: Set<Int> = setOf(2, 6)

    /** Stock AIService: Gasped cruise status 1/2 ? enter CCS key mode. */
    val CCS_ENGAGED_STATUSES: Set<Int> = setOf(1, 2)

    const val DEFAULT_TARGET_KMH = ACC_CRUISE_TARGET_KMH_DEFAULT
    const val DEFAULT_STEP_INTERVAL_MS = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT

    /** Wait for ACC to become engaged after enable / SET pulse. */
    const val ENGAGE_TIMEOUT_MS = 8_000L

    /** CCS: max time to converge vehicle speed to widget target after SET?. */
    const val CCS_CONVERGE_TIMEOUT_MS = 30_000L

    /** CCS: treat vehicle speed as matching widget target within this band (km/h). */
    const val CCS_SPEED_TOLERANCE_KMH = 1

    /** Poll interval while waiting for ACCMode / VSetDis / Gasped / speed updates. */
    const val STATE_POLL_MS = 50L

    /** Brief pause after CCS SET? before reading speed / stepping. */
    const val CCS_POST_SET_DELAY_MS = 300L

    fun isEngaged(accMode: Int?): Boolean =
        accMode != null && accMode in ENGAGED_ACC_MODES

    fun isStandbyReadyForSet(accMode: Int?): Boolean =
        accMode != null && accMode in STANDBY_SET_ACC_MODES

    fun isCcsEngaged(cruiseControlStatus: Int?): Boolean =
        cruiseControlStatus != null && cruiseControlStatus in CCS_ENGAGED_STATUSES

    /**
     * Prefer ACC FRM control when FRM feedback has been observed on this session
     * (including ACCMode=0 = ACC present but off). Otherwise use conventional CCS.
     */
    fun shouldUseAccPath(frmFeedbackAvailable: Boolean): Boolean = frmFeedbackAvailable

    fun isActiveAtTarget(accMode: Int?, vSetDisKmh: Int?, targetKmh: Int): Boolean =
        isEngaged(accMode) && vSetDisKmh != null && vSetDisKmh == normalizeAccCruiseTargetKmh(targetKmh)

    fun isCcsActiveAtTarget(
        cruiseControlStatus: Int?,
        vehicleSpeedKmh: Float?,
        targetKmh: Int,
    ): Boolean {
        if (!isCcsEngaged(cruiseControlStatus)) return false
        return isVehicleSpeedAtTarget(vehicleSpeedKmh, targetKmh)
    }

    fun isVehicleSpeedAtTarget(vehicleSpeedKmh: Float?, targetKmh: Int): Boolean {
        val speed = vehicleSpeedKmh ?: return false
        if (!speed.isFinite()) return false
        val target = normalizeAccCruiseTargetKmh(targetKmh)
        return abs(speed.roundToInt() - target) <= CCS_SPEED_TOLERANCE_KMH
    }

    /** mbCAN FRM byte is displayed km/h (unsigned). */
    fun decodeMbCanVSetDisKmh(raw: Int): Int = raw and 0xFF

    /**
     * A10 Launcher: `ceil(raw * 0.5)` for [R_0B00_FRM_3_VSetDis] (9-bit INT32).
     */
    fun decodeVhalVSetDisKmh(raw: Int): Int = ceil(raw * 0.5).toInt().coerceAtLeast(0)

    fun decodeMbCanAccMode(raw: Int): Int = raw and 0xFF

    fun decodeMbCanCruiseControlStatus(raw: Int): Int = raw and 0xFF

    fun clampTargetKmh(value: Int): Int = normalizeAccCruiseTargetKmh(value)

    fun clampStepIntervalMs(value: Int): Int = normalizeAccCruiseStepIntervalMs(value)
}
