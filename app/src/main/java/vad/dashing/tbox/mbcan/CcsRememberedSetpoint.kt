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
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.normalizeAccCruiseTargetKmh

/**
 * Session-only remembered CCS cruise setpoint (km/h).
 *
 * Conventional cruise has no HU VSetDis — we infer the last SET/RES target from
 * widget MFS pulses and from stalk-driven [ccsCruiseStatus] + vehicle speed.
 * Cleared on full Off (status 0). Does not survive process/app restart.
 * TBox `cruiseSetSpeed` is intentionally ignored.
 */
object CcsRememberedSetpoint {
    private const val LOG_TAG = "CcsSetpoint"

    /** Stalk enter-Active: keep remembered if speed within this band of it (RES), else capture (SET). */
    const val STALK_MATCH_THRESHOLD_KMH = 2

    /** Ignore external enter-Active / nudge inference while our MFS pulse may still settle. */
    const val OUR_PULSE_WINDOW_MS = 2_000L

    /** Active ±1 stalk nudge: adopt rounded speed after it holds this long. */
    const val NUDGE_SETTLE_MS = 500L

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
    }

    /** Mark that we just sent an MFS pulse so stalk inference yields to controller updates. */
    fun markOurPulse(windowMs: Long = OUR_PULSE_WINDOW_MS) {
        ensureStarted()
        ourPulseUntilElapsed = System.currentTimeMillis() + windowMs
    }

    fun isWithinOurPulseWindow(nowElapsed: Long = System.currentTimeMillis()): Boolean =
        nowElapsed < ourPulseUntilElapsed

    fun clear(reason: String) {
        if (_kmh.value == null) return
        debug("clear reason=$reason was=${_kmh.value}")
        _kmh.value = null
        pendingNudgeKmh = null
    }

    fun remember(kmh: Int, reason: String) {
        ensureStarted()
        val normalized = normalizeAccCruiseTargetKmh(kmh)
        if (_kmh.value == normalized) return
        debug("set reason=$reason kmh=$normalized was=${_kmh.value}")
        _kmh.value = normalized
        pendingNudgeKmh = null
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
     * Pure decision for stalk-driven enter Active (outside our pulse window).
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

    fun shouldShowRememberedSetpoint(ccsStatus: Int?): Boolean =
        AccCruiseDomain.isCcsEngaged(ccsStatus) && hasSetpoint()

    private fun onStatus(status: Int?) {
        val previous = lastStatus
        lastStatus = status
        when {
            status == null -> {
                // Unbind / unknown — drop session memory.
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
            return
        }
        if (isWithinOurPulseWindow()) return
        val remembered = _kmh.value ?: return
        val rounded = roundSpeedKmh(speed) ?: return
        val delta = abs(rounded - remembered)
        when {
            delta == 0 -> {
                pendingNudgeKmh = null
            }
            delta == 1 -> {
                val now = System.currentTimeMillis()
                if (pendingNudgeKmh != rounded) {
                    pendingNudgeKmh = rounded
                    pendingNudgeSinceElapsed = now
                } else if (now - pendingNudgeSinceElapsed >= NUDGE_SETTLE_MS) {
                    remember(rounded, "stalk_nudge")
                }
            }
            else -> {
                // Gas override or large change — do not adopt as setpoint.
                pendingNudgeKmh = null
            }
        }
    }

    private fun roundSpeedKmh(speed: Float?): Int? {
        if (speed == null || !speed.isFinite()) return null
        return speed.roundToInt().coerceIn(0, 300)
    }

    private fun debug(message: String) {
        MbCanDiagnostics.log("DEBUG", LOG_TAG, message)
    }

    sealed class StalkEnterActiveDecision {
        data object Keep : StalkEnterActiveDecision()
        data class Capture(val kmh: Int) : StalkEnterActiveDecision()
    }
}
