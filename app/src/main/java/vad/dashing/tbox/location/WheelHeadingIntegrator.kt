package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.vehicle.WheelCounters
import vad.dashing.tbox.vehicle.WheelPulseOdometer
import kotlin.math.abs

data class WheelHeadingCalibration(
    /** Effective rear track width B (m). Default Jetour Dashing rear track ~1.60 m. */
    val trackWidthM: Float = WheelHeadingIntegrator.DEFAULT_TRACK_M,
    val calibratedAtEpochMs: Long = 0L,
    val arcCount: Int = 0,
)

object WheelHeadingCalibrationStore {
    private val _calibration = MutableStateFlow(WheelHeadingCalibration())
    val calibration: StateFlow<WheelHeadingCalibration> = _calibration.asStateFlow()

    val trackWidthM: Float
        get() = _calibration.value.trackWidthM
            .takeIf { it.isFinite() && it in WheelHeadingIntegrator.MIN_TRACK_M..WheelHeadingIntegrator.MAX_TRACK_M }
            ?: WheelHeadingIntegrator.DEFAULT_TRACK_M

    fun update(next: WheelHeadingCalibration) {
        val clamped = next.trackWidthM
            .takeIf { it.isFinite() }
            ?.coerceIn(WheelHeadingIntegrator.MIN_TRACK_M, WheelHeadingIntegrator.MAX_TRACK_M)
            ?: WheelHeadingIntegrator.DEFAULT_TRACK_M
        _calibration.value = next.copy(trackWidthM = clamped)
    }

    fun reset() {
        _calibration.value = WheelHeadingCalibration()
    }
}

/**
 * Rear-wheel differential heading integrator (`WHEELS` and `WHEELS_STEER`).
 *
 * Uses rear non-driven wheels (`LHR`, `RHR`):
 * `Δψ_rad = (Δs_RR − Δs_RL) / B`, where `Δs = Δpulses × k` and `B` is effective rear track width.
 *
 * Nav bearing convention matches [YawIntegrator] and [SteerHeadingIntegrator]:
 * left turn (`Δs_RR > Δs_RL` forward) → negative nav bearing delta;
 * right turn (`Δs_RL > Δs_RR` forward) → positive nav bearing delta.
 * Reverse gear inverts the sign of `Δψ`.
 */
object WheelHeadingIntegrator {
    /** Default Jetour Dashing rear track width (m). */
    const val DEFAULT_TRACK_M = 1.60f
    const val MIN_TRACK_M = 1.30f
    const val MAX_TRACK_M = 2.00f

    /** Fallback nominal m/pulse when [WheelPulseOdometer] is not yet calibrated. */
    const val DEFAULT_METERS_PER_PULSE = 0.0235f

    /** Below this speed (km/h), pulse quantization is noisy; [MockHeadingSource.WHEELS_STEER] blends to steer. */
    const val LOW_SPEED_THRESHOLD_KMH = 5f
    const val FULL_WHEELS_SPEED_KMH = 12f

    /** Max age of last wheel pulse sample before considering wheel heading silent (ms). */
    const val MAX_SAMPLE_AGE_MS = 2_500L

    /** Front vs rear L/R asymmetry divergence threshold for slip rejection. */
    const val SLIP_PAIR_ASYM_DIFF_THRESHOLD = 0.12f

    /** Minimum speed (km/h) for lateral acceleration (`ω_lat = a_lat / v`) cross-check. */
    const val YAS_MIN_SPEED_KMH = 20f

    /** Disagreement (°/s) between `ω_wheels` and `ω_lat` where trust starts decreasing. */
    const val YAS_DISAGREE_START_DEG_PER_SEC = 8f
    const val YAS_DISAGREE_MAX_DEG_PER_SEC = 25f

    /** Both rates above this (°/s) vote on the YAS sign. */
    private const val YAS_SIGN_VOTE_MIN_DEG_PER_SEC = 6f
    private const val YAS_SIGN_VOTES_KNOWN = 8
    private const val YAS_SIGN_VOTES_MAX = 20

    /** Online track calibration arc thresholds. */
    private const val CALIB_MIN_SPEED_KMH = 15f
    private const val CALIB_MIN_ARC_DEG = 18f
    private const val CALIB_MAX_ARC_DEG = 140f
    private const val CALIB_ALPHA = 0.20f

    data class DebugSnapshot(
        val lastDeltaDeg: Float = 0f,
        val lastOmegaWheelsDegPerSec: Float? = null,
        val lastOmegaLatDegPerSec: Float? = null,
        val lastLateralAccelMps2: Float? = null,
        val yasTrustWeight: Float = 1f,
        val slipRejected: Boolean = false,
        val trackWidthM: Float = DEFAULT_TRACK_M,
        val lastSampleElapsedMs: Long = 0L,
    )

    private val lock = Any()
    private var prevCounters: WheelCounters? = null
    /** dt anchor; [discardThrough] moves it forward. */
    private var lastSampleElapsedMs: Long = 0L
    /** Time of the last real wheel sample; drives [isFresh] and the gap check. */
    private var lastIngestElapsedMs: Long = 0L
    private var pendingDeltaDeg: Double = 0.0
    private var lastStepDeltaDeg: Float = 0f
    private var lastOmegaWheelsDegPerSec: Float? = null
    private var lastOmegaLatDegPerSec: Float? = null
    private var lastLateralAccelMps2: Float? = null
    private var lastYasTrustWeight: Float = 1f
    private var lastSlipRejected: Boolean = false
    private var yasSignVotes: Int = 0

    // Online arc calibration state for track B
    private var arcWheelDiffM: Double = 0.0
    private var arcStartGnssCourseDeg: Float? = null
    private var arcLastGnssCourseDeg: Float? = null
    private var arcAccumGnssDeltaDeg: Float = 0f
    private var arcHadSlipOrReverse: Boolean = false

    fun pendingDeltaDeg(): Float = synchronized(lock) { pendingDeltaDeg.toFloat() }

    fun lastSampleElapsedMs(): Long = synchronized(lock) { lastIngestElapsedMs }

    fun isFresh(nowElapsedMs: Long): Boolean = synchronized(lock) {
        nowElapsedMs > 0L &&
            lastIngestElapsedMs > 0L &&
            nowElapsedMs - lastIngestElapsedMs <= MAX_SAMPLE_AGE_MS
    }

    fun peekDebugSnapshot(): DebugSnapshot = synchronized(lock) {
        DebugSnapshot(
            lastDeltaDeg = lastStepDeltaDeg,
            lastOmegaWheelsDegPerSec = lastOmegaWheelsDegPerSec,
            lastOmegaLatDegPerSec = lastOmegaLatDegPerSec,
            lastLateralAccelMps2 = lastLateralAccelMps2,
            yasTrustWeight = lastYasTrustWeight,
            slipRejected = lastSlipRejected,
            trackWidthM = WheelHeadingCalibrationStore.trackWidthM,
            lastSampleElapsedMs = lastIngestElapsedMs,
        )
    }

    fun reset() {
        synchronized(lock) {
            prevCounters = null
            lastSampleElapsedMs = 0L
            lastIngestElapsedMs = 0L
            pendingDeltaDeg = 0.0
            lastStepDeltaDeg = 0f
            lastOmegaWheelsDegPerSec = null
            lastOmegaLatDegPerSec = null
            lastLateralAccelMps2 = null
            lastYasTrustWeight = 1f
            lastSlipRejected = false
            yasSignVotes = 0
            resetArcLocked()
        }
    }

    fun onLateralAcceleration(latAccelMps2: Float?) {
        synchronized(lock) {
            lastLateralAccelMps2 = latAccelMps2?.takeIf { it.isFinite() }
        }
    }

    /**
     * Ingest one wheel-pulse sample.
     *
     * @param counters 4-wheel pulse counters (13-bit wrap).
     * @param kMetersPerPulse calibrated m/pulse from [WheelPulseOdometer], or `<= 0` for nominal.
     * @param reverse true when reverse gear is engaged (`considerReverse`).
     * @param steerDeg current steering wheel angle (°) for slip/quantization ceiling.
     * @param speedKmh current vehicle speed (km/h).
     * @param lateralAccelMps2 current YAS lateral acceleration (m/s²); null when no fresh value.
     * @param nowElapsedMs monotonic timestamp (`SystemClock.elapsedRealtime()`).
     */
    fun onWheelSample(
        counters: WheelCounters,
        kMetersPerPulse: Float,
        reverse: Boolean,
        steerDeg: Float?,
        speedKmh: Float?,
        lateralAccelMps2: Float? = null,
        nowElapsedMs: Long,
    ) {
        if (nowElapsedMs <= 0L) return
        synchronized(lock) {
            // The caller passes the current value; keeping an old one would cross-check
            // the wheels against a turn that ended when the source went silent.
            lastLateralAccelMps2 = lateralAccelMps2?.takeIf { it.isFinite() }
            val prev = prevCounters
            val prevAt = lastSampleElapsedMs
            val prevIngestAt = lastIngestElapsedMs
            prevCounters = counters
            lastSampleElapsedMs = nowElapsedMs
            lastIngestElapsedMs = nowElapsedMs
            if (prev == null || prevAt <= 0L || nowElapsedMs < prevAt) return
            // After a silent gap (collection stopped, standstill, CAN dropout) the counter
            // delta spans an unknown path: rebase instead of integrating it as one step.
            if (prevIngestAt <= 0L || nowElapsedMs - prevIngestAt > MAX_SAMPLE_AGE_MS) {
                arcHadSlipOrReverse = true
                return
            }

            val dLhf = WheelPulseOdometer.forwardDelta(prev.lhf, counters.lhf)
            val dRhf = WheelPulseOdometer.forwardDelta(prev.rhf, counters.rhf)
            val dLhr = WheelPulseOdometer.forwardDelta(prev.lhr, counters.lhr)
            val dRhr = WheelPulseOdometer.forwardDelta(prev.rhr, counters.rhr)
            if (dLhf == 0 && dRhf == 0 && dLhr == 0 && dRhr == 0) return

            val asymFront = WheelPulseOdometer.asymmetryRatio(dLhf, dRhf)
            val asymRear = WheelPulseOdometer.asymmetryRatio(dLhr, dRhr)
            val meanRear = (dLhr + dRhr) * 0.5f
            val slip = meanRear >= 4f && abs(asymFront - asymRear) > SLIP_PAIR_ASYM_DIFF_THRESHOLD
            lastSlipRejected = slip
            if (slip) {
                arcHadSlipOrReverse = true
                return
            }

            val k = kMetersPerPulse
                .takeIf { it.isFinite() && it in WheelPulseOdometer.MIN_METERS_PER_PULSE..WheelPulseOdometer.MAX_METERS_PER_PULSE }
                ?: DEFAULT_METERS_PER_PULSE
            val trackB = WheelHeadingCalibrationStore.trackWidthM

            // Rear wheel path difference: Δs_RR - Δs_RL (m)
            val diffRearM = (dRhr - dLhr).toDouble() * k.toDouble()
            val sign = if (reverse) -1.0 else 1.0
            val rawYawRad = sign * (diffRearM / trackB.toDouble())
            var rawNavDeltaDeg = (-Math.toDegrees(rawYawRad)).toFloat()

            // Cap single-sample quantization jump by steering bicycle ceiling when available
            val meanRearM = meanRear * k
            if (meanRearM > 0f) {
                val maxBicycleDeg = maxKinematicTurnDeg(meanRearM.toDouble(), steerDeg)
                rawNavDeltaDeg = rawNavDeltaDeg.coerceIn(-maxBicycleDeg, maxBicycleDeg)
            }

            val dtSec = ((nowElapsedMs - prevAt) / 1000.0).coerceIn(0.02, 2.0)
            val omegaWheels = (-rawNavDeltaDeg / dtSec.toFloat())
            lastOmegaWheelsDegPerSec = omegaWheels

            // YAS lateral acceleration confirmation at v > 20 km/h
            val spd = speedKmh?.takeIf { it.isFinite() && it >= 0f } ?: 0f
            val latAcc = lastLateralAccelMps2
            if (spd >= YAS_MIN_SPEED_KMH && latAcc != null && latAcc.isFinite()) {
                val vMps = (spd / 3.6f).coerceAtLeast(1f)
                val omegaLat = Math.toDegrees((latAcc / vMps).toDouble()).toFloat()
                lastOmegaLatDegPerSec = omegaLat
                val diff = yasDisagreementLocked(omegaWheels, omegaLat)
                lastYasTrustWeight = when {
                    diff <= YAS_DISAGREE_START_DEG_PER_SEC -> 1f
                    diff >= YAS_DISAGREE_MAX_DEG_PER_SEC -> 0.35f
                    else -> {
                        val t = (diff - YAS_DISAGREE_START_DEG_PER_SEC) /
                            (YAS_DISAGREE_MAX_DEG_PER_SEC - YAS_DISAGREE_START_DEG_PER_SEC)
                        1f - 0.65f * t
                    }
                }
            } else {
                lastOmegaLatDegPerSec = null
                lastYasTrustWeight = 1f
            }

            pendingDeltaDeg += rawNavDeltaDeg.toDouble()
            if (!reverse && spd >= CALIB_MIN_SPEED_KMH) {
                // Nav bearing convention: right turn +, so (s_RL - s_RR) > 0 on right turn
                arcWheelDiffM += (dLhr - dRhr).toDouble() * k.toDouble()
            } else if (reverse) {
                arcHadSlipOrReverse = true
            }
        }
    }

    /**
     * Feed truthful GNSS course on forward motion to auto-calibrate effective rear track [B].
     * Returns updated [WheelHeadingCalibration] when an arc completes, or null.
     */
    fun onGnssCourseForCalibration(
        gnssCourseDeg: Float?,
        speedKmh: Float?,
        reverse: Boolean,
        gnssTruthful: Boolean,
        nowEpochMs: Long,
    ): WheelHeadingCalibration? {
        synchronized(lock) {
            val spd = speedKmh ?: 0f
            if (!gnssTruthful || reverse || !spd.isFinite() || spd < CALIB_MIN_SPEED_KMH ||
                gnssCourseDeg == null || !gnssCourseDeg.isFinite()
            ) {
                resetArcLocked()
                return null
            }
            // Exactly 0° is what some receivers send without a course, but it is also due
            // north: skip the sample instead of dropping an arc that passes through north.
            if (gnssCourseDeg == 0f) return null
            if (arcHadSlipOrReverse) {
                resetArcLocked()
                return null
            }
            val prevCourse = arcLastGnssCourseDeg
            if (prevCourse == null || arcStartGnssCourseDeg == null) {
                arcStartGnssCourseDeg = gnssCourseDeg
                arcLastGnssCourseDeg = gnssCourseDeg
                arcAccumGnssDeltaDeg = 0f
                arcWheelDiffM = 0.0
                return null
            }
            val stepDeg = DriveCalibrationMath.wrapDeltaDeg(prevCourse, gnssCourseDeg)
            arcLastGnssCourseDeg = gnssCourseDeg
            if (abs(stepDeg) > 45f) {
                resetArcLocked()
                return null
            }
            arcAccumGnssDeltaDeg += stepDeg
            val totalGnssDeg = abs(arcAccumGnssDeltaDeg)
            if (totalGnssDeg < CALIB_MIN_ARC_DEG) return null
            if (totalGnssDeg > CALIB_MAX_ARC_DEG) {
                resetArcLocked()
                return null
            }
            val gnssRad = Math.toRadians(arcAccumGnssDeltaDeg.toDouble())
            if (abs(gnssRad) < 0.25 || arcWheelDiffM * gnssRad <= 0.0) {
                resetArcLocked()
                return null
            }
            val candidateB = (arcWheelDiffM / gnssRad).toFloat()
            resetArcLocked()
            if (!candidateB.isFinite() || candidateB !in MIN_TRACK_M..MAX_TRACK_M) {
                return null
            }
            val cur = WheelHeadingCalibrationStore.calibration.value
            val blended = if (cur.arcCount == 0) {
                candidateB * 0.5f + DEFAULT_TRACK_M * 0.5f
            } else {
                cur.trackWidthM * (1f - CALIB_ALPHA) + candidateB * CALIB_ALPHA
            }.coerceIn(MIN_TRACK_M, MAX_TRACK_M)
            val updated = WheelHeadingCalibration(
                trackWidthM = blended,
                calibratedAtEpochMs = nowEpochMs,
                arcCount = cur.arcCount + 1,
            )
            WheelHeadingCalibrationStore.update(updated)
            return updated
        }
    }

    fun consumeDeltaDeg(): Float = synchronized(lock) {
        val out = pendingDeltaDeg.toFloat()
        pendingDeltaDeg = 0.0
        lastStepDeltaDeg = out
        out
    }

    fun discard() {
        synchronized(lock) {
            pendingDeltaDeg = 0.0
            lastStepDeltaDeg = 0f
        }
    }

    fun discardThrough(nowElapsedMs: Long) {
        synchronized(lock) {
            pendingDeltaDeg = 0.0
            lastStepDeltaDeg = 0f
            if (nowElapsedMs > lastSampleElapsedMs && lastSampleElapsedMs > 0L) {
                lastSampleElapsedMs = nowElapsedMs
            }
        }
    }

    /**
     * Blends rear-wheel differential delta and steering bicycle delta for `WHEELS_STEER`:
     * - Below [LOW_SPEED_THRESHOLD_KMH] (~5 km/h) or on wheel slip: 100% steer.
     * - Above [FULL_WHEELS_SPEED_KMH]: wheels weighted by [yasTrustWeight], supplemented by steer.
     */
    fun hybridWheelsSteerDelta(
        wheelsDeltaDeg: Float,
        steerDeltaDeg: Float,
        speedKmh: Float,
        slipRejected: Boolean = lastSlipRejected,
        yasTrustWeight: Float = lastYasTrustWeight,
    ): Float {
        val w = if (wheelsDeltaDeg.isFinite()) wheelsDeltaDeg else 0f
        val s = if (steerDeltaDeg.isFinite()) steerDeltaDeg else 0f
        if (slipRejected || !speedKmh.isFinite() || speedKmh < LOW_SPEED_THRESHOLD_KMH) {
            return s
        }
        val speedW = when {
            speedKmh >= FULL_WHEELS_SPEED_KMH -> 1f
            else -> (speedKmh - LOW_SPEED_THRESHOLD_KMH) /
                (FULL_WHEELS_SPEED_KMH - LOW_SPEED_THRESHOLD_KMH)
        }.coerceIn(0f, 1f)
        val effWheelW = (speedW * yasTrustWeight.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        // A zero wheel delta is a quantization gap: the next sample carries those pulses,
        // so steer must not get extra weight here or curves are over-counted.
        return w * effWheelW + s * (1f - effWheelW)
    }

    private fun maxKinematicTurnDeg(distanceM: Double, steerDeg: Float?): Float {
        val wheelbase = SteerHeadingIntegrator.resolveWheelbaseM(
            SteerCalibrationStore.offsets.wheelbaseM,
        ).toDouble()
        val maxRoadRad = Math.toRadians(SteerHeadingIntegrator.MAX_ROAD_WHEEL_DEG.toDouble())
        val physicalMax = Math.toDegrees(distanceM / wheelbase * kotlin.math.tan(maxRoadRad)).toFloat()
        if (steerDeg == null || !steerDeg.isFinite()) {
            return physicalMax.coerceAtLeast(1.5f)
        }
        val centered = SteerCalibrationStore.applyZero(steerDeg) ?: steerDeg
        val roadDeg = (abs(centered) * SteerHeadingIntegrator.DEFAULT_SCALE * 1.8f + 4f)
            .coerceAtMost(SteerHeadingIntegrator.MAX_ROAD_WHEEL_DEG)
        val steerCeiling = Math.toDegrees(
            distanceM / wheelbase * kotlin.math.tan(Math.toRadians(roadDeg.toDouble())),
        ).toFloat()
        return (steerCeiling + 1.0f).coerceIn(1.0f, physicalMax.coerceAtLeast(1.5f))
    }

    /**
     * The YAS sign relative to the wheels is not fixed by spec, so it is learned from clear
     * turns. Until it is known only magnitudes are compared; after that a turn the wheels see
     * one way and YAS the other counts as full disagreement.
     */
    private fun yasDisagreementLocked(omegaWheels: Float, omegaLat: Float): Float {
        if (abs(omegaWheels) >= YAS_SIGN_VOTE_MIN_DEG_PER_SEC && abs(omegaLat) >= YAS_SIGN_VOTE_MIN_DEG_PER_SEC) {
            val vote = if ((omegaWheels > 0f) == (omegaLat > 0f)) 1 else -1
            yasSignVotes = (yasSignVotes + vote).coerceIn(-YAS_SIGN_VOTES_MAX, YAS_SIGN_VOTES_MAX)
        }
        return when {
            yasSignVotes >= YAS_SIGN_VOTES_KNOWN -> abs(omegaWheels - omegaLat)
            yasSignVotes <= -YAS_SIGN_VOTES_KNOWN -> abs(omegaWheels + omegaLat)
            else -> abs(abs(omegaWheels) - abs(omegaLat))
        }
    }

    private fun resetArcLocked() {
        arcWheelDiffM = 0.0
        arcStartGnssCourseDeg = null
        arcLastGnssCourseDeg = null
        arcAccumGnssDeltaDeg = 0f
        arcHadSlipOrReverse = false
    }
}
