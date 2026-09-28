package vad.dashing.tbox.automation

import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_DEFAULT
import vad.dashing.tbox.CruiseControlType
import vad.dashing.tbox.mbcan.AccCruiseController
import vad.dashing.tbox.mbcan.MbCanCommandResult
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh

/**
 * Parses Builtin cruise parameters and dispatches to [AccCruiseController].
 *
 * Mode is always forced ACC or CCS (`stringValue`); Auto/DEFAULT is rejected.
 * Optional step intervals for engage: `acc:150:200` / `ccs:150:200`
 * (`mode:increaseMs:decreaseMs`); bare `acc` / `ccs` uses widget defaults.
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

    fun parseForcedMode(stringValue: String): CruiseControlType? {
        val modePart = stringValue.trim().lowercase().substringBefore(':').substringBefore(',')
        return when (modePart) {
            MODE_ACC -> CruiseControlType.ACC
            MODE_CCS -> CruiseControlType.CCS
            else -> null
        }
    }

    fun parseEngageParams(action: AutomationAction.Builtin): EngageParams? {
        val mode = parseForcedMode(action.stringValue) ?: return null
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
            cruiseControlType = mode,
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
        val mode = parseForcedMode(action.stringValue)
            ?: return AutomationActionResult.failure("Круиз: режим ACC или CCS")
        val result: MbCanCommandResult = when (action.type) {
            AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET -> {
                val params = parseEngageParams(action)
                    ?: return AutomationActionResult.failure("Круиз: режим ACC или CCS")
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
