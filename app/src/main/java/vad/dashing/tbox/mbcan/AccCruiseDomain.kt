package vad.dashing.tbox.mbcan

import kotlin.math.ceil
import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_DEFAULT
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh

/**
 * ACC FRM / MFS decode helpers shared by mbCAN and Android 10 VHAL.
 *
 * Engaged modes follow A10 Launcher ({3,4,5}); standby SET path follows TTG ({2,6}).
 * mbCAN [VSetDis] is already km/h; VHAL raw uses [decodeVhalVSetDisKmh].
 */
object AccCruiseDomain {
    const val MFS_PULSE_VALUE = 1

    val ENGAGED_ACC_MODES: Set<Int> = setOf(3, 4, 5)
    val STANDBY_SET_ACC_MODES: Set<Int> = setOf(2, 6)

    const val DEFAULT_TARGET_KMH = ACC_CRUISE_TARGET_KMH_DEFAULT
    const val DEFAULT_STEP_INTERVAL_MS = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT

    /** Wait for ACC to become engaged after enable / SET pulse. */
    const val ENGAGE_TIMEOUT_MS = 8_000L

    /** Poll interval while waiting for ACCMode / VSetDis updates. */
    const val STATE_POLL_MS = 50L

    fun isEngaged(accMode: Int?): Boolean =
        accMode != null && accMode in ENGAGED_ACC_MODES

    fun isStandbyReadyForSet(accMode: Int?): Boolean =
        accMode != null && accMode in STANDBY_SET_ACC_MODES

    fun isActiveAtTarget(accMode: Int?, vSetDisKmh: Int?, targetKmh: Int): Boolean =
        isEngaged(accMode) && vSetDisKmh != null && vSetDisKmh == normalizeAccCruiseTargetKmh(targetKmh)

    /** mbCAN FRM byte is displayed km/h (unsigned). */
    fun decodeMbCanVSetDisKmh(raw: Int): Int = raw and 0xFF

    /**
     * A10 Launcher: `ceil(raw * 0.5)` for [R_0B00_FRM_3_VSetDis] (9-bit INT32).
     */
    fun decodeVhalVSetDisKmh(raw: Int): Int = ceil(raw * 0.5).toInt().coerceAtLeast(0)

    fun decodeMbCanAccMode(raw: Int): Int = raw and 0xFF

    fun clampTargetKmh(value: Int): Int = normalizeAccCruiseTargetKmh(value)

    fun clampStepIntervalMs(value: Int): Int = normalizeAccCruiseStepIntervalMs(value)
}
