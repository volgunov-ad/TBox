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
import vad.dashing.tbox.CruiseControlType
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh

/**
 * ACC / conventional CCS enable / setpoint stepping / cancel.
 * Uses a process-scoped job so the step loop survives Compose disposal.
 *
 * Logical states: Off / Standby / Active / Fault (see [AccCruiseDomain.cruiseLogicalState]).
 * Road-proven MFS: **210** = full off from Active; **212** = pause Active?Standby;
 * **214** SET? activates from Standby; ACC/CCS converge loops unchanged.
 */
object AccCruiseController {
    private const val LOG_TAG = "AccCruise"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var adjustJob: Job? = null
    private var adjustGeneration = 0

    private val _isAdjusting = MutableStateFlow(false)
    val isAdjusting: StateFlow<Boolean> = _isAdjusting.asStateFlow()

    private fun debug(message: String) {
        MbCanDiagnostics.log("DEBUG", LOG_TAG, message)
    }

    private fun signalSnapshot(): String =
        "accMode=${UniversalCanRepository.accCruiseMode.value}" +
            " vSet=${UniversalCanRepository.accCruiseVSetDisKmh.value}" +
            " ccs=${UniversalCanRepository.ccsCruiseStatus.value}" +
            " carSpeed=${TripTelemetryRepository.carSpeed.value}" +
            " frm=${UniversalCanRepository.accFrmFeedbackAvailable.value}"

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

    /** Setpoint / status double-tap: full off via 210 when Standby or Active. */
    fun launchFullOff(cruiseControlType: CruiseControlType = CruiseControlType.AUTO) {
        scope.launch {
            fullOff(cruiseControlType)
        }
    }

    /** Status tile single tap: Off/Standby ? activate at current; Active ? pause (212). */
    fun launchStatusSingleTap(cruiseControlType: CruiseControlType = CruiseControlType.AUTO) {
        scope.launch {
            statusSingleTap(cruiseControlType)
        }
    }

    /**
     * Status tile swipe down: Standby ? SET? (activate current); Active ? SET? (?1 km/h).
     */
    fun launchStatusSwipeDown(cruiseControlType: CruiseControlType = CruiseControlType.AUTO) {
        scope.launch {
            statusSwipeDown(cruiseControlType)
        }
    }

    /**
     * Status tile swipe up: Standby ? RES+ (resume); Active ? RES+ (+1 km/h).
     */
    fun launchStatusSwipeUp(cruiseControlType: CruiseControlType = CruiseControlType.AUTO) {
        scope.launch {
            statusSwipeUp(cruiseControlType)
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
        val useAcc = AccCruiseDomain.shouldUseAccPath(
            UniversalCanRepository.accFrmFeedbackAvailable.value,
            cruiseControlType,
        )

        val state = currentLogicalState(cruiseControlType)
        val atTarget = state == CruiseLogicalState.Active && isAtWidgetTarget(useAcc, target)
        debug(
            "setpointTap type=$cruiseControlType useAcc=$useAcc state=$state " +
                "target=$target atTarget=$atTarget ${signalSnapshot()}",
        )
        if (state == CruiseLogicalState.Fault) {
            abortAdjustLoop()
            debug("setpointTap action=ignore_fault")
            return MbCanCommandResult(true, "Cruise fault  tap ignored")
        }

        if (atTarget) {
            abortAdjustLoop()
            debug("setpointTap action=pause_212")
            return pulseCancel()
        }

        val captureSetpoint = state != CruiseLogicalState.Active
        debug("setpointTap action=converge captureSetpoint=$captureSetpoint")

        mutex.withLock {
            adjustJob?.cancel()
            adjustGeneration += 1
            val generation = adjustGeneration
            adjustJob = scope.launch {
                try {
                    _isAdjusting.value = true
                    if (useAcc) {
                        runAccEngageToTarget(generation, target, increaseMs, decreaseMs, captureSetpoint)
                    } else {
                        runCcsEngageToTarget(generation, target, increaseMs, decreaseMs, captureSetpoint)
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

    suspend fun statusSingleTap(
        cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
    ): MbCanCommandResult {
        abortAdjustLoop()
        val state = currentLogicalState(cruiseControlType)
        debug("statusTap type=$cruiseControlType state=$state ${signalSnapshot()}")
        return when (state) {
            CruiseLogicalState.Fault -> {
                debug("statusTap action=ignore_fault")
                MbCanCommandResult(true, "Cruise fault  tap ignored")
            }
            CruiseLogicalState.Active -> {
                debug("statusTap action=pause_212")
                pulseCancel()
            }
            CruiseLogicalState.Off, CruiseLogicalState.Standby -> {
                debug("statusTap action=activate_current")
                activateAtCurrentSpeed(cruiseControlType)
            }
        }
    }

    suspend fun statusSwipeDown(
        cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
    ): MbCanCommandResult {
        abortAdjustLoop()
        val state = currentLogicalState(cruiseControlType)
        debug("statusSwipeDown type=$cruiseControlType state=$state ${signalSnapshot()}")
        return when (state) {
            CruiseLogicalState.Standby -> {
                debug("statusSwipeDown action=activate_set_minus")
                activateAtCurrentSpeed(cruiseControlType)
            }
            CruiseLogicalState.Active -> {
                debug("statusSwipeDown action=nudge_set_minus")
                pulseSetMinus()
            }
            CruiseLogicalState.Off, CruiseLogicalState.Fault -> {
                debug("statusSwipeDown action=noop")
                MbCanCommandResult(true, "Swipe down ignored")
            }
        }
    }

    suspend fun statusSwipeUp(
        cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
    ): MbCanCommandResult {
        abortAdjustLoop()
        val state = currentLogicalState(cruiseControlType)
        debug("statusSwipeUp type=$cruiseControlType state=$state ${signalSnapshot()}")
        return when (state) {
            CruiseLogicalState.Standby -> {
                debug("statusSwipeUp action=resume_res_plus")
                val result = pulseResPlus()
                if (!result.success) {
                    debug("statusSwipeUp res_failed ${result.message}")
                    return result
                }
                val becameActive = waitPredicate(AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
                    currentLogicalState(cruiseControlType) == CruiseLogicalState.Active
                }
                debug("statusSwipeUp done becameActive=$becameActive ${signalSnapshot()}")
                MbCanCommandResult(true, "Cruise RES+ from Standby")
            }
            CruiseLogicalState.Active -> {
                debug("statusSwipeUp action=nudge_res_plus")
                pulseResPlus()
            }
            CruiseLogicalState.Off, CruiseLogicalState.Fault -> {
                debug("statusSwipeUp action=noop")
                MbCanCommandResult(true, "Swipe up ignored")
            }
        }
    }

    suspend fun fullOff(
        cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
    ): MbCanCommandResult {
        abortAdjustLoop()
        val state = currentLogicalState(cruiseControlType)
        debug("fullOff type=$cruiseControlType state=$state ${signalSnapshot()}")
        return when (state) {
            CruiseLogicalState.Standby, CruiseLogicalState.Active -> {
                debug("fullOff action=pulse_210")
                pulseCruiseControl()
            }
            CruiseLogicalState.Off, CruiseLogicalState.Fault -> {
                debug("fullOff action=noop")
                MbCanCommandResult(true, "Cruise already off")
            }
        }
    }

    fun abortAdjustLoop() {
        adjustGeneration += 1
        adjustJob?.cancel()
        adjustJob = null
        _isAdjusting.value = false
    }

    private fun currentLogicalState(cruiseControlType: CruiseControlType): CruiseLogicalState {
        val useAcc = AccCruiseDomain.shouldUseAccPath(
            UniversalCanRepository.accFrmFeedbackAvailable.value,
            cruiseControlType,
        )
        return AccCruiseDomain.cruiseLogicalState(
            useAccPath = useAcc,
            accMode = UniversalCanRepository.accCruiseMode.value,
            ccsStatus = UniversalCanRepository.ccsCruiseStatus.value,
        )
    }

    private fun isAtWidgetTarget(useAcc: Boolean, target: Int): Boolean =
        AccCruiseDomain.isAtWidgetTarget(
            useAccPath = useAcc,
            accMode = UniversalCanRepository.accCruiseMode.value,
            vSetDisKmh = UniversalCanRepository.accCruiseVSetDisKmh.value,
            ccsStatus = UniversalCanRepository.ccsCruiseStatus.value,
            vehicleSpeedKmh = TripTelemetryRepository.carSpeed.value,
            targetKmh = target,
        )

    /** Status tile: 210 (if Off) then SET? to Active at current speed. */
    private suspend fun activateAtCurrentSpeed(
        cruiseControlType: CruiseControlType,
    ): MbCanCommandResult {
        val useAcc = AccCruiseDomain.shouldUseAccPath(
            UniversalCanRepository.accFrmFeedbackAvailable.value,
            cruiseControlType,
        )
        if (currentLogicalState(cruiseControlType) == CruiseLogicalState.Off) {
            debug("activateCurrent pulse_210 useAcc=$useAcc")
            val enableResult = pulseCruiseControl()
            if (!enableResult.success) {
                debug("activateCurrent enable_failed ${enableResult.message}")
                return enableResult
            }
            if (!waitPredicate(AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
                    val s = currentLogicalState(cruiseControlType)
                    s == CruiseLogicalState.Standby || s == CruiseLogicalState.Active
                }
            ) {
                debug("activateCurrent enable_timeout ${signalSnapshot()}")
                return MbCanCommandResult(false, "Cruise enable timeout")
            }
        }
        if (currentLogicalState(cruiseControlType) == CruiseLogicalState.Active) {
            debug("activateCurrent already_active ${signalSnapshot()}")
            return MbCanCommandResult(true, "Cruise already active")
        }
        if (currentLogicalState(cruiseControlType) != CruiseLogicalState.Standby) {
            debug("activateCurrent not_standby ${signalSnapshot()}")
            return MbCanCommandResult(false, "Cruise not in standby for SET-")
        }
        debug("activateCurrent pulse_214_SET ${signalSnapshot()}")
        val setResult = pulseSetMinus()
        if (!setResult.success) {
            debug("activateCurrent set_failed ${setResult.message}")
            return setResult
        }
        val becameActive = waitPredicate(AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
            currentLogicalState(cruiseControlType) == CruiseLogicalState.Active
        }
        debug("activateCurrent done becameActive=$becameActive ${signalSnapshot()}")
        return MbCanCommandResult(true, "Cruise activated at current speed useAcc=$useAcc")
    }

    private suspend fun runAccEngageToTarget(
        generation: Int,
        target: Int,
        increaseMs: Long,
        decreaseMs: Long,
        captureSetpoint: Boolean,
    ) {
        if (!isCurrentGeneration(generation)) return
        debug("accConverge start capture=$captureSetpoint target=$target ${signalSnapshot()}")

        if (captureSetpoint) {
            if (!AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)) {
                val mode = UniversalCanRepository.accCruiseMode.value
                val alreadyStandby = AccCruiseDomain.isStandbyDisplay(mode)
                if (!alreadyStandby) {
                    debug("accConverge pulse_210")
                    val enableResult = pulseCruiseControl()
                    if (!enableResult.success) return
                    if (!waitUntil(generation, AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
                            AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value) ||
                                AccCruiseDomain.isStandbyDisplay(UniversalCanRepository.accCruiseMode.value)
                        }
                    ) {
                        debug("accConverge enable_timeout ${signalSnapshot()}")
                        return
                    }
                }
                if (!isCurrentGeneration(generation)) return
                if (!AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value) &&
                    AccCruiseDomain.isStandbyDisplay(UniversalCanRepository.accCruiseMode.value)
                ) {
                    debug("accConverge pulse_214_SET")
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
        }

        if (!isCurrentGeneration(generation)) return
        if (!AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)) {
            debug("accConverge abort_not_engaged ${signalSnapshot()}")
            return
        }

        val current = UniversalCanRepository.accCruiseVSetDisKmh.value
        if (current != null && current == target) {
            debug("accConverge already_at_target vSet=$current")
            return
        }

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
        debug(
            "accConverge end genOk=${isCurrentGeneration(generation)} " +
                "vSet=${UniversalCanRepository.accCruiseVSetDisKmh.value} target=$target",
        )
    }

    private suspend fun runCcsEngageToTarget(
        generation: Int,
        target: Int,
        increaseMs: Long,
        decreaseMs: Long,
        captureSetpoint: Boolean,
    ) {
        if (!isCurrentGeneration(generation)) return
        debug("ccsConverge start capture=$captureSetpoint target=$target ${signalSnapshot()}")

        if (captureSetpoint) {
            if (!AccCruiseDomain.isCcsEngaged(UniversalCanRepository.ccsCruiseStatus.value)) {
                debug("ccsConverge pulse_210")
                val enableResult = pulseCruiseControl()
                if (!enableResult.success) return
                waitUntil(generation, AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
                    AccCruiseDomain.isCcsEngaged(UniversalCanRepository.ccsCruiseStatus.value)
                }
                if (!isCurrentGeneration(generation)) return
            }
            if (!AccCruiseDomain.isCcsActive(UniversalCanRepository.ccsCruiseStatus.value)) {
                debug("ccsConverge pulse_214_SET ${signalSnapshot()}")
                pulseSetMinus()
                delay(AccCruiseDomain.CCS_POST_SET_DELAY_MS)
                if (!isCurrentGeneration(generation)) return
            }
        }

        // Batch converge loop (unchanged algorithm).
        val deadlineElapsed = System.currentTimeMillis() + AccCruiseDomain.CCS_CONVERGE_TIMEOUT_MS
        while (isCurrentGeneration(generation) && System.currentTimeMillis() < deadlineElapsed) {
            if (ccsAbortedByDriver()) {
                debug("ccsConverge abort_driver ${signalSnapshot()}")
                return
            }

            val speed = TripTelemetryRepository.carSpeed.value
            if (speed == null || !speed.isFinite()) {
                delay(AccCruiseDomain.STATE_POLL_MS)
                continue
            }

            // 1) Already in band: wait verify, recheck, stop or restart measure.
            if (AccCruiseDomain.isVehicleSpeedAtTarget(speed, target)) {
                if (!ccsWaitWhileAlive(generation, deadlineElapsed, AccCruiseDomain.CCS_AT_TARGET_VERIFY_MS)) {
                    debug("ccsConverge verify_aborted ${signalSnapshot()}")
                    return
                }
                if (ccsAbortedByDriver()) {
                    debug("ccsConverge abort_driver ${signalSnapshot()}")
                    return
                }
                if (AccCruiseDomain.isVehicleSpeedAtTarget(TripTelemetryRepository.carSpeed.value, target)) {
                    debug("ccsConverge done_in_band speed=${TripTelemetryRepository.carSpeed.value}")
                    return
                }
                continue
            }

            val delta = AccCruiseDomain.ccsStepDelta(speed, target)
            if (delta == null) {
                delay(AccCruiseDomain.STATE_POLL_MS)
                continue
            }
            val steps = AccCruiseDomain.ccsBatchSteps(delta)
            if (steps <= 0) {
                delay(AccCruiseDomain.STATE_POLL_MS)
                continue
            }
            val increasing = delta > 0

            // 2) Pulse batch of 1 (up to 5); overshoot ? restart measure.
            var overshot = false
            for (i in 0 until steps) {
                if (!isCurrentGeneration(generation) || System.currentTimeMillis() >= deadlineElapsed) {
                    debug("ccsConverge batch_stop gen/deadline ${signalSnapshot()}")
                    return
                }
                if (ccsAbortedByDriver()) {
                    debug("ccsConverge abort_driver ${signalSnapshot()}")
                    return
                }
                if (increasing) {
                    pulseResPlus()
                    delay(increaseMs)
                } else {
                    pulseSetMinus()
                    delay(decreaseMs)
                }
                if (ccsAbortedByDriver()) {
                    debug("ccsConverge abort_driver ${signalSnapshot()}")
                    return
                }
                if (AccCruiseDomain.ccsOvershot(TripTelemetryRepository.carSpeed.value, target, increasing)) {
                    overshot = true
                    debug("ccsConverge overshoot speed=${TripTelemetryRepository.carSpeed.value}")
                    break
                }
            }
            if (overshot) continue
            if (!isCurrentGeneration(generation) || System.currentTimeMillis() >= deadlineElapsed) {
                debug("ccsConverge stop gen/deadline ${signalSnapshot()}")
                return
            }

            // 3) Wait 1s; branch on in-band / unchanged / still moving.
            val waitStart = TripTelemetryRepository.carSpeed.value
            if (!ccsWaitWhileAlive(generation, deadlineElapsed, AccCruiseDomain.CCS_POST_BATCH_WAIT_MS)) {
                debug("ccsConverge post_batch_aborted ${signalSnapshot()}")
                return
            }
            if (ccsAbortedByDriver()) {
                debug("ccsConverge abort_driver ${signalSnapshot()}")
                return
            }
            val waitEnd = TripTelemetryRepository.carSpeed.value
            if (AccCruiseDomain.isVehicleSpeedAtTarget(waitEnd, target)) {
                if (!ccsWaitWhileAlive(generation, deadlineElapsed, AccCruiseDomain.CCS_POST_BATCH_WAIT_MS)) {
                    debug("ccsConverge post_verify_aborted ${signalSnapshot()}")
                    return
                }
                if (ccsAbortedByDriver()) {
                    debug("ccsConverge abort_driver ${signalSnapshot()}")
                    return
                }
                if (AccCruiseDomain.isVehicleSpeedAtTarget(TripTelemetryRepository.carSpeed.value, target)) {
                    debug("ccsConverge done_in_band speed=${TripTelemetryRepository.carSpeed.value}")
                    return
                }
                continue
            }
            if (AccCruiseDomain.ccsSpeedUnchanged(waitStart, waitEnd)) {
                continue
            }

            // 4) Still moving toward target: one more 1s patience, then restart measure.
            if (!ccsWaitWhileAlive(generation, deadlineElapsed, AccCruiseDomain.CCS_POST_BATCH_WAIT_MS)) {
                debug("ccsConverge patience_aborted ${signalSnapshot()}")
                return
            }
            if (ccsAbortedByDriver()) {
                debug("ccsConverge abort_driver ${signalSnapshot()}")
                return
            }
            if (AccCruiseDomain.isVehicleSpeedAtTarget(TripTelemetryRepository.carSpeed.value, target)) {
                debug("ccsConverge done_in_band speed=${TripTelemetryRepository.carSpeed.value}")
                return
            }
        }
        debug("ccsConverge end timeout_or_cancel ${signalSnapshot()}")
    }

    /** Delay up to [durationMs] while generation and deadline remain valid; false if aborted by time. */
    private suspend fun ccsWaitWhileAlive(
        generation: Int,
        deadlineElapsed: Long,
        durationMs: Long,
    ): Boolean {
        val endAt = minOf(deadlineElapsed, System.currentTimeMillis() + durationMs)
        while (isCurrentGeneration(generation) && System.currentTimeMillis() < endAt) {
            if (ccsAbortedByDriver()) return false
            delay(AccCruiseDomain.STATE_POLL_MS)
        }
        return isCurrentGeneration(generation) && System.currentTimeMillis() < deadlineElapsed
    }

    /**
     * Abort when Gasped reports cruise system off (not in {1,2}).
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

    private suspend fun waitPredicate(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val ok = withTimeoutOrNull(timeoutMs) {
            while (!predicate()) {
                delay(AccCruiseDomain.STATE_POLL_MS)
            }
            true
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
