package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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

    /** In-flight one-shot tile action (tap / swipe / double-tap). */
    private var commandJob: Job? = null
    private val commandLock = Any()

    /** Non-null while a setpoint tile is converging; value identifies the tapped tile. */
    private val _adjustingWidgetKey = MutableStateFlow<String?>(null)
    val adjustingWidgetKey: StateFlow<String?> = _adjustingWidgetKey.asStateFlow()

    private fun debug(message: String) {
        MbCanDiagnostics.log("DEBUG", LOG_TAG, message)
    }

    private fun signalSnapshot(): String =
        "accMode=${UniversalCanRepository.accCruiseMode.value}" +
            " vSet=${UniversalCanRepository.accCruiseVSetDisKmh.value}" +
            " ccs=${UniversalCanRepository.ccsCruiseStatus.value}" +
            " ccsSet=${CcsRememberedSetpoint.kmh.value}" +
            " carSpeed=${TripTelemetryRepository.carSpeed.value}" +
            " frm=${UniversalCanRepository.accFrmFeedbackAvailable.value}" +
            " accEver=${UniversalCanRepository.accModeEverNonZero.value}"

    private fun resolveUseAcc(cruiseControlType: CruiseControlType): Boolean =
        AccCruiseDomain.shouldUseAccPath(
            frmFeedbackAvailable = UniversalCanRepository.accFrmFeedbackAvailable.value,
            type = cruiseControlType,
            accMode = UniversalCanRepository.accCruiseMode.value,
            ccsStatus = UniversalCanRepository.ccsCruiseStatus.value,
            accModeEverNonZero = UniversalCanRepository.accModeEverNonZero.value,
        )

    private fun hasStandbyResumeSetpoint(useAcc: Boolean): Boolean =
        if (useAcc) {
            AccCruiseDomain.shouldShowAccSetpoint(UniversalCanRepository.accCruiseMode.value) &&
                UniversalCanRepository.accCruiseVSetDisKmh.value != null
        } else {
            CcsRememberedSetpoint.hasSetpoint()
        }

    /**
     * Run a one-shot tile action, cancelling the previous one first.
     *
     * Tile actions keep waiting for the vehicle to reach the expected state after their pulse
     * (up to [AccCruiseDomain.ENGAGE_TIMEOUT_MS]). Without this, a tap followed by a double-tap
     * left the first wait running and it could still send a follow-up pulse (e.g. SET-) after
     * the driver already asked for full off.
     */
    private fun launchExclusive(block: suspend () -> Unit) {
        synchronized(commandLock) {
            val previous = commandJob
            commandJob = scope.launch {
                if (previous?.isActive == true) {
                    debug("cancel_pending_command")
                    previous.cancelAndJoin()
                }
                block()
            }
        }
    }

    fun launchEngageToTarget(
        targetKmh: Int,
        increaseIntervalMs: Int,
        decreaseIntervalMs: Int,
        cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
        widgetKey: String = "",
    ) {
        launchExclusive {
            engageToTarget(
                targetKmh,
                increaseIntervalMs,
                decreaseIntervalMs,
                cruiseControlType,
                widgetKey,
            )
        }
    }

    /** Setpoint / status double-tap: full off via 210 when Standby or Active. */
    fun launchFullOff(cruiseControlType: CruiseControlType = CruiseControlType.AUTO) {
        launchExclusive {
            fullOff(cruiseControlType)
        }
    }

    /** Status tile single tap: Off ? enable+SET?; Standby ? RES+ if setpoint else SET?; Active ? pause. */
    fun launchStatusSingleTap(cruiseControlType: CruiseControlType = CruiseControlType.AUTO) {
        launchExclusive {
            statusSingleTap(cruiseControlType)
        }
    }

    /**
     * Status tile swipe down: Standby ? SET? (activate current); Active ? SET? (?1 km/h).
     */
    fun launchStatusSwipeDown(cruiseControlType: CruiseControlType = CruiseControlType.AUTO) {
        launchExclusive {
            statusSwipeDown(cruiseControlType)
        }
    }

    /**
     * Status tile swipe up: Standby ? RES+ (resume); Active ? RES+ (+1 km/h).
     */
    fun launchStatusSwipeUp(cruiseControlType: CruiseControlType = CruiseControlType.AUTO) {
        launchExclusive {
            statusSwipeUp(cruiseControlType)
        }
    }

    suspend fun engageToTarget(
        targetKmh: Int,
        increaseIntervalMs: Int,
        decreaseIntervalMs: Int,
        cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
        widgetKey: String = "",
    ): MbCanCommandResult {
        val target = normalizeAccCruiseTargetKmh(targetKmh)
        val increaseMs = normalizeAccCruiseStepIntervalMs(increaseIntervalMs).toLong()
        val decreaseMs = normalizeAccCruiseStepIntervalMs(decreaseIntervalMs).toLong()
        val useAcc = resolveUseAcc(cruiseControlType)

        val state = currentLogicalState(cruiseControlType)
        val atTarget = state == CruiseLogicalState.Active && isAtWidgetTarget(useAcc, target)
        debug(
            "setpointTap type=$cruiseControlType useAcc=$useAcc state=$state " +
                "target=$target atTarget=$atTarget widgetKey=$widgetKey ${signalSnapshot()}",
        )
        if (state == CruiseLogicalState.Fault) {
            abortAdjustLoop()
            debug("setpointTap action=ignore_fault")
            return MbCanCommandResult(true, "Cruise fault ó tap ignored")
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
                    _adjustingWidgetKey.value = widgetKey.ifBlank { null }
                    if (useAcc) {
                        runAccEngageToTarget(generation, target, increaseMs, decreaseMs, captureSetpoint)
                    } else {
                        runCcsEngageToTarget(generation, target, increaseMs, decreaseMs, captureSetpoint)
                    }
                } finally {
                    if (generation == adjustGeneration) {
                        _adjustingWidgetKey.value = null
                    }
                }
            }
        }
        return MbCanCommandResult(true, "ACC/CCS adjust started target=$target type=$cruiseControlType")
    }

    suspend fun statusSingleTap(
        cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
    ): MbCanCommandResult {
        CcsRememberedSetpoint.ensureStarted()
        abortAdjustLoop()
        val useAcc = resolveUseAcc(cruiseControlType)
        val state = currentLogicalState(cruiseControlType)
        debug("statusTap type=$cruiseControlType state=$state ${signalSnapshot()}")
        return when (state) {
            CruiseLogicalState.Fault -> {
                debug("statusTap action=ignore_fault")
                MbCanCommandResult(true, "Cruise fault ? tap ignored")
            }
            CruiseLogicalState.Active -> {
                debug("statusTap action=pause_212")
                pulseCancel()
            }
            CruiseLogicalState.Off -> {
                debug("statusTap action=activate_current")
                activateAtCurrentSpeed(cruiseControlType)
            }
            CruiseLogicalState.Standby -> {
                val hasSetpoint = hasStandbyResumeSetpoint(useAcc)
                if (hasSetpoint) {
                    debug("statusTap action=resume_res_plus hasSetpoint=true")
                    resumePriorSetpoint(cruiseControlType)
                } else {
                    debug("statusTap action=activate_set_minus hasSetpoint=false")
                    activateAtCurrentSpeed(cruiseControlType)
                }
            }
        }
    }

    suspend fun statusSwipeDown(
        cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
    ): MbCanCommandResult {
        CcsRememberedSetpoint.ensureStarted()
        abortAdjustLoop()
        val useAcc = resolveUseAcc(cruiseControlType)
        val state = currentLogicalState(cruiseControlType)
        debug("statusSwipeDown type=$cruiseControlType state=$state ${signalSnapshot()}")
        return when (state) {
            CruiseLogicalState.Standby -> {
                debug("statusSwipeDown action=activate_set_minus")
                activateAtCurrentSpeed(cruiseControlType)
            }
            CruiseLogicalState.Active -> {
                debug("statusSwipeDown action=nudge_set_minus")
                if (!useAcc) {
                    CcsRememberedSetpoint.markOurPulse()
                    CcsRememberedSetpoint.nudgeBy(-1, "widget_nudge_set")
                }
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
        CcsRememberedSetpoint.ensureStarted()
        abortAdjustLoop()
        val useAcc = resolveUseAcc(cruiseControlType)
        val state = currentLogicalState(cruiseControlType)
        debug("statusSwipeUp type=$cruiseControlType state=$state ${signalSnapshot()}")
        return when (state) {
            CruiseLogicalState.Standby -> {
                debug("statusSwipeUp action=resume_res_plus")
                resumePriorSetpoint(cruiseControlType)
            }
            CruiseLogicalState.Active -> {
                debug("statusSwipeUp action=nudge_res_plus")
                if (!useAcc) {
                    CcsRememberedSetpoint.markOurPulse()
                    CcsRememberedSetpoint.nudgeBy(1, "widget_nudge_res")
                }
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
        CcsRememberedSetpoint.ensureStarted()
        abortAdjustLoop()
        val useAcc = resolveUseAcc(cruiseControlType)
        val state = currentLogicalState(cruiseControlType)
        debug("fullOff type=$cruiseControlType state=$state ${signalSnapshot()}")
        return when (state) {
            CruiseLogicalState.Standby, CruiseLogicalState.Active -> {
                debug("fullOff action=pulse_210")
                if (!useAcc) {
                    CcsRememberedSetpoint.markOurPulse()
                    // Off status push will clear; clear eagerly so UI updates before bus lag.
                    CcsRememberedSetpoint.clear("widget_full_off")
                }
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
        _adjustingWidgetKey.value = null
    }

    private fun currentLogicalState(cruiseControlType: CruiseControlType): CruiseLogicalState {
        val useAcc = resolveUseAcc(cruiseControlType)
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

    /** Standby ? Active via RES+ (resume prior setpoint). */
    private suspend fun resumePriorSetpoint(
        cruiseControlType: CruiseControlType,
    ): MbCanCommandResult {
        val useAcc = resolveUseAcc(cruiseControlType)
        if (!useAcc) CcsRememberedSetpoint.markOurPulse()
        val result = pulseResPlus()
        if (!result.success) {
            debug("resumePriorSetpoint res_failed ${result.message}")
            return result
        }
        val becameActive = waitPredicate(AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
            currentLogicalState(cruiseControlType) == CruiseLogicalState.Active
        }
        debug("resumePriorSetpoint done becameActive=$becameActive ${signalSnapshot()}")
        return MbCanCommandResult(true, "Cruise RES+ from Standby")
    }

    /** Status tile: 210 (if Off) then SET? to Active at current speed. */
    private suspend fun activateAtCurrentSpeed(
        cruiseControlType: CruiseControlType,
    ): MbCanCommandResult {
        val useAcc = resolveUseAcc(cruiseControlType)
        if (currentLogicalState(cruiseControlType) == CruiseLogicalState.Off) {
            debug("activateCurrent pulse_210 useAcc=$useAcc")
            if (!useAcc) CcsRememberedSetpoint.markOurPulse()
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
            if (!useAcc) CcsRememberedSetpoint.captureFromVehicleSpeed("widget_already_active")
            return MbCanCommandResult(true, "Cruise already active")
        }
        if (currentLogicalState(cruiseControlType) != CruiseLogicalState.Standby) {
            debug("activateCurrent not_standby ${signalSnapshot()}")
            return MbCanCommandResult(false, "Cruise not in standby for SET-")
        }
        debug("activateCurrent pulse_214_SET ${signalSnapshot()}")
        if (!useAcc) CcsRememberedSetpoint.markOurPulse()
        val setResult = pulseSetMinus()
        if (!setResult.success) {
            debug("activateCurrent set_failed ${setResult.message}")
            return setResult
        }
        val becameActive = waitPredicate(AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
            currentLogicalState(cruiseControlType) == CruiseLogicalState.Active
        }
        if (!useAcc && becameActive) {
            CcsRememberedSetpoint.captureFromVehicleSpeed("widget_set")
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
        if (convergeAbortedByDriver(useAcc = true)) {
            debug("accConverge abort_driver ${signalSnapshot()}")
            return
        }

        val current = UniversalCanRepository.accCruiseVSetDisKmh.value
        if (current != null && current == target) {
            debug("accConverge already_at_target vSet=$current")
            runPostConvergeVerify(generation, useAcc = true, target, increaseMs, decreaseMs)
            return
        }

        while (isCurrentGeneration(generation) && !convergeAbortedByDriver(useAcc = true)) {
            val speed = UniversalCanRepository.accCruiseVSetDisKmh.value ?: break
            if (speed == target) break
            if (speed < target) {
                pulseResPlus()
                if (!delayWhileConverging(generation, useAcc = true, increaseMs)) {
                    debug("accConverge abort_driver_during_step ${signalSnapshot()}")
                    return
                }
            } else {
                pulseSetMinus()
                if (!delayWhileConverging(generation, useAcc = true, decreaseMs)) {
                    debug("accConverge abort_driver_during_step ${signalSnapshot()}")
                    return
                }
            }
        }
        debug(
            "accConverge end genOk=${isCurrentGeneration(generation)} " +
                "vSet=${UniversalCanRepository.accCruiseVSetDisKmh.value} target=$target",
        )
        if (!isCurrentGeneration(generation) || convergeAbortedByDriver(useAcc = true)) {
            if (convergeAbortedByDriver(useAcc = true)) {
                debug("accConverge abort_driver ${signalSnapshot()}")
            }
            return
        }
        runPostConvergeVerify(generation, useAcc = true, target, increaseMs, decreaseMs)
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
        CcsRememberedSetpoint.ensureStarted()

        if (captureSetpoint) {
            if (!AccCruiseDomain.isCcsEngaged(UniversalCanRepository.ccsCruiseStatus.value)) {
                debug("ccsConverge pulse_210")
                CcsRememberedSetpoint.markOurPulse()
                val enableResult = pulseCruiseControl()
                if (!enableResult.success) return
                waitUntil(generation, AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
                    AccCruiseDomain.isCcsEngaged(UniversalCanRepository.ccsCruiseStatus.value)
                }
                if (!isCurrentGeneration(generation)) return
            }
            if (!AccCruiseDomain.isCcsActive(UniversalCanRepository.ccsCruiseStatus.value)) {
                debug("ccsConverge pulse_214_SET ${signalSnapshot()}")
                CcsRememberedSetpoint.markOurPulse()
                pulseSetMinus()
                if (!waitUntil(generation, AccCruiseDomain.ENGAGE_TIMEOUT_MS) {
                        AccCruiseDomain.isCcsActive(UniversalCanRepository.ccsCruiseStatus.value)
                    }
                ) {
                    debug("ccsConverge set_timeout ${signalSnapshot()}")
                    return
                }
                if (!isCurrentGeneration(generation)) return
                CcsRememberedSetpoint.captureFromVehicleSpeed("ccs_converge_set")
            }
        }

        if (convergeAbortedByDriver(useAcc = false)) {
            debug("ccsConverge abort_driver_before_loop ${signalSnapshot()}")
            return
        }

        // Batch converge loop (unchanged algorithm).
        val deadlineElapsed = System.currentTimeMillis() + AccCruiseDomain.CCS_CONVERGE_TIMEOUT_MS
        while (isCurrentGeneration(generation) && System.currentTimeMillis() < deadlineElapsed) {
            if (convergeAbortedByDriver(useAcc = false)) {
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
                if (convergeAbortedByDriver(useAcc = false)) {
                    debug("ccsConverge abort_driver ${signalSnapshot()}")
                    return
                }
                if (AccCruiseDomain.isVehicleSpeedAtTarget(TripTelemetryRepository.carSpeed.value, target)) {
                    debug("ccsConverge done_in_band speed=${TripTelemetryRepository.carSpeed.value}")
                    CcsRememberedSetpoint.remember(target, "ccs_converge_done")
                    runPostConvergeVerify(generation, useAcc = false, target, increaseMs, decreaseMs)
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

            // 2) Pulse batch of ?1 (up to 5); overshoot ? restart measure.
            var overshot = false
            for (i in 0 until steps) {
                if (!isCurrentGeneration(generation) || System.currentTimeMillis() >= deadlineElapsed) {
                    debug("ccsConverge batch_stop gen/deadline ${signalSnapshot()}")
                    return
                }
                if (convergeAbortedByDriver(useAcc = false)) {
                    debug("ccsConverge abort_driver ${signalSnapshot()}")
                    return
                }
                CcsRememberedSetpoint.markOurPulse()
                if (increasing) {
                    pulseResPlus()
                    if (!delayWhileConverging(generation, useAcc = false, increaseMs)) {
                        debug("ccsConverge abort_driver_during_step ${signalSnapshot()}")
                        return
                    }
                } else {
                    pulseSetMinus()
                    if (!delayWhileConverging(generation, useAcc = false, decreaseMs)) {
                        debug("ccsConverge abort_driver_during_step ${signalSnapshot()}")
                        return
                    }
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
            if (convergeAbortedByDriver(useAcc = false)) {
                debug("ccsConverge abort_driver ${signalSnapshot()}")
                return
            }
            val waitEnd = TripTelemetryRepository.carSpeed.value
            if (AccCruiseDomain.isVehicleSpeedAtTarget(waitEnd, target)) {
                if (!ccsWaitWhileAlive(generation, deadlineElapsed, AccCruiseDomain.CCS_POST_BATCH_WAIT_MS)) {
                    debug("ccsConverge post_verify_aborted ${signalSnapshot()}")
                    return
                }
                if (convergeAbortedByDriver(useAcc = false)) {
                    debug("ccsConverge abort_driver ${signalSnapshot()}")
                    return
                }
                if (AccCruiseDomain.isVehicleSpeedAtTarget(TripTelemetryRepository.carSpeed.value, target)) {
                    debug("ccsConverge done_in_band speed=${TripTelemetryRepository.carSpeed.value}")
                    CcsRememberedSetpoint.remember(target, "ccs_converge_done")
                    runPostConvergeVerify(generation, useAcc = false, target, increaseMs, decreaseMs)
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
            if (convergeAbortedByDriver(useAcc = false)) {
                debug("ccsConverge abort_driver ${signalSnapshot()}")
                return
            }
            if (AccCruiseDomain.isVehicleSpeedAtTarget(TripTelemetryRepository.carSpeed.value, target)) {
                debug("ccsConverge done_in_band speed=${TripTelemetryRepository.carSpeed.value}")
                CcsRememberedSetpoint.remember(target, "ccs_converge_done")
                runPostConvergeVerify(generation, useAcc = false, target, increaseMs, decreaseMs)
                return
            }
        }
        debug("ccsConverge end timeout_or_cancel ${signalSnapshot()}")
        if (!isCurrentGeneration(generation) || convergeAbortedByDriver(useAcc = false)) return
        runPostConvergeVerify(generation, useAcc = false, target, increaseMs, decreaseMs)
    }

    /**
     * Wait [POST_CONVERGE_VERIFY_MS], abort if driver left Active, then catch ù1 drift.
     */
    private suspend fun runPostConvergeVerify(
        generation: Int,
        useAcc: Boolean,
        target: Int,
        increaseMs: Long,
        decreaseMs: Long,
    ) {
        if (!isCurrentGeneration(generation)) return
        if (convergeAbortedByDriver(useAcc)) {
            debug("postVerify abort_driver_before_wait useAcc=$useAcc ${signalSnapshot()}")
            return
        }
        debug("postVerify wait useAcc=$useAcc target=$target ${signalSnapshot()}")
        if (!delayWhileConverging(generation, useAcc, AccCruiseDomain.POST_CONVERGE_VERIFY_MS)) {
            debug("postVerify abort_driver_during_wait useAcc=$useAcc ${signalSnapshot()}")
            return
        }
        if (convergeAbortedByDriver(useAcc)) {
            debug("postVerify abort_driver_before_catchup useAcc=$useAcc ${signalSnapshot()}")
            return
        }
        if (useAcc) {
            val vSet = UniversalCanRepository.accCruiseVSetDisKmh.value
            if (vSet != null && vSet == target) {
                debug("postVerify ok vSet=$vSet")
                return
            }
            debug("postVerify catchup_acc vSet=$vSet target=$target")
            var steps = 0
            while (
                isCurrentGeneration(generation) &&
                !convergeAbortedByDriver(useAcc = true) &&
                steps < AccCruiseDomain.POST_CONVERGE_CATCHUP_MAX_STEPS
            ) {
                val speed = UniversalCanRepository.accCruiseVSetDisKmh.value ?: break
                if (speed == target) {
                    debug("postVerify catchup_acc done vSet=$speed")
                    return
                }
                if (speed < target) {
                    pulseResPlus()
                    if (!delayWhileConverging(generation, useAcc = true, increaseMs)) {
                        debug("postVerify abort_driver_catchup ${signalSnapshot()}")
                        return
                    }
                } else {
                    pulseSetMinus()
                    if (!delayWhileConverging(generation, useAcc = true, decreaseMs)) {
                        debug("postVerify abort_driver_catchup ${signalSnapshot()}")
                        return
                    }
                }
                steps++
            }
            debug(
                "postVerify catchup_acc end steps=$steps " +
                    "vSet=${UniversalCanRepository.accCruiseVSetDisKmh.value} target=$target",
            )
        } else {
            if (AccCruiseDomain.isVehicleSpeedAtTarget(TripTelemetryRepository.carSpeed.value, target)) {
                debug("postVerify ok speed=${TripTelemetryRepository.carSpeed.value}")
                return
            }
            val delta = AccCruiseDomain.ccsStepDelta(TripTelemetryRepository.carSpeed.value, target)
            if (delta == null) {
                debug("postVerify catchup_ccs no_speed ${signalSnapshot()}")
                return
            }
            val steps = AccCruiseDomain.ccsBatchSteps(delta)
                .coerceAtMost(AccCruiseDomain.POST_CONVERGE_CATCHUP_MAX_STEPS)
            if (steps <= 0) {
                debug("postVerify catchup_ccs in_band_or_small_delta delta=$delta")
                return
            }
            val increasing = delta > 0
            debug("postVerify catchup_ccs steps=$steps increasing=$increasing ${signalSnapshot()}")
            for (i in 0 until steps) {
                if (!isCurrentGeneration(generation) || convergeAbortedByDriver(useAcc = false)) {
                    debug("postVerify abort_driver_catchup ${signalSnapshot()}")
                    return
                }
                if (increasing) {
                    pulseResPlus()
                    if (!delayWhileConverging(generation, useAcc = false, increaseMs)) {
                        debug("postVerify abort_driver_catchup ${signalSnapshot()}")
                        return
                    }
                } else {
                    pulseSetMinus()
                    if (!delayWhileConverging(generation, useAcc = false, decreaseMs)) {
                        debug("postVerify abort_driver_catchup ${signalSnapshot()}")
                        return
                    }
                }
            }
            debug("postVerify catchup_ccs end speed=${TripTelemetryRepository.carSpeed.value}")
        }
    }

    /** Delay up to [durationMs] while generation and deadline remain valid; false if aborted by time. */
    private suspend fun ccsWaitWhileAlive(
        generation: Int,
        deadlineElapsed: Long,
        durationMs: Long,
    ): Boolean {
        val endAt = minOf(deadlineElapsed, System.currentTimeMillis() + durationMs)
        while (isCurrentGeneration(generation) && System.currentTimeMillis() < endAt) {
            if (convergeAbortedByDriver(useAcc = false)) return false
            delay(AccCruiseDomain.STATE_POLL_MS)
        }
        return isCurrentGeneration(generation) && System.currentTimeMillis() < deadlineElapsed
    }

    /**
     * Delay while cruise stays Active; false if generation cancelled or driver Standby/Off.
     */
    private suspend fun delayWhileConverging(
        generation: Int,
        useAcc: Boolean,
        durationMs: Long,
    ): Boolean {
        val endAt = System.currentTimeMillis() + durationMs
        while (isCurrentGeneration(generation) && System.currentTimeMillis() < endAt) {
            if (convergeAbortedByDriver(useAcc)) return false
            delay(AccCruiseDomain.STATE_POLL_MS)
        }
        return isCurrentGeneration(generation) && !convergeAbortedByDriver(useAcc)
    }

    /**
     * During setpoint stepping: abort on Standby (brake) or Off.
     * Null CCS status alone does not abort (unknown yet).
     */
    private fun convergeAbortedByDriver(useAcc: Boolean): Boolean {
        if (useAcc) {
            return !AccCruiseDomain.isEngaged(UniversalCanRepository.accCruiseMode.value)
        }
        val status = UniversalCanRepository.ccsCruiseStatus.value ?: return false
        return !AccCruiseDomain.isCcsActive(status)
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
