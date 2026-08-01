package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.CruiseControlType
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh

/**
 * ACC / conventional CCS enable / setpoint stepping / cancel.
 * Uses a process-scoped job so the step loop survives Compose disposal.
 *
 * ACC: FRM ACCMode/VSetDis (A10 Launcher / FRM path).
 * CCS: MFS enable ? SET? (capture) ? RES+/SET? until vehicle speed ? target (ù1), max 30 s;
 * aborts when Gasped cruise status drops or [abortAdjustLoop]/double-tap cancel.
 * Status tile: [togglePauseEnable] = 210, [fullCancel] = 212.
 */
object AccCruiseController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var adjustJob: Job? = null
    private var adjustGeneration = 0

    private val _isAdjusting = MutableStateFlow(false)
    val isAdjusting: StateFlow<Boolean> = _isAdjusting.asStateFlow()

    fun launchEngageToTarget(
        targetKmh: Int,
        increaseIntervalMs: Int,
        decreaseIntervalMs: Int,
        cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
    ) {
        scope.launch {
            engageToTarget(targetKmh, increaseIntervalMs, decreaseIntervalMs, cruiseControlType)
        }
    }

    fun launchCancelIfEngaged() {
        scope.launch {
            cancelIfEngaged()
        }
    }

    /** Status tile: single tap ù enable / pause via MFS Cruise (210). */
    fun launchTogglePauseEnable() {
        scope.launch {
            togglePauseEnable()
        }
    }

    /** Status tile: double tap ù full off via MFS Cancel (212). */
    fun launchFullCancel() {
        scope.launch {
            fullCancel()
        }
    }

    suspend fun engageToTarget(
        targetKmh: Int,
        increaseIntervalMs: Int,
        decreaseIntervalMs: Int,
        cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
    ): MbCanCommandResult {
        val target = normalizeAccCruiseTargetKmh(targetKmh)
        val increaseMs = normalizeAccCruiseStepIntervalMs(increaseIntervalMs).toLong()
        val decreaseMs = normalizeAccCruiseStepIntervalMs(decreaseIntervalMs).toLong()

        mutex.withLock {
            adjustJob?.cancel()
            adjustGeneration += 1
            val generation = adjustGeneration
            adjustJob = scope.launch {
                try {
                    _isAdjusting.value = true
                    if (AccCruiseDomain.shouldUseAccPath(
                            UniversalCanRepository.accFrmFeedbackAvailable.value,
                            cruiseControlType,
                        )
                    ) {
                        runAccEngageToTarget(generation, target, increaseMs, decreaseMs)
                    } else {
                        runCcsEngageToTarget(generation, target, increaseMs, decreaseMs)
                    }
                } finally {
                    if (generation == adjustGeneration) {
                        _isAdjusting.value = false
                    }
                }
            }
        }
        return MbCanCommandResult(true, "ACC/CCS adjust started target=$target type=$cruiseControlType")
    }

    suspend fun cancelIfEngaged(): MbCanCommandResult {
        abortAdjustLoop()
        val accEngaged = AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)
        val ccsEngaged = AccCruiseDomain.isCcsEngaged(UniversalCanRepository.ccsCruiseStatus.value)
        if (!accEngaged && !ccsEngaged) {
            return MbCanCommandResult(true, "Cruise not engaged")
        }
        return pulseCruiseControl()
    }

    suspend fun togglePauseEnable(): MbCanCommandResult {
        abortAdjustLoop()
        return pulseCruiseControl()
    }

    suspend fun fullCancel(): MbCanCommandResult {
        abortAdjustLoop()
        return pulseCancel()
    }

    fun abortAdjustLoop() {
        adjustGeneration += 1
        adjustJob?.cancel()
        adjustJob = null
        _isAdjusting.value = false
    }

    private suspend fun runAccEngageToTarget(
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

    private suspend fun runCcsEngageToTarget(
        generation: Int,
        target: Int,
        increaseMs: Long,
        decreaseMs: Long,
    ) {
        if (!isCurrentGeneration(generation)) return

        if (AccCruiseDomain.isCcsEngaged(UniversalCanRepository.ccsCruiseStatus.value) &&
            AccCruiseDomain.isVehicleSpeedAtTarget(TripTelemetryRepository.carSpeed.value, target)
        ) {
            return
        }

        if (!AccCruiseDomain.isCcsEngaged(UniversalCanRepository.ccsCruiseStatus.value)) {
            val enableResult = pulseCruiseControl()
            if (!enableResult.success) return
            waitUntil(generation, AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
                AccCruiseDomain.isCcsEngaged(UniversalCanRepository.ccsCruiseStatus.value)
            }
            if (!isCurrentGeneration(generation)) return
        }

        // Capture current speed as CCS setpoint, then step toward widget target.
        pulseSetMinus()
        delay(AccCruiseDomain.CCS_POST_SET_DELAY_MS)
        if (!isCurrentGeneration(generation)) return

        val deadlineElapsed = System.currentTimeMillis() + AccCruiseDomain.CCS_CONVERGE_TIMEOUT_MS
        var bandEnteredAtMs: Long? = null
        while (isCurrentGeneration(generation) && System.currentTimeMillis() < deadlineElapsed) {
            if (ccsAbortedByDriver()) return
            val nowMs = System.currentTimeMillis()
            val inBand = AccCruiseDomain.isVehicleSpeedAtTarget(
                TripTelemetryRepository.carSpeed.value,
                target,
            )
            bandEnteredAtMs = AccCruiseDomain.nextCcsSettleBandEnteredAtMs(
                inBand = inBand,
                nowMs = nowMs,
                bandEnteredAtMs = bandEnteredAtMs,
            )
            if (AccCruiseDomain.isCcsSpeedSettled(bandEnteredAtMs, nowMs)) {
                return
            }
            if (inBand) {
                // Hold: wait for dwell; do not pulse while already inside the band.
                delay(AccCruiseDomain.STATE_POLL_MS)
                continue
            }
            val speed = TripTelemetryRepository.carSpeed.value
            if (speed == null || !speed.isFinite()) {
                delay(AccCruiseDomain.STATE_POLL_MS)
                continue
            }
            if (speed < target) {
                pulseResPlus()
                delay(increaseMs)
            } else {
                pulseSetMinus()
                delay(decreaseMs)
            }
            if (ccsAbortedByDriver()) return
        }
    }

    /**
     * Abort when Gasped reports cruise no longer holding (Cancel / brake).
     * If status is still unknown (null), do not abort on this signal alone.
     */
    private fun ccsAbortedByDriver(): Boolean {
        val status = UniversalCanRepository.ccsCruiseStatus.value ?: return false
        return !AccCruiseDomain.isCcsEngaged(status)
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

    private suspend fun pulseCancel(): MbCanCommandResult =
        UniversalCanRepository.execute(
            MbCanCommand.SetProperty(
                MbCanKnownVehiclePropertyId.MFS_CANCEL,
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
