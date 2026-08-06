package vad.dashing.tbox.location

import kotlin.math.abs

/**
 * Continuous yaw bias / scale refinement from truthful GNSS (enhancement mock modes).
 *
 * Runs in CONSTANT / ALWAYS / WHEN_FIX_LOST while GNSS is trustworthy.
 *
 * - **Bias:** EMA of residual debiased yaw on straight segments (stable GNSS course).
 * - **Scale:** EMA of GNSS↔gyro turn ratio (same sign convention as [DriveCalibrationMath]).
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

    const val PERSIST_MIN_INTERVAL_MS = 30_000L
    const val PERSIST_BIAS_DELTA = 0.02f
    const val PERSIST_SCALE_REL = 0.01f

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

    /**
     * Next bias (°/s): move toward raw yaw residual on straight (EMA of debiased → 0).
     * [debiasedYaw] = raw − currentBias.
     */
    fun nextBiasDegPerSec(currentBias: Float, debiasedYaw: Float): Float {
        if (!currentBias.isFinite() || !debiasedYaw.isFinite()) return currentBias
        val step = (BIAS_EMA_ALPHA * debiasedYaw).coerceIn(-BIAS_MAX_STEP, BIAS_MAX_STEP)
        return (currentBias + step).coerceIn(-BIAS_ABS_MAX, BIAS_ABS_MAX)
    }

    /**
     * Scale candidate for yawSign = +1 convention:
     * gnssNoseDelta ≈ −scale × gyroIntegralDebiased → scale = −gnss/gyro.
     * For store [yawSign] = −1, gyro was collected as debiased (pre-sign);
     * candidate uses the same formula as [DriveCalibrationMath.estimateYawScaleAndSign] pos branch,
     * then caller multiplies by existing sign only when applying relative correction.
     */
    fun scaleCandidate(gyroIntegralDebiased: Float, gnssNoseDeltaDeg: Float): Float? {
        if (abs(gyroIntegralDebiased) < 1f) return null
        if (abs(gnssNoseDeltaDeg) < TURN_MIN_ABS_DEG * 0.45f) return null
        val sp = -gnssNoseDeltaDeg / gyroIntegralDebiased
        if (sp !in SCALE_CANDIDATE_MIN..SCALE_CANDIDATE_MAX) return null
        return sp
    }

    /** Scale for current [yawSign]: if sign is −1, positive candidate maps to flipped integral. */
    fun scaleCandidateForSign(
        gyroIntegralDebiased: Float,
        gnssNoseDeltaDeg: Float,
        yawSign: Int,
    ): Float? {
        val sign = if (yawSign < 0) -1 else 1
        // bearing Δ ≈ −(debiased · scale · sign) · … → scale = −gnss / (gyro · sign)
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
        val base = lastPersisted.takeIf { it.isFinite() && it > 0f } ?: 1f
        return abs(current - lastPersisted) / base >= PERSIST_SCALE_REL
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

    private var lastPersistedBias: Float = Float.NaN
    private var lastPersistedScale: Float = Float.NaN
    private var lastPersistBiasElapsedMs: Long = 0L
    private var lastPersistScaleElapsedMs: Long = 0L

    private var lastDebug = OnlineYawCalibDebug()

    fun lastDebug(): OnlineYawCalibDebug = lastDebug

    fun reset() {
        prevCourseDeg = null
        prevCourseElapsedMs = 0L
        straightSinceElapsedMs = 0L
        abortTurn()
        lastDebug = OnlineYawCalibDebug()
    }

    /**
     * @param rawYawDegPerSec raw HU gyro yaw (°/s, left +)
     * @param gnssNoseCourseDeg GNSS course converted to vehicle nose (reverse already applied)
     * @param speedKmh CAN (or mock) speed
     * @param accuracyM horizontal accuracy, or null if unknown
     * @param reverse when true, skip updates (segment noise)
     * @param gnssTruthful junk/truth gate
     */
    fun onTick(
        elapsedMs: Long,
        rawYawDegPerSec: Float?,
        gnssNoseCourseDeg: Float?,
        speedKmh: Float,
        accuracyM: Float?,
        reverse: Boolean,
        gnssTruthful: Boolean,
        currentBias: Float = GyroBiasStore.offsets.yawDegPerSec,
        currentScale: Float = DriveCalibrationStore.offsets.yawScale,
        currentYawSign: Int = DriveCalibrationStore.offsets.yawSign,
    ): OnlineYawCalibTickResult {
        if (lastPersistedBias.isNaN()) lastPersistedBias = currentBias
        if (lastPersistedScale.isNaN()) lastPersistedScale = currentScale

        if (!gnssTruthful || reverse || rawYawDegPerSec == null || !rawYawDegPerSec.isFinite() ||
            gnssNoseCourseDeg == null || !gnssNoseCourseDeg.isFinite() || gnssNoseCourseDeg == 0f
        ) {
            abortTurn()
            straightSinceElapsedMs = 0L
            prevCourseDeg = null
            lastDebug = OnlineYawCalibDebug(phase = OnlineYawCalibPhase.IDLE)
            return OnlineYawCalibTickResult(debug = lastDebug)
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

        // Prefer turn over straight when yaw is clearly turning.
        if (OnlineYawCalibMath.isTurnCandidate(speedKmh, accuracyM, yawAbs)) {
            straightSinceElapsedMs = 0L
            return onTurnTick(
                elapsedMs = elapsedMs,
                debiasedYaw = debiased,
                gnssNoseCourseDeg = gnssNoseCourseDeg,
                speedKmh = speedKmh,
                currentScale = currentScale,
                currentYawSign = currentYawSign,
            )
        }

        abortTurn()

        if (rateAbs != null &&
            OnlineYawCalibMath.isStraightCandidate(speedKmh, accuracyM, yawAbs, rateAbs)
        ) {
            return onStraightTick(
                elapsedMs = elapsedMs,
                debiasedYaw = debiased,
                currentBias = currentBias,
            )
        }

        straightSinceElapsedMs = 0L
        lastDebug = OnlineYawCalibDebug(phase = OnlineYawCalibPhase.IDLE)
        return OnlineYawCalibTickResult(debug = lastDebug)
    }

    private fun onStraightTick(
        elapsedMs: Long,
        debiasedYaw: Float,
        currentBias: Float,
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
        // One step per hold window; reset hold so α does not fire every tick.
        straightSinceElapsedMs = elapsedMs
        val nextBias = OnlineYawCalibMath.nextBiasDegPerSec(currentBias, debiasedYaw)
        val biasChanged = abs(nextBias - currentBias) > 1e-6f
        if (biasChanged) {
            GyroBiasStore.update(
                GyroBiasStore.offsets.copy(yawDegPerSec = nextBias),
            )
        }
        val persistBias = biasChanged &&
            OnlineYawCalibMath.shouldPersistBias(
                lastPersistedBias,
                nextBias,
                lastPersistBiasElapsedMs,
                elapsedMs,
            )
        if (persistBias) {
            lastPersistedBias = nextBias
            lastPersistBiasElapsedMs = elapsedMs
        }
        lastDebug = OnlineYawCalibDebug(
            phase = OnlineYawCalibPhase.STRAIGHT,
            straightHoldMs = hold,
            lastBiasStep = nextBias - currentBias,
        )
        return OnlineYawCalibTickResult(
            biasChanged = biasChanged,
            persistBias = persistBias,
            debug = lastDebug,
        )
    }

    private fun onTurnTick(
        elapsedMs: Long,
        debiasedYaw: Float,
        gnssNoseCourseDeg: Float,
        speedKmh: Float,
        currentScale: Float,
        currentYawSign: Int,
    ): OnlineYawCalibTickResult {
        if (!turnActive) {
            turnActive = true
            turnStartElapsedMs = elapsedMs
            turnStartCourseDeg = gnssNoseCourseDeg
            turnGyroIntegral = 0f
            turnLastElapsedMs = elapsedMs
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
        abortTurn()
        if (candidate == null) {
            lastDebug = OnlineYawCalibDebug(phase = OnlineYawCalibPhase.IDLE)
            return OnlineYawCalibTickResult(debug = lastDebug)
        }
        val nextScale = OnlineYawCalibMath.nextScale(currentScale, candidate)
        val scaleChanged = abs(nextScale - currentScale) > 1e-5f
        if (scaleChanged) {
            val off = DriveCalibrationStore.offsets
            DriveCalibrationStore.update(
                off.copy(
                    yawScale = nextScale,
                    yawEstimated = true,
                    // Keep calibratedAtEpochMs — online tweak is not a full drive Save.
                ),
            )
        }
        val persistScale = scaleChanged &&
            OnlineYawCalibMath.shouldPersistScale(
                lastPersistedScale,
                nextScale,
                lastPersistScaleElapsedMs,
                elapsedMs,
            )
        if (persistScale) {
            lastPersistedScale = nextScale
            lastPersistScaleElapsedMs = elapsedMs
        }
        lastDebug = OnlineYawCalibDebug(
            phase = OnlineYawCalibPhase.TURN,
            turnGyroAbsDeg = gyroAbs,
            lastScaleCandidate = candidate,
        )
        return OnlineYawCalibTickResult(
            scaleChanged = scaleChanged,
            persistScale = persistScale,
            debug = lastDebug,
        )
    }

    private fun abortTurn() {
        turnActive = false
        turnStartElapsedMs = 0L
        turnGyroIntegral = 0f
        turnLastElapsedMs = 0L
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
