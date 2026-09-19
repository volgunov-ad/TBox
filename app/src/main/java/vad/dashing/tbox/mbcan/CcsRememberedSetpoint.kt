package vad.dashing.tbox.mbcan

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.normalizeAccCruiseTargetKmh

/**
 * Session-only remembered CCS cruise setpoint (km/h).
 *
 * Conventional cruise has no HU VSetDis — we track the last SET/RES target from:
 * 1. Widget MFS pulses ([markOurPulse] / [nudgeBy] / capture),
 * 2. A9 hardkey left joystick (RES+ = 29, SET− = 30),
 * 3. Fallback status+speed heuristics when hardkey is missing (A10),
 * 4. Stable-speed reconcile when live speed holds far from remembered.
 *
 * Cleared on full Off (status 0). Does not survive process/app restart.
 * TBox `cruiseSetSpeed` is intentionally ignored. ACC path is unaffected.
 */
object CcsRememberedSetpoint {
    private const val LOG_TAG = "CcsSetpoint"

    /** A9 mbCAN: left-wheel joystick up → RES+. */
    const val HARDKEY_RES_PLUS = 29

    /** A9 mbCAN: left-wheel joystick down → SET−. */
    const val HARDKEY_SET_MINUS = 30

    /** A9 mbCAN hardkey: 0 = pressed, 1 = released. */
    const val HARDKEY_STATUS_PRESSED = 0

    /** Ignore duplicate hardkey presses within this window. */
    const val HARDKEY_DEBOUNCE_MS = 120L

    /** Stalk enter-Active fallback (no hardkey): keep if speed within this band of remembered. */
    const val STALK_MATCH_THRESHOLD_KMH = 2

    /** Ignore external enter-Active / speed reconcile while our MFS pulse may still settle. */
    const val OUR_PULSE_WINDOW_MS = 2_000L

    /** Active ±1 without hardkey: adopt rounded speed after it holds this long. */
    const val NUDGE_SETTLE_MS = 500L

    /**
     * When vehicle speed stays within [STABLE_SPEED_BAND_KMH] of an anchor for this long
     * and differs from remembered by ≥ [RECONCILE_MIN_DELTA_KMH], adopt speed as setpoint.
     */
    const val STABLE_RECONCILE_MS = 2_000L

    /** Max |speed − stabilityAnchor| while building a stable window. */
    const val STABLE_SPEED_BAND_KMH = 1f

    /** Minimum |speed − remembered| to trigger stable reconcile. */
    const val RECONCILE_MIN_DELTA_KMH = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startLock = Any()
    private var started = false

    private val _kmh = MutableStateFlow<Int?>(null)
    val kmh: StateFlow<Int?> = _kmh.asStateFlow()

    @Volatile
    private var ourPulseUntilElapsed: Long = 0L

    private var lastStatus: Int? = null
    private var pendingNudgeKmh: Int? = null
    private var pendingNudgeSinceElapsed: Long = 0L

    /** Hardkey already explained the next Standby→Active transition. */
    private var hardkeyOwnedEnterActive = false

    private var lastHardkeyCode: Int? = null
    private var lastHardkeyElapsed: Long = 0L

    private var stabilityAnchorKmh: Float? = null
    private var stabilitySinceElapsed: Long = 0L

    fun hasSetpoint(): Boolean = _kmh.value != null

    fun ensureStarted() {
        synchronized(startLock) {
            if (started) return
            started = true
        }
        scope.launch {
            UniversalCanRepository.ccsCruiseStatus.collectLatest { status ->
                onStatus(status)
            }
        }
        scope.launch {
            TripTelemetryRepository.carSpeed.collectLatest { speed ->
                onSpeedWhileActive(speed)
            }
        }
        scope.launch {
            UniversalCanRepository.mode.collectLatest { mode ->
                val enable = mode == HeadUnitCanMode.Android9MbCan
                debug("hardkey_tracking enable=$enable mode=$mode")
                MbCanRepository.setCcsHardKeyTrackingEnabled(enable)
            }
        }
    }

    /** Mark that we just sent an MFS pulse so stalk inference yields to controller updates. */
    fun markOurPulse(windowMs: Long = OUR_PULSE_WINDOW_MS) {
        ensureStarted()
        ourPulseUntilElapsed = System.currentTimeMillis() + windowMs
        resetStability("our_pulse")
    }

    fun isWithinOurPulseWindow(nowElapsed: Long = System.currentTimeMillis()): Boolean =
        nowElapsed < ourPulseUntilElapsed

    fun clear(reason: String) {
        if (_kmh.value == null) return
        debug("clear reason=$reason was=${_kmh.value}")
        _kmh.value = null
        pendingNudgeKmh = null
        hardkeyOwnedEnterActive = false
        resetStability(reason)
    }

    fun remember(kmh: Int, reason: String) {
        ensureStarted()
        val normalized = normalizeAccCruiseTargetKmh(kmh)
        if (_kmh.value == normalized) return
        debug("set reason=$reason kmh=$normalized was=${_kmh.value}")
        _kmh.value = normalized
        pendingNudgeKmh = null
        resetStability(reason)
    }

    /** Capture rounded vehicle speed as remembered setpoint. */
    fun captureFromVehicleSpeed(reason: String): Boolean {
        val speed = TripTelemetryRepository.carSpeed.value
        val rounded = roundSpeedKmh(speed) ?: return false
        remember(rounded, reason)
        return true
    }

    fun nudgeBy(delta: Int, reason: String) {
        val current = _kmh.value
        if (current == null) {
            captureFromVehicleSpeed(reason)
            return
        }
        remember(current + delta, reason)
    }

    /**
     * A9 hardkey callback (left joystick RES+/SET−). Only PRESSED events; debounced.
     * Safe to call from OEM callback threads.
     */
    fun onHardKey(keyCode: Int, keyStatus: Int, keyType: Int) {
        if (keyStatus != HARDKEY_STATUS_PRESSED) return
        val key = parseHardkeyCruiseKey(keyCode) ?: return
        val now = System.currentTimeMillis()
        if (lastHardkeyCode == keyCode && now - lastHardkeyElapsed < HARDKEY_DEBOUNCE_MS) {
            return
        }
        lastHardkeyCode = keyCode
        lastHardkeyElapsed = now

        val status = lastStatus ?: UniversalCanRepository.ccsCruiseStatus.value
        val decision = decideHardkey(
            key = key,
            ccsStatus = status,
        )
        debug(
            "hardkey key=$key status=$status decision=$decision " +
                "keyType=$keyType remembered=${_kmh.value}",
        )
        applyHardkeyDecision(decision)
    }

    fun applyHardkeyDecision(decision: HardkeySetpointDecision) {
        when (decision) {
            HardkeySetpointDecision.Ignore -> Unit
            HardkeySetpointDecision.KeepRemembered -> {
                hardkeyOwnedEnterActive = true
            }
            is HardkeySetpointDecision.CaptureSpeed -> {
                hardkeyOwnedEnterActive = true
                if (!captureFromVehicleSpeed(decision.reason) && _kmh.value == null) {
                    debug("hardkey capture_pending_no_speed reason=${decision.reason}")
                }
            }
            is HardkeySetpointDecision.Nudge -> {
                nudgeBy(decision.delta, decision.reason)
            }
        }
    }

    /**
     * Pure hardkey → setpoint action.
     * Standby RES+ keeps remembered (resume); Standby SET− captures current speed;
     * Active RES+/SET− nudge ±1. Off / unknown → ignore.
     *
     * Widget MFS writes do not appear on the hardkey channel, so stalk presses are
     * always applied (including during widget converge).
     */
    fun decideHardkey(
        key: HardkeyCruiseKey,
        ccsStatus: Int?,
    ): HardkeySetpointDecision {
        val active = AccCruiseDomain.isCcsActive(ccsStatus)
        val standby = AccCruiseDomain.isCcsStandby(ccsStatus)
        return when (key) {
            HardkeyCruiseKey.ResPlus -> when {
                active -> HardkeySetpointDecision.Nudge(1, "hardkey_res")
                standby -> HardkeySetpointDecision.KeepRemembered
                else -> HardkeySetpointDecision.Ignore
            }
            HardkeyCruiseKey.SetMinus -> when {
                active -> HardkeySetpointDecision.Nudge(-1, "hardkey_set")
                standby -> HardkeySetpointDecision.CaptureSpeed("hardkey_set")
                else -> HardkeySetpointDecision.Ignore
            }
        }
    }

    fun parseHardkeyCruiseKey(keyCode: Int): HardkeyCruiseKey? = when (keyCode) {
        HARDKEY_RES_PLUS -> HardkeyCruiseKey.ResPlus
        HARDKEY_SET_MINUS -> HardkeyCruiseKey.SetMinus
        else -> null
    }

    /**
     * Pure decision for stalk-driven enter Active when hardkey was not seen (A10 / miss).
     * Null remembered → capture speed; within threshold of remembered → keep; else capture (new SET).
     */
    fun decideStalkEnterActive(
        rememberedKmh: Int?,
        vehicleSpeedKmh: Float?,
    ): StalkEnterActiveDecision {
        val rounded = roundSpeedKmh(vehicleSpeedKmh)
        if (rememberedKmh == null) {
            return if (rounded != null) {
                StalkEnterActiveDecision.Capture(rounded)
            } else {
                StalkEnterActiveDecision.Keep
            }
        }
        if (rounded == null) return StalkEnterActiveDecision.Keep
        return if (abs(rounded - rememberedKmh) <= STALK_MATCH_THRESHOLD_KMH) {
            StalkEnterActiveDecision.Keep
        } else {
            StalkEnterActiveDecision.Capture(rounded)
        }
    }

    /**
     * Pure: adopt live speed as remembered when it has been stable long enough and
     * differs from remembered by at least [RECONCILE_MIN_DELTA_KMH].
     */
    fun shouldReconcileStableSpeed(
        rememberedKmh: Int?,
        roundedSpeedKmh: Int?,
        stableForMs: Long,
        withinOurPulse: Boolean,
    ): Boolean {
        if (withinOurPulse) return false
        val remembered = rememberedKmh ?: return false
        val rounded = roundedSpeedKmh ?: return false
        if (stableForMs < STABLE_RECONCILE_MS) return false
        return abs(rounded - remembered) >= RECONCILE_MIN_DELTA_KMH
    }

    fun shouldShowRememberedSetpoint(ccsStatus: Int?): Boolean =
        AccCruiseDomain.isCcsEngaged(ccsStatus) && hasSetpoint()

    private fun onStatus(status: Int?) {
        val previous = lastStatus
        lastStatus = status
        if (status != null && UniversalCanRepository.mode.value == HeadUnitCanMode.Android9MbCan) {
            // mbCAN may bind after ensureStarted; re-assert OEM hardkey registration.
            MbCanRepository.setCcsHardKeyTrackingEnabled(true)
        }
        when {
            status == null -> {
                clear("status_null")
                pendingNudgeKmh = null
                return
            }
            status == 0 || !AccCruiseDomain.isCcsEngaged(status) -> {
                clear("status_off")
                pendingNudgeKmh = null
                return
            }
        }
        if (!AccCruiseDomain.isCcsActive(status)) {
            pendingNudgeKmh = null
            resetStability("not_active")
            return
        }
        // Entered Active.
        if (!AccCruiseDomain.isCcsActive(previous)) {
            onEnteredActive()
        }
    }

    private fun onEnteredActive() {
        if (isWithinOurPulseWindow()) {
            // Controller owns the update (SET capture / RES keep). Safety net if SET left memory empty.
            if (_kmh.value == null) {
                captureFromVehicleSpeed("our_pulse_enter_active")
            }
            hardkeyOwnedEnterActive = false
            return
        }
        if (hardkeyOwnedEnterActive) {
            hardkeyOwnedEnterActive = false
            if (_kmh.value == null) {
                captureFromVehicleSpeed("hardkey_enter_active_fallback")
            } else {
                debug("hardkey_enter_active keep remembered=${_kmh.value}")
            }
            return
        }
        when (
            val decision = decideStalkEnterActive(_kmh.value, TripTelemetryRepository.carSpeed.value)
        ) {
            StalkEnterActiveDecision.Keep ->
                debug("stalk_enter_active keep remembered=${_kmh.value}")
            is StalkEnterActiveDecision.Capture ->
                remember(decision.kmh, "stalk_enter_active")
        }
    }

    private fun onSpeedWhileActive(speed: Float?) {
        if (!AccCruiseDomain.isCcsActive(lastStatus)) {
            pendingNudgeKmh = null
            resetStability("not_active")
            return
        }
        if (isWithinOurPulseWindow()) {
            pendingNudgeKmh = null
            resetStability("our_pulse")
            return
        }
        val remembered = _kmh.value
        val rounded = roundSpeedKmh(speed)
        if (remembered == null || rounded == null || speed == null || !speed.isFinite()) {
            pendingNudgeKmh = null
            resetStability("no_speed")
            return
        }

        val delta = abs(rounded - remembered)
        when {
            delta == 0 -> {
                pendingNudgeKmh = null
                updateStability(speed)
            }
            delta == 1 -> {
                val now = System.currentTimeMillis()
                if (pendingNudgeKmh != rounded) {
                    pendingNudgeKmh = rounded
                    pendingNudgeSinceElapsed = now
                } else if (now - pendingNudgeSinceElapsed >= NUDGE_SETTLE_MS) {
                    remember(rounded, "stalk_nudge")
                    return
                }
                updateStability(speed)
            }
            else -> {
                pendingNudgeKmh = null
                updateStability(speed)
                val stableFor = currentStableDurationMs()
                if (
                    shouldReconcileStableSpeed(
                        rememberedKmh = remembered,
                        roundedSpeedKmh = rounded,
                        stableForMs = stableFor,
                        withinOurPulse = false,
                    )
                ) {
                    remember(rounded, "stable_reconcile")
                }
            }
        }
    }

    private fun updateStability(speed: Float) {
        val now = System.currentTimeMillis()
        val anchor = stabilityAnchorKmh
        if (anchor == null || abs(speed - anchor) > STABLE_SPEED_BAND_KMH) {
            stabilityAnchorKmh = speed
            stabilitySinceElapsed = now
            return
        }
        // still within band — keep since
    }

    private fun currentStableDurationMs(now: Long = System.currentTimeMillis()): Long {
        if (stabilityAnchorKmh == null) return 0L
        return (now - stabilitySinceElapsed).coerceAtLeast(0L)
    }

    private fun resetStability(reason: String) {
        if (stabilityAnchorKmh != null) {
            debug("stability_reset reason=$reason")
        }
        stabilityAnchorKmh = null
        stabilitySinceElapsed = 0L
    }

    private fun roundSpeedKmh(speed: Float?): Int? {
        if (speed == null || !speed.isFinite()) return null
        return speed.roundToInt().coerceIn(0, 300)
    }

    private fun debug(message: String) {
        MbCanDiagnostics.log("DEBUG", LOG_TAG, message)
    }

    enum class HardkeyCruiseKey {
        ResPlus,
        SetMinus,
    }

    sealed class HardkeySetpointDecision {
        data object Ignore : HardkeySetpointDecision()
        data object KeepRemembered : HardkeySetpointDecision()
        data class CaptureSpeed(val reason: String) : HardkeySetpointDecision()
        data class Nudge(val delta: Int, val reason: String) : HardkeySetpointDecision()
    }

    sealed class StalkEnterActiveDecision {
        data object Keep : StalkEnterActiveDecision()
        data class Capture(val kmh: Int) : StalkEnterActiveDecision()
    }
}
