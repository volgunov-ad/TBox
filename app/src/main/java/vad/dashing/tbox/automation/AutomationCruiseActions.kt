package vad.dashing.tbox.automation

import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_DEFAULT
import vad.dashing.tbox.CruiseControlType
import vad.dashing.tbox.GlobalCruiseControlType
import vad.dashing.tbox.VehicleFeatureSettings
import vad.dashing.tbox.mbcan.AccCruiseController
import vad.dashing.tbox.mbcan.MbCanCommandResult
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh

/**
 * Parses Builtin cruise parameters and dispatches to [AccCruiseController].
 *
 * Runtime path always follows the global [VehicleFeatureSettings.cruiseControlType].
 * Stored `stringValue` may still carry a legacy `acc`/`ccs` prefix (codec migration);
 * optional step intervals for engage: `acc:150:200` / `ccs:150:200` /
 * `:150:200` (`mode:increaseMs:decreaseMs`). Bare / empty mode uses the global type.
 */
object AutomationCruiseActions {
    const val MODE_ACC = "acc"
    const val MODE_CCS = "ccs"

    val MODE_OPTIONS: List<String> = listOf(MODE_ACC, MODE_CCS)

    data class EngageParams(
        val cruiseControlType: CruiseControlType,
        val targetKmh: Int,
        val increaseIntervalMs: Int,
        val decreaseIntervalMs: Int,
    )

    /** Legacy decode: returns ACC/CCS when the prefix is present; null when absent/invalid. */
    fun parseForcedMode(stringValue: String): CruiseControlType? {
        val modePart = stringValue.trim().lowercase().substringBefore(':').substringBefore(',')
        return when (modePart) {
            MODE_ACC -> CruiseControlType.ACC
            MODE_CCS -> CruiseControlType.CCS
            else -> null
        }
    }

    /**
     * Runtime cruise path: always the global setting (legacy stringValue mode is ignored).
     */
    fun resolveRuntimeMode(
        global: GlobalCruiseControlType = VehicleFeatureSettings.cruiseControlType,
    ): CruiseControlType = global.toCruiseControlType()

    fun parseEngageParams(
        action: AutomationAction.Builtin,
        global: GlobalCruiseControlType = VehicleFeatureSettings.cruiseControlType,
    ): EngageParams {
        val target = normalizeAccCruiseTargetKmh(
            if (action.intValue == 0) ACC_CRUISE_TARGET_KMH_DEFAULT else action.intValue,
        )
        val parts = action.stringValue.trim().lowercase().split(':', ',')
        val increase = parts.getOrNull(1)?.toIntOrNull()
            ?.let(::normalizeAccCruiseStepIntervalMs)
            ?: ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
        val decrease = parts.getOrNull(2)?.toIntOrNull()
            ?.let(::normalizeAccCruiseStepIntervalMs)
            ?: ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
        return EngageParams(
            cruiseControlType = resolveRuntimeMode(global),
            targetKmh = target,
            increaseIntervalMs = increase,
            decreaseIntervalMs = decrease,
        )
    }

    fun encodeMode(
        type: CruiseControlType,
        increaseIntervalMs: Int? = null,
        decreaseIntervalMs: Int? = null,
    ): String {
        val mode = when (type) {
            CruiseControlType.ACC -> MODE_ACC
            CruiseControlType.CCS -> MODE_CCS
            CruiseControlType.AUTO -> MODE_ACC
        }
        if (increaseIntervalMs == null && decreaseIntervalMs == null) return mode
        val inc = normalizeAccCruiseStepIntervalMs(
            increaseIntervalMs ?: ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT,
        )
        val dec = normalizeAccCruiseStepIntervalMs(
            decreaseIntervalMs ?: ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT,
        )
        return if (
            inc == ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT &&
            dec == ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
        ) {
            mode
        } else {
            "$mode:$inc:$dec"
        }
    }

    fun modeLabel(key: String): String = when (key.trim().lowercase()) {
        MODE_ACC -> "ACC"
        MODE_CCS -> "CCS"
        else -> key
    }

    suspend fun execute(action: AutomationAction.Builtin): AutomationActionResult {
        val mode = resolveRuntimeMode()
        val result: MbCanCommandResult = when (action.type) {
            AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET -> {
                val params = parseEngageParams(action)
                AccCruiseController.engageToTargetAwaiting(
                    targetKmh = params.targetKmh,
                    increaseIntervalMs = params.increaseIntervalMs,
                    decreaseIntervalMs = params.decreaseIntervalMs,
                    cruiseControlType = params.cruiseControlType,
                )
            }
            AutomationBuiltinActionType.CRUISE_PAUSE ->
                AccCruiseController.pauseCruise(mode)
            AutomationBuiltinActionType.CRUISE_FULL_OFF ->
                AccCruiseController.fullOff(mode)
            AutomationBuiltinActionType.CRUISE_RESUME ->
                AccCruiseController.resumeCruise(mode)
            AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED ->
                AccCruiseController.activateCruiseAtCurrentSpeed(mode)
            AutomationBuiltinActionType.CRUISE_NUDGE -> {
                if (action.intValue != 1 && action.intValue != -1) {
                    return AutomationActionResult.failure("Круиз: шаг уставки +1 или −1")
                }
                AccCruiseController.nudgeCruise(action.intValue, mode)
            }
            else ->
                return AutomationActionResult.failure("Не действие круиза")
        }
        return AutomationActionResult(result.success, result.message)
    }
}
