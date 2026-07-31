package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh

/**
 * ACC enable / setpoint stepping / cancel. Uses a process-scoped job so the step loop
 * survives Compose disposal.
 */
object AccCruiseController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var adjustJob: Job? = null
    private var adjustGeneration = 0

    fun launchEngageToTarget(
        targetKmh: Int,
        increaseIntervalMs: Int,
        decreaseIntervalMs: Int,
    ) {
        scope.launch {
            engageToTarget(targetKmh, increaseIntervalMs, decreaseIntervalMs)
        }
    }

    fun launchCancelIfEngaged() {
        scope.launch {
            cancelIfEngaged()
        }
    }

    suspend fun engageToTarget(
        targetKmh: Int,
        increaseIntervalMs: Int,
        decreaseIntervalMs: Int,
    ): MbCanCommandResult {
        val target = normalizeAccCruiseTargetKmh(targetKmh)
        val increaseMs = normalizeAccCruiseStepIntervalMs(increaseIntervalMs).toLong()
        val decreaseMs = normalizeAccCruiseStepIntervalMs(decreaseIntervalMs).toLong()

        mutex.withLock {
            adjustJob?.cancel()
            adjustGeneration += 1
            val generation = adjustGeneration
            adjustJob = scope.launch {
                runEngageToTarget(generation, target, increaseMs, decreaseMs)
            }
        }
        return MbCanCommandResult(true, "ACC adjust started target=$target")
    }

    suspend fun cancelIfEngaged(): MbCanCommandResult {
        abortAdjustLoop()
        if (!AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)) {
            return MbCanCommandResult(true, "ACC not engaged")
        }
        return pulseCruiseControl()
    }

    fun abortAdjustLoop() {
        adjustGeneration += 1
        adjustJob?.cancel()
        adjustJob = null
    }

    private suspend fun runEngageToTarget(
        generation: Int,
        target: Int,
        increaseMs: Long,
        decreaseMs: Long,
    ) {
        if (!isCurrentGeneration(generation)) return

        if (!AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)) {
            val enableResult = pulseCruiseControl()
            if (!enableResult.success) return
            if (!waitUntil(generation, AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
                    AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value) ||
                        AccCruiseDomain.isStandbyReadyForSet(UniversalCanRepository.accCruiseMode.value)
                }
            ) {
                return
            }
            if (!isCurrentGeneration(generation)) return
            if (AccCruiseDomain.isStandbyReadyForSet(UniversalCanRepository.accCruiseMode.value) &&
                !AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)
            ) {
                pulseSetMinus()
                waitUntil(generation, AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
                    AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)
                }
            } else if (!AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)) {
                waitUntil(generation, AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
                    AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)
                }
            }
        }

        if (!isCurrentGeneration(generation)) return
        if (!AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)) return

        val current = UniversalCanRepository.accCruiseVSetDisKmh.value
        if (current != null && current == target) return

        while (isCurrentGeneration(generation) &&
            AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)
        ) {
            val speed = UniversalCanRepository.accCruiseVSetDisKmh.value ?: break
            if (speed == target) break
            if (speed < target) {
                pulseResPlus()
                delay(increaseMs)
            } else {
                pulseSetMinus()
                delay(decreaseMs)
            }
        }
    }

    private fun isCurrentGeneration(generation: Int): Boolean = generation == adjustGeneration

    private suspend fun waitUntil(generation: Int, timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val ok = withTimeoutOrNull(timeoutMs) {
            while (isCurrentGeneration(generation)) {
                if (predicate()) return@withTimeoutOrNull true
                delay(AccCruiseDomain.STATE_POLL_MS)
            }
            false
        }
        return ok == true
    }

    private suspend fun pulseCruiseControl(): MbCanCommandResult =
        UniversalCanRepository.execute(
            MbCanCommand.SetProperty(
                MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL,
                AccCruiseDomain.MFS_PULSE_VALUE,
            ),
        )

    private suspend fun pulseResPlus(): MbCanCommandResult =
        UniversalCanRepository.execute(
            MbCanCommand.SetProperty(
                MbCanKnownVehiclePropertyId.MFS_RES_PLUS,
                AccCruiseDomain.MFS_PULSE_VALUE,
            ),
        )

    private suspend fun pulseSetMinus(): MbCanCommandResult =
        UniversalCanRepository.execute(
            MbCanCommand.SetProperty(
                MbCanKnownVehiclePropertyId.MFS_SET_MINUS,
                AccCruiseDomain.MFS_PULSE_VALUE,
            ),
        )
}
