package vad.dashing.tbox.location

import kotlin.math.abs

/**
 * Continuous yaw bias / dual L·R scale refinement from truthful GNSS (enhancement mock modes).
 *
 * Runs in CONSTANT / ALWAYS / WHEN_FIX_LOST while GNSS is trustworthy.
 *
 * - **Bias:** EMA of residual debiased yaw on straight segments (stable GNSS course).
 *   Faster EMA when |gyroTemp − yawCalibTempC| is large (temperature drift).
 * - **Scale:** EMA of GNSS↔gyro turn ratio per turn side (left = ∫gyro ≥ 0, right < 0).
 *   Skipped when temperature spans too much during the turn segment.
 *
 * Does **not** use lateral accel. Does **not** clear [GeoCalibrationState.needsCalibration]
 * or call [GeoCalibrationState.markCalibrated].
 */
object OnlineYawCalibMath {
    val MIN_SPEED_KMH: Float = DriveCalibrationMath.MIN_SPEED_KMH
    val MAX_ACCURACY_M: Float = DriveCalibrationMath.MAX_HORIZONTAL_ACCURACY_M

    /** |debiased yaw| below this (°/s) while course-stable → straight candidate. */
    const val STRAIGHT_MAX_YAW_ABS = 1.0f

    /** Max |GNSS course rate| (°/s) to treat as straight. */
    const val STRAIGHT_MAX_COURSE_RATE_DEG_S = 1.5f

    /** Hold straight conditions this long before applying a bias step. */
    const val STRAIGHT_MIN_HOLD_MS = 3_000L

    /** EMA weight per bias step after [STRAIGHT_MIN_HOLD_MS]. */
    const val BIAS_EMA_ALPHA = 0.08f

    /** Faster bias EMA when far from calib temperature. */
    const val BIAS_EMA_ALPHA_TEMP_DRIFT = 0.16f

    /** |ΔT| (°C) vs [GyroBiasOffsets.yawCalibTempC] to use faster bias EMA. */
    const val BIAS_TEMP_FAST_TRACK_C = 5f

    /** Clamp |Δbias| per step (°/s). */
    const val BIAS_MAX_STEP = 0.05f

    /** Absolute bias clamp (°/s). */
    const val BIAS_ABS_MAX = 5f

    /** Start / continue a turn segment when |debiased yaw| ≥ this. */
    const val TURN_MIN_YAW_ABS = 1.5f

    val TURN_MIN_ABS_DEG: Float = DriveCalibrationMath.MIN_TURN_ABS_DEG
    val TURN_MAX_MS: Long = DriveCalibrationMath.YAW_SEGMENT_MAX_MS

    const val SCALE_EMA_ALPHA = 0.15f
    const val SCALE_MIN = 0.5f
    const val SCALE_MAX = 1.8f

    /** Accept segment scale only in this band (matches batch calib). */
    const val SCALE_CANDIDATE_MIN = 0.5f
    const val SCALE_CANDIDATE_MAX = 1.8f

    /**
     * Reject scale update if |T_end − T_start| on the turn exceeds this (°C).
     * Protects against thermal transient during a long arc.
     */
    const val SCALE_MAX_TEMP_SPAN_C = 1.5f

    const val PERSIST_MIN_INTERVAL_MS = 10_000L
    const val PERSIST_BIAS_DELTA = 0.015f
    const val PERSIST_SCALE_REL = 0.008f

    fun courseRateDegPerSec(
        prevCourseDeg: Float,
        prevElapsedMs: Long,
        courseDeg: Float,
        elapsedMs: Long,
    ): Float? {
        val dtMs = elapsedMs - prevElapsedMs
        if (dtMs <= 0L || dtMs > 2_500L) return null
        val d = DriveCalibrationMath.wrapDeltaDeg(prevCourseDeg, courseDeg)
        return d / (dtMs / 1000f)
    }

    fun isStraightCandidate(
        speedKmh: Float,
        accuracyM: Float?,
        debiasedYawAbs: Float,
        courseRateAbs: Float,
    ): Boolean {
        if (!speedKmh.isFinite() || speedKmh < MIN_SPEED_KMH) return false
        if (accuracyM != null && accuracyM.isFinite() && accuracyM > MAX_ACCURACY_M) return false
        if (!debiasedYawAbs.isFinite() || debiasedYawAbs > STRAIGHT_MAX_YAW_ABS) return false
        if (!courseRateAbs.isFinite() || courseRateAbs > STRAIGHT_MAX_COURSE_RATE_DEG_S) {
            return false
        }
        return true
    }

    fun isTurnCandidate(speedKmh: Float, accuracyM: Float?, debiasedYawAbs: Float): Boolean {
        if (!speedKmh.isFinite() || speedKmh < MIN_SPEED_KMH) return false
        if (accuracyM != null && accuracyM.isFinite() && accuracyM > MAX_ACCURACY_M) return false
        return debiasedYawAbs.isFinite() && debiasedYawAbs >= TURN_MIN_YAW_ABS
    }

    fun biasAlpha(gyroTempC: Float?, calibTempC: Float?): Float {
        if (gyroTempC == null || !gyroTempC.isFinite() ||
            calibTempC == null || !calibTempC.isFinite()
        ) {
            return BIAS_EMA_ALPHA
        }
        return if (abs(gyroTempC - calibTempC) >= BIAS_TEMP_FAST_TRACK_C) {
            BIAS_EMA_ALPHA_TEMP_DRIFT
        } else {
            BIAS_EMA_ALPHA
        }
    }

    /**
     * Next bias (°/s): move toward raw yaw residual on straight (EMA of debiased → 0).
     * [debiasedYaw] = raw − currentBias.
     */
    fun nextBiasDegPerSec(
        currentBias: Float,
        debiasedYaw: Float,
        alpha: Float = BIAS_EMA_ALPHA,
    ): Float {
        if (!currentBias.isFinite() || !debiasedYaw.isFinite()) return currentBias
        val a = alpha.coerceIn(0.01f, 0.5f)
        val step = (a * debiasedYaw).coerceIn(-BIAS_MAX_STEP, BIAS_MAX_STEP)
        return (currentBias + step).coerceIn(-BIAS_ABS_MAX, BIAS_ABS_MAX)
    }

    /** Scale for current [yawSign] from debiased gyro integral vs GNSS nose Δ. */
    fun scaleCandidateForSign(
        gyroIntegralDebiased: Float,
        gnssNoseDeltaDeg: Float,
        yawSign: Int,
    ): Float? {
        val sign = if (yawSign < 0) -1 else 1
        if (abs(gyroIntegralDebiased) < 1f) return null
        if (abs(gnssNoseDeltaDeg) < TURN_MIN_ABS_DEG * 0.45f) return null
        val s = -gnssNoseDeltaDeg / (gyroIntegralDebiased * sign)
        if (s !in SCALE_CANDIDATE_MIN..SCALE_CANDIDATE_MAX) return null
        return s
    }

    fun nextScale(currentScale: Float, candidate: Float): Float {
        val cur = currentScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        if (!candidate.isFinite()) return cur
        val blended = cur * (1f - SCALE_EMA_ALPHA) + candidate * SCALE_EMA_ALPHA
        return blended.coerceIn(SCALE_MIN, SCALE_MAX)
    }

    /** True when turn temperature span is too large to trust a scale sample. */
    fun scaleBlockedByTemp(tempStartC: Float?, tempEndC: Float?): Boolean {
        if (tempStartC == null || !tempStartC.isFinite()) return false
        if (tempEndC == null || !tempEndC.isFinite()) return false
        return abs(tempEndC - tempStartC) > SCALE_MAX_TEMP_SPAN_C
    }

    fun shouldPersistBias(
        lastPersisted: Float,
        current: Float,
        lastPersistElapsedMs: Long,
        nowElapsedMs: Long,
    ): Boolean {
        if (nowElapsedMs - lastPersistElapsedMs < PERSIST_MIN_INTERVAL_MS &&
            lastPersistElapsedMs > 0L
        ) {
            return false
        }
        return abs(current - lastPersisted) >= PERSIST_BIAS_DELTA
    }

    fun shouldPersistScale(
        lastPersistedLeft: Float,
        lastPersistedRight: Float,
        currentLeft: Float,
        currentRight: Float,
        lastPersistElapsedMs: Long,
        nowElapsedMs: Long,
    ): Boolean {
        if (nowElapsedMs - lastPersistElapsedMs < PERSIST_MIN_INTERVAL_MS &&
            lastPersistElapsedMs > 0L
        ) {
            return false
        }
        fun rel(last: Float, cur: Float): Boolean {
            val base = last.takeIf { it.isFinite() && it > 0f } ?: 1f
            return abs(cur - last) / base >= PERSIST_SCALE_REL
        }
        return rel(lastPersistedLeft, currentLeft) || rel(lastPersistedRight, currentRight)
    }
}

enum class OnlineYawCalibPhase {
    IDLE,
    STRAIGHT,
    TURN,
}

data class OnlineYawCalibDebug(
    val phase: OnlineYawCalibPhase = OnlineYawCalibPhase.IDLE,
    val straightHoldMs: Long = 0L,
    val turnGyroAbsDeg: Float = 0f,
    val lastBiasStep: Float? = null,
    val lastScaleCandidate: Float? = null,
    /** "L" / "R" when last scale candidate applied to one side. */
    val lastScaleSide: String? = null,
)

data class OnlineYawCalibTickResult(
    val biasChanged: Boolean = false,
    val scaleChanged: Boolean = false,
    val persistBias: Boolean = false,
    val persistScale: Boolean = false,
    val debug: OnlineYawCalibDebug = OnlineYawCalibDebug(),
)

/**
 * Stateful online estimator. One instance per [MockLocationJob] (or shared singleton reset on stop).
 */
class OnlineYawCalibEstimator {
    private var prevCourseDeg: Float? = null
    private var prevCourseElapsedMs: Long = 0L
    private var straightSinceElapsedMs: Long = 0L

    private var turnActive = false
    private var turnStartElapsedMs: Long = 0L
    private var turnStartCourseDeg: Float = 0f
    private var turnGyroIntegral: Float = 0f
    private var turnLastElapsedMs: Long = 0L
    private var turnStartTempC: Float? = null

    private var lastPersistedBias: Float = Float.NaN
    private var lastPersistedScaleLeft: Float = Float.NaN
    private var lastPersistedScaleRight: Float = Float.NaN
    private var lastPersistBiasElapsedMs: Long = 0L
    private var lastPersistScaleElapsedMs: Long = 0L
    private var dirtyBias: Boolean = false
    private var dirtyScale: Boolean = false

    private var lastDebug = OnlineYawCalibDebug()

    fun lastDebug(): OnlineYawCalibDebug = lastDebug

    fun hasDirtyPersist(): Boolean = dirtyBias || dirtyScale

    /**
     * Decide whether to persist current store values (debounce).
     * [force] kept for unit tests; production uses periodic debounce only.
     */
    fun evaluatePersist(
        elapsedMs: Long,
        currentBias: Float = GyroBiasStore.offsets.yawDegPerSec,
        currentScaleLeft: Float = DriveCalibrationStore.offsets.yawScaleLeft,
        currentScaleRight: Float = DriveCalibrationStore.offsets.yawScaleRight,
        force: Boolean = false,
    ): OnlineYawCalibTickResult {
        if (lastPersistedBias.isNaN()) lastPersistedBias = currentBias
        if (lastPersistedScaleLeft.isNaN()) lastPersistedScaleLeft = currentScaleLeft
        if (lastPersistedScaleRight.isNaN()) lastPersistedScaleRight = currentScaleRight
        var persistBias = false
        var persistScale = false
        if (dirtyBias) {
            persistBias = force ||
                OnlineYawCalibMath.shouldPersistBias(
                    lastPersistedBias,
                    currentBias,
                    lastPersistBiasElapsedMs,
                    elapsedMs,
                )
            if (persistBias) {
                lastPersistedBias = currentBias
                lastPersistBiasElapsedMs = elapsedMs
                dirtyBias = false
            }
        }
        if (dirtyScale) {
            persistScale = force ||
                OnlineYawCalibMath.shouldPersistScale(
                    lastPersistedScaleLeft,
                    lastPersistedScaleRight,
                    currentScaleLeft,
                    currentScaleRight,
                    lastPersistScaleElapsedMs,
                    elapsedMs,
                )
            if (persistScale) {
                lastPersistedScaleLeft = currentScaleLeft
                lastPersistedScaleRight = currentScaleRight
                lastPersistScaleElapsedMs = elapsedMs
                dirtyScale = false
            }
        }
        return OnlineYawCalibTickResult(
            persistBias = persistBias,
            persistScale = persistScale,
            debug = lastDebug,
        )
    }

    fun reset() {
        prevCourseDeg = null
        prevCourseElapsedMs = 0L
        straightSinceElapsedMs = 0L
        abortTurn()
        lastDebug = OnlineYawCalibDebug()
        // Keep dirty* / lastPersisted* so a later debounce tick can still persist.
    }

    fun onTick(
        elapsedMs: Long,
        rawYawDegPerSec: Float?,
        gnssNoseCourseDeg: Float?,
        speedKmh: Float,
        accuracyM: Float?,
        reverse: Boolean,
        gnssTruthful: Boolean,
        gyroTempC: Float? = null,
        currentBias: Float = GyroBiasStore.offsets.yawDegPerSec,
        currentScaleLeft: Float = DriveCalibrationStore.offsets.yawScaleLeft,
        currentScaleRight: Float = DriveCalibrationStore.offsets.yawScaleRight,
        currentYawSign: Int = DriveCalibrationStore.offsets.yawSign,
        yawCalibTempC: Float? = GyroBiasStore.offsets.yawCalibTempC,
    ): OnlineYawCalibTickResult {
        if (lastPersistedBias.isNaN()) lastPersistedBias = currentBias
        if (lastPersistedScaleLeft.isNaN()) lastPersistedScaleLeft = currentScaleLeft
        if (lastPersistedScaleRight.isNaN()) lastPersistedScaleRight = currentScaleRight

        if (!gnssTruthful || reverse || rawYawDegPerSec == null || !rawYawDegPerSec.isFinite() ||
            gnssNoseCourseDeg == null || !gnssNoseCourseDeg.isFinite() || gnssNoseCourseDeg == 0f
        ) {
            abortTurn()
            straightSinceElapsedMs = 0L
            prevCourseDeg = null
            lastDebug = OnlineYawCalibDebug(phase = OnlineYawCalibPhase.IDLE)
            return withPersist(elapsedMs, OnlineYawCalibTickResult(debug = lastDebug))
        }

        val debiased = rawYawDegPerSec - currentBias
        val courseRate = prevCourseDeg?.let {
            OnlineYawCalibMath.courseRateDegPerSec(
                it,
                prevCourseElapsedMs,
                gnssNoseCourseDeg,
                elapsedMs,
            )
        }
        prevCourseDeg = gnssNoseCourseDeg
        prevCourseElapsedMs = elapsedMs

        val yawAbs = abs(debiased)
        val rateAbs = courseRate?.let { abs(it) }

        if (OnlineYawCalibMath.isTurnCandidate(speedKmh, accuracyM, yawAbs)) {
            straightSinceElapsedMs = 0L
            return withPersist(
                elapsedMs,
                onTurnTick(
                    elapsedMs = elapsedMs,
                    debiasedYaw = debiased,
                    gnssNoseCourseDeg = gnssNoseCourseDeg,
                    speedKmh = speedKmh,
                    gyroTempC = gyroTempC,
                    currentScaleLeft = currentScaleLeft,
                    currentScaleRight = currentScaleRight,
                    currentYawSign = currentYawSign,
                ),
            )
        }

        abortTurn()

        if (rateAbs != null &&
            OnlineYawCalibMath.isStraightCandidate(speedKmh, accuracyM, yawAbs, rateAbs)
        ) {
            return withPersist(
                elapsedMs,
                onStraightTick(
                    elapsedMs = elapsedMs,
                    debiasedYaw = debiased,
                    currentBias = currentBias,
                    gyroTempC = gyroTempC,
                    yawCalibTempC = yawCalibTempC,
                ),
            )
        }

        straightSinceElapsedMs = 0L
        lastDebug = OnlineYawCalibDebug(phase = OnlineYawCalibPhase.IDLE)
        return withPersist(elapsedMs, OnlineYawCalibTickResult(debug = lastDebug))
    }

    private fun withPersist(
        elapsedMs: Long,
        result: OnlineYawCalibTickResult,
    ): OnlineYawCalibTickResult {
        val p = evaluatePersist(elapsedMs)
        return result.copy(
            persistBias = p.persistBias,
            persistScale = p.persistScale,
        )
    }

    private fun onStraightTick(
        elapsedMs: Long,
        debiasedYaw: Float,
        currentBias: Float,
        gyroTempC: Float?,
        yawCalibTempC: Float?,
    ): OnlineYawCalibTickResult {
        if (straightSinceElapsedMs == 0L) {
            straightSinceElapsedMs = elapsedMs
        }
        val hold = elapsedMs - straightSinceElapsedMs
        if (hold < OnlineYawCalibMath.STRAIGHT_MIN_HOLD_MS) {
            lastDebug = OnlineYawCalibDebug(
                phase = OnlineYawCalibPhase.STRAIGHT,
                straightHoldMs = hold,
            )
            return OnlineYawCalibTickResult(debug = lastDebug)
        }
        straightSinceElapsedMs = elapsedMs
        val alpha = OnlineYawCalibMath.biasAlpha(gyroTempC, yawCalibTempC)
        val nextBias = OnlineYawCalibMath.nextBiasDegPerSec(currentBias, debiasedYaw, alpha)
        val biasChanged = abs(nextBias - currentBias) > 1e-6f
        if (biasChanged) {
            GyroBiasStore.update(
                GyroBiasStore.offsets.copy(
                    yawDegPerSec = nextBias,
                    yawCalibTempC = gyroTempC?.takeIf { it.isFinite() }
                        ?: GyroBiasStore.offsets.yawCalibTempC,
                ),
            )
            dirtyBias = true
        }
        lastDebug = OnlineYawCalibDebug(
            phase = OnlineYawCalibPhase.STRAIGHT,
            straightHoldMs = hold,
            lastBiasStep = nextBias - currentBias,
        )
        return OnlineYawCalibTickResult(
            biasChanged = biasChanged,
            debug = lastDebug,
        )
    }

    private fun onTurnTick(
        elapsedMs: Long,
        debiasedYaw: Float,
        gnssNoseCourseDeg: Float,
        speedKmh: Float,
        gyroTempC: Float?,
        currentScaleLeft: Float,
        currentScaleRight: Float,
        currentYawSign: Int,
    ): OnlineYawCalibTickResult {
        if (!turnActive) {
            turnActive = true
            turnStartElapsedMs = elapsedMs
            turnStartCourseDeg = gnssNoseCourseDeg
            turnGyroIntegral = 0f
            turnLastElapsedMs = elapsedMs
            turnStartTempC = gyroTempC
            lastDebug = OnlineYawCalibDebug(
                phase = OnlineYawCalibPhase.TURN,
                turnGyroAbsDeg = 0f,
            )
            return OnlineYawCalibTickResult(debug = lastDebug)
        }

        val dtSec = ((elapsedMs - turnLastElapsedMs) / 1000f).coerceIn(0f, 0.5f)
        turnLastElapsedMs = elapsedMs
        if (dtSec > 0f) {
            turnGyroIntegral += debiasedYaw * dtSec
        }

        val age = elapsedMs - turnStartElapsedMs
        val gyroAbs = abs(turnGyroIntegral)
        if (age > OnlineYawCalibMath.TURN_MAX_MS) {
            abortTurn()
            lastDebug = OnlineYawCalibDebug(phase = OnlineYawCalibPhase.IDLE)
            return OnlineYawCalibTickResult(debug = lastDebug)
        }
        if (speedKmh < OnlineYawCalibMath.MIN_SPEED_KMH * 0.8f) {
            abortTurn()
            lastDebug = OnlineYawCalibDebug(phase = OnlineYawCalibPhase.IDLE)
            return OnlineYawCalibTickResult(debug = lastDebug)
        }

        if (gyroAbs < OnlineYawCalibMath.TURN_MIN_ABS_DEG) {
            lastDebug = OnlineYawCalibDebug(
                phase = OnlineYawCalibPhase.TURN,
                turnGyroAbsDeg = gyroAbs,
            )
            return OnlineYawCalibTickResult(debug = lastDebug)
        }

        val gnssDelta = DriveCalibrationMath.wrapDeltaDeg(turnStartCourseDeg, gnssNoseCourseDeg)
        val candidate = OnlineYawCalibMath.scaleCandidateForSign(
            gyroIntegralDebiased = turnGyroIntegral,
            gnssNoseDeltaDeg = gnssDelta,
            yawSign = currentYawSign,
        )
        val tempBlocked = OnlineYawCalibMath.scaleBlockedByTemp(turnStartTempC, gyroTempC)
        val sideLeft = turnGyroIntegral >= 0f
        abortTurn()
        if (candidate == null || tempBlocked) {
            lastDebug = OnlineYawCalibDebug(phase = OnlineYawCalibPhase.IDLE)
            return OnlineYawCalibTickResult(debug = lastDebug)
        }
        val currentSide = if (sideLeft) currentScaleLeft else currentScaleRight
        val nextScale = OnlineYawCalibMath.nextScale(currentSide, candidate)
        val scaleChanged = abs(nextScale - currentSide) > 1e-5f
        if (scaleChanged) {
            val off = DriveCalibrationStore.offsets
            DriveCalibrationStore.update(
                off.copy(
                    yawScaleLeft = if (sideLeft) nextScale else off.yawScaleLeft,
                    yawScaleRight = if (sideLeft) off.yawScaleRight else nextScale,
                    yawEstimated = true,
                    calibratedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            dirtyScale = true
        }
        lastDebug = OnlineYawCalibDebug(
            phase = OnlineYawCalibPhase.TURN,
            turnGyroAbsDeg = gyroAbs,
            lastScaleCandidate = candidate,
            lastScaleSide = if (sideLeft) "L" else "R",
        )
        return OnlineYawCalibTickResult(
            scaleChanged = scaleChanged,
            debug = lastDebug,
        )
    }

    private fun abortTurn() {
        turnActive = false
        turnStartElapsedMs = 0L
        turnGyroIntegral = 0f
        turnLastElapsedMs = 0L
        turnStartTempC = null
    }
}

/**
 * Last online-calib tick for geo-debug.
 */
object OnlineYawCalibRuntimeDebug {
    @Volatile
    var snapshot: OnlineYawCalibDebug = OnlineYawCalibDebug()
        private set

    fun publish(debug: OnlineYawCalibDebug) {
        snapshot = debug
    }

    fun clear() {
        snapshot = OnlineYawCalibDebug()
    }
}
