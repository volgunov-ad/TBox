package vad.dashing.tbox.mbcan

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_DEFAULT
import vad.dashing.tbox.CruiseControlType
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh

/**
 * ACC FRM / conventional CCS / MFS helpers shared by mbCAN and Android 10 VHAL.
 *
 * Engaged ACC modes follow A10 Launcher ({3,4,5}); standby SET path uses modes {2,6}.
 * Conventional CCS uses Gasped / EMS [CruiseControlStatus] in {1,2} (stock AIService CCS mode).
 * mbCAN [VSetDis] is already km/h; VHAL raw uses [decodeVhalVSetDisKmh].
 * CCS converge: vehicle speed (+/- [CCS_SPEED_TOLERANCE_KMH]) in batches of up to
 * [CCS_BATCH_MAX_STEPS] with post-batch waits — not TBox cruiseSetSpeed.
 */
object AccCruiseDomain {
    const val MFS_PULSE_VALUE = 1

    val ENGAGED_ACC_MODES: Set<Int> = setOf(3, 4, 5)
    val STANDBY_SET_ACC_MODES: Set<Int> = setOf(2, 6)
    /** Stock AIService ADAS card: setpoint visible, dark (standby/override) UI. */
    val STANDBY_DISPLAY_ACC_MODES: Set<Int> = setOf(1, 2, 6, 7)

    /** Stock AIService: Gasped cruise status 1/2 -> enter CCS key mode. */
    val CCS_ENGAGED_STATUSES: Set<Int> = setOf(1, 2)

    const val DEFAULT_TARGET_KMH = ACC_CRUISE_TARGET_KMH_DEFAULT
    const val DEFAULT_STEP_INTERVAL_MS = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT

    /** Wait for ACC to become engaged after enable / SET pulse. */
    const val ENGAGE_TIMEOUT_MS = 8_000L

    /** CCS: max time to converge vehicle speed to widget target after SET-. */
    const val CCS_CONVERGE_TIMEOUT_MS = 30_000L

    /** CCS: treat vehicle speed as matching widget target within this band (km/h). */
    const val CCS_SPEED_TOLERANCE_KMH = 1

    /** CCS: max ±1 km/h pulses per batch (actual steps = min of this and |delta|). */
    const val CCS_BATCH_MAX_STEPS = 5

    /** CCS: when already in-band at measure, wait this long then recheck. */
    const val CCS_AT_TARGET_VERIFY_MS = 2_000L

    /** CCS: wait after a pulse batch (and for follow-up patience / verify). */
    const val CCS_POST_BATCH_WAIT_MS = 1_000L

    /** Poll interval while waiting for ACCMode / VSetDis / Gasped / speed updates. */
    const val STATE_POLL_MS = 50L

    /** Brief pause after CCS SET- before reading speed / stepping. */
    const val CCS_POST_SET_DELAY_MS = 300L

    fun isEngaged(accMode: Int?): Boolean =
        accMode != null && accMode in ENGAGED_ACC_MODES

    fun isStandbyReadyForSet(accMode: Int?): Boolean =
        accMode != null && accMode in STANDBY_SET_ACC_MODES

    fun isStandbyDisplay(accMode: Int?): Boolean =
        accMode != null && accMode in STANDBY_DISPLAY_ACC_MODES

    /** ACC off or fault - no live setpoint UI. */
    fun isAccFullyOff(accMode: Int?): Boolean =
        accMode == null || accMode == 0 || accMode == 9

    fun isCcsEngaged(cruiseControlStatus: Int?): Boolean =
        cruiseControlStatus != null && cruiseControlStatus in CCS_ENGAGED_STATUSES

    /** Show live VSetDis on status tile (engaged or standby display). */
    fun shouldShowAccSetpoint(accMode: Int?): Boolean =
        isEngaged(accMode) || isStandbyDisplay(accMode)

    /**
     * Prefer ACC FRM control when [type] is [CruiseControlType.ACC], or [CruiseControlType.AUTO]
     * and FRM feedback has been observed (including ACCMode=0). [CruiseControlType.CCS] always
     * uses conventional CCS.
     */
    fun shouldUseAccPath(
        frmFeedbackAvailable: Boolean,
        type: CruiseControlType = CruiseControlType.AUTO,
    ): Boolean = when (type) {
        CruiseControlType.AUTO -> frmFeedbackAvailable
        CruiseControlType.ACC -> true
        CruiseControlType.CCS -> false
    }

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

    /**
     * Signed km/h delta from rounded vehicle speed to widget target.
     * Positive = need to increase cruise setpoint; null if speed unknown.
     */
    fun ccsStepDelta(vehicleSpeedKmh: Float?, targetKmh: Int): Int? {
        val speed = vehicleSpeedKmh ?: return null
        if (!speed.isFinite()) return null
        val target = normalizeAccCruiseTargetKmh(targetKmh)
        return target - speed.roundToInt()
    }

    /** Number of ±1 pulses in the next batch: min([CCS_BATCH_MAX_STEPS], |delta|), 0 if in band. */
    fun ccsBatchSteps(delta: Int): Int {
        val absDelta = abs(delta)
        if (absDelta <= CCS_SPEED_TOLERANCE_KMH) return 0
        return minOf(CCS_BATCH_MAX_STEPS, absDelta)
    }

    /**
     * True when speed has crossed past the target band in the step direction.
     * [increasing] true = RES+ direction; false = SET− direction.
     */
    fun ccsOvershot(vehicleSpeedKmh: Float?, targetKmh: Int, increasing: Boolean): Boolean {
        val speed = vehicleSpeedKmh ?: return false
        if (!speed.isFinite()) return false
        val target = normalizeAccCruiseTargetKmh(targetKmh)
        val rounded = speed.roundToInt()
        return if (increasing) {
            rounded > target + CCS_SPEED_TOLERANCE_KMH
        } else {
            rounded < target - CCS_SPEED_TOLERANCE_KMH
        }
    }

    /** True when speed did not meaningfully move over a wait window (|end-start| < 1 km/h). */
    fun ccsSpeedUnchanged(startKmh: Float?, endKmh: Float?): Boolean {
        val start = startKmh ?: return false
        val end = endKmh ?: return false
        if (!start.isFinite() || !end.isFinite()) return false
        return abs(end - start) < 1f
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
