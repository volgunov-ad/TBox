package vad.dashing.tbox.vehicle

import kotlin.math.abs
import kotlin.math.pow

/**
 * Wheel-pulse path integrator with odometer-anchored calibration.
 *
 * **Uncalibrated pulse (k=0 or confidence below threshold) is never used for distance**
 * — only raw Δpulse accumulates for k estimation. See [WheelPulseCalibrationStore.isUsableForDistance].
 *
 * Distance consumers use independent cursors / peeks:
 * — trips: [peekPulseSinceLastOdoM] over integer odo (odo = truth);
 * — mock DR: [flushDrDistanceM]; call [syncDrCursor] when pulse DR is enabled
 *   so a session backlog is not applied as one step.
 */
object WheelPulseOdometer {
    /**
     * ESP `*PulseCounter` on Dashing wraps at 2^13 (field log max 8189).
     * 16-bit wrap treated 8180→50 as a glitch (Δ=0) and 200% L/R asymmetry.
     */
    const val COUNTER_BITS = 13
    const val HARD_CALIB_MIN_ODO_KM = 5
    const val ASYM_SLIP_THRESHOLD = 0.08f
    /** Ignore L/R noise when a sample is too short for 1 pulse to stay under [ASYM_SLIP_THRESHOLD]. */
    const val MIN_ASYM_MEAN_PULSES = 20f
    const val STRAIGHT_STEER_DEG = 15f
    const val MIN_SPEED_KMH_CALIB = 5f
    const val SOFT_NUDGE_ALPHA = 0.03f
    const val HARD_CALIB_ALPHA = 0.3f
    const val MAX_ODO_PULSE_RATIO_ERROR = 0.25f
    /** ~90-tooth ring × 1.8…2.4 m tyre; 0.51 from a sparse first window is invalid. */
    const val MIN_METERS_PER_PULSE = 0.010f
    const val MAX_METERS_PER_PULSE = 0.080f
    const val HARD_CALIB_CONFIDENCE_BUMP = 0.15f
    /** Skip a km-tick if any sample had a stronger steer than this (deg). */
    const val KM_DIRTY_PEAK_STEER_DEG = 25f
    /** Skip a km-tick if pulse-weighted mean |steer| exceeds this (deg). */
    const val KM_DIRTY_MEAN_STEER_DEG = 6f
    /** Skip a km-tick if moving-speed min…max span exceeds this (km/h). */
    const val KM_DIRTY_SPEED_SPAN_KMH = 40f

    data class CalibrationState(
        val metersPerPulse: Float,
        val confidence: Float,
        val lastAsymmetryRatio: Float,
        val pulseSinceLastOdoM: Float,
        val usableForDistance: Boolean,
    )

    /** Last-tick fields for geo-debug and field calibration. */
    data class DebugSnapshot(
        val counters: WheelCounters?,
        val dLhf: Int?,
        val dRhf: Int?,
        val dLhr: Int?,
        val dRhr: Int?,
        val asymFront: Float,
        val metersPerPulse: Float,
        val confidence: Float,
        val pulseSinceLastOdoM: Float,
        val lastOdoKm: UInt?,
        /** `expectedM − pulseSince` on last km-tick; null before first tick. */
        val lastOdoResidualM: Float?,
        val lastOdoNudgeSkipped: Boolean,
        /** `range` / `turn` / `span` / `rev` when the last km-tick skipped a k nudge. */
        val lastOdoSkipReason: String?,
        val usableForDistance: Boolean,
    )

    private val lock = Any()

    private var lastCounters: WheelCounters? = null
    private var kMetersPerPulse: Float = 0f
    private var calibrationConfidence: Float = 0f
    /** Session path length while usable (m); DR cursor tracks this. */
    private var totalPathM: Double = 0.0
    private var drCursorM: Double = 0.0
    private var pulseDistanceSinceLastOdoM: Float = 0f
    private var lastOdoKm: UInt? = null
    private var lastAsymmetryRatio: Float = 0f

    private var calibWindowStartOdoKm: UInt? = null
    private var calibWindowStartPulse: Double = 0.0
    private var calibWindowAsymSum: Float = 0f
    private var calibWindowAsymSamples: Int = 0

    private var lastSampleCounters: WheelCounters? = null
    private var lastDLhf: Int? = null
    private var lastDRhf: Int? = null
    private var lastDLhr: Int? = null
    private var lastDRhr: Int? = null
    private var lastOdoResidualM: Float? = null
    private var lastOdoNudgeSkipped: Boolean = false
    private var lastOdoSkipReason: String? = null

    private var kmHadReverse: Boolean = false
    private var kmMaxAbsSteerDeg: Float = 0f
    private var kmAbsSteerPulseSum: Float = 0f
    private var kmSteerWeight: Float = 0f
    private var kmMinSpeedKmh: Float? = null
    private var kmMaxSpeedKmh: Float? = null

    fun configure(metersPerPulse: Float, confidence: Float) {
        synchronized(lock) {
            val k = metersPerPulse.coerceAtLeast(0f)
            val conf = confidence.coerceIn(0f, 1f)
            val sane = k <= 0f || k in MIN_METERS_PER_PULSE..MAX_METERS_PER_PULSE
            if (sane) {
                kMetersPerPulse = k
                calibrationConfidence = conf
            } else {
                kMetersPerPulse = 0f
                calibrationConfidence = 0f
                publishCalibrationLocked()
            }
            if (!isUsableForUseLocked()) {
                drCursorM = totalPathM
                pulseDistanceSinceLastOdoM = 0f
            }
        }
    }

    fun peekCalibration(): CalibrationState = synchronized(lock) {
        CalibrationState(
            metersPerPulse = kMetersPerPulse,
            confidence = calibrationConfidence,
            lastAsymmetryRatio = lastAsymmetryRatio,
            pulseSinceLastOdoM = pulseDistanceSinceLastOdoM,
            usableForDistance = isUsableForUseLocked(),
        )
    }

    fun peekPulseSinceLastOdoM(): Float = synchronized(lock) {
        pulseDistanceSinceLastOdoM.coerceAtLeast(0f)
    }

    fun peekDebugSnapshot(): DebugSnapshot = synchronized(lock) {
        DebugSnapshot(
            counters = lastSampleCounters,
            dLhf = lastDLhf,
            dRhf = lastDRhf,
            dLhr = lastDLhr,
            dRhr = lastDRhr,
            asymFront = lastAsymmetryRatio,
            metersPerPulse = kMetersPerPulse,
            confidence = calibrationConfidence,
            pulseSinceLastOdoM = pulseDistanceSinceLastOdoM,
            lastOdoKm = lastOdoKm,
            lastOdoResidualM = lastOdoResidualM,
            lastOdoNudgeSkipped = lastOdoNudgeSkipped,
            lastOdoSkipReason = lastOdoSkipReason,
            usableForDistance = isUsableForUseLocked(),
        )
    }

    fun resetSession() {
        synchronized(lock) {
            lastCounters = null
            totalPathM = 0.0
            drCursorM = 0.0
            pulseDistanceSinceLastOdoM = 0f
            lastAsymmetryRatio = 0f
            resetCalibWindowLocked()
            resetKmQualityLocked()
        }
    }

    fun onWheelSample(
        counters: WheelCounters,
        reverse: Boolean,
        steerDeg: Float?,
        speedKmh: Float?,
        nowElapsedMs: Long,
    ) {
        synchronized(lock) {
            val prev = lastCounters
            lastCounters = counters
            if (prev == null) return

            val dL = forwardDelta(prev.lhf, counters.lhf)
            val dR = forwardDelta(prev.rhf, counters.rhf)
            val dLr = forwardDelta(prev.lhr, counters.lhr)
            val dRr = forwardDelta(prev.rhr, counters.rhr)

            if (dL == 0 && dR == 0 && dLr == 0 && dRr == 0) return

            val meanFront = (dL + dR) * 0.5f
            val asym = asymmetryRatio(dL, dR)
            lastAsymmetryRatio = asym
            lastSampleCounters = counters
            lastDLhf = dL
            lastDRhf = dR
            lastDLhr = dLr
            lastDRhr = dRr

            accumulateKmQualityLocked(reverse, steerDeg, speedKmh, meanFront)

            if (meanFront > 0f) {
                calibWindowStartPulse += meanFront.toDouble()
            }
            val straight = isStraight(steerDeg) && !reverse
            if (straight && meanFront > 0f && isAsymSampleReliable(speedKmh, meanFront)) {
                calibWindowAsymSum += asym
                calibWindowAsymSamples++
                if (asym > ASYM_SLIP_THRESHOLD && isUsableForUseLocked()) {
                    calibrationConfidence =
                        (calibrationConfidence - 0.02f).coerceAtLeast(0f)
                    discardPendingDistanceLocked()
                    publishCalibrationLocked()
                }
            }

            maybeHardCalibrateLocked(steerDeg, reverse, speedKmh)

            if (!isUsableForUseLocked()) return

            val deltaM = meanFront * kMetersPerPulse
            if (deltaM <= 0f || !deltaM.isFinite()) return
            totalPathM += deltaM.toDouble()
            pulseDistanceSinceLastOdoM += deltaM
        }
    }

    fun onOdometerKm(odo: UInt, nowElapsedMs: Long) {
        synchronized(lock) {
            val prev = lastOdoKm
            lastOdoKm = odo
            if (prev == null) {
                calibWindowStartOdoKm = odo
                calibWindowStartPulse = 0.0
                calibWindowAsymSum = 0f
                calibWindowAsymSamples = 0
                resetKmQualityLocked()
                return
            }
            if (odo <= prev) return
            val deltaOdoKm = (odo - prev).toInt()
            if (deltaOdoKm <= 0) return

            val skipReason = currentKmSkipReason(pulseDistanceSinceLastOdoM)
            if (isUsableForUseLocked()) {
                softNudgeOnOdoTickLocked(deltaOdoKm, skipReason)
            } else {
                lastOdoResidualM = deltaOdoKm * 1000f - pulseDistanceSinceLastOdoM
                lastOdoNudgeSkipped = skipReason != null
                lastOdoSkipReason = skipReason
                pulseDistanceSinceLastOdoM = 0f
            }

            if (isDirtyKinematicsSkip(skipReason)) {
                resetCalibWindowLocked()
            } else if (calibWindowStartOdoKm == null) {
                calibWindowStartOdoKm = prev
                calibWindowStartPulse = 0.0
            }
            resetKmQualityLocked()
        }
    }

    /**
     * Meters since last DR flush. Independent of trip fraction.
     * 0 until [WheelPulseCalibrationStore.isUsableForDistance].
     */
    fun flushDrDistanceM(): Float = synchronized(lock) {
        if (!isUsableForUseLocked()) {
            drCursorM = totalPathM
            return 0f
        }
        val out = (totalPathM - drCursorM).toFloat()
        drCursorM = totalPathM
        out.coerceAtLeast(0f)
    }

    /**
     * Align the DR cursor with the current path without emitting metres.
     * Call when pulse mock DR is turned on so a session backlog is not dumped
     * as one teleport.
     */
    fun syncDrCursor() {
        synchronized(lock) {
            drCursorM = totalPathM
        }
    }

    /** Unflushed pulse metres waiting for mock DR. Independent of trip fraction. */
    fun peekDrPendingM(): Float = synchronized(lock) {
        if (!isUsableForUseLocked()) return 0f
        (totalPathM - drCursorM).toFloat().coerceAtLeast(0f)
    }

    @Deprecated("Use flushDrDistanceM", ReplaceWith("flushDrDistanceM()"))
    fun flushDistanceM(): Float = flushDrDistanceM()

    internal fun forwardDelta(prev: Int, next: Int, bits: Int = COUNTER_BITS): Int {
        val mod = 1 shl bits
        val mask = mod - 1
        val p = prev and mask
        val n = next and mask
        var d = n - p
        if (d < 0) d += mod
        if (d > mod / 2) return 0
        return d
    }

    internal fun asymmetryRatio(dL: Int, dR: Int): Float {
        val mean = (dL + dR) * 0.5f
        if (mean <= 0f) return 0f
        return abs(dL - dR) / mean
    }

    private fun isUsableForUseLocked(): Boolean =
        kMetersPerPulse > 0f &&
            calibrationConfidence >= WheelPulseCalibrationStore.CONFIDENCE_USE_THRESHOLD &&
            kMetersPerPulse.isFinite()

    private fun isStraight(steerDeg: Float?): Boolean {
        val s = steerDeg ?: return true
        if (!s.isFinite()) return true
        return abs(s) <= STRAIGHT_STEER_DEG
    }

    /** Crawl / 1-pulse quantization is not ESP slip. Geo-debug 2026-08-21: 2 km/h dL=5 dropped Ready. */
    private fun isAsymSampleReliable(speedKmh: Float?, meanFront: Float): Boolean {
        val spd = speedKmh ?: return false
        if (!spd.isFinite() || spd < MIN_SPEED_KMH_CALIB) return false
        return meanFront >= MIN_ASYM_MEAN_PULSES
    }

    private fun accumulateKmQualityLocked(
        reverse: Boolean,
        steerDeg: Float?,
        speedKmh: Float?,
        meanFront: Float,
    ) {
        if (reverse) kmHadReverse = true
        val steer = steerDeg
        if (steer != null && steer.isFinite()) {
            val absSteer = abs(steer)
            if (absSteer > kmMaxAbsSteerDeg) kmMaxAbsSteerDeg = absSteer
            if (meanFront > 0f) {
                kmAbsSteerPulseSum += absSteer * meanFront
                kmSteerWeight += meanFront
            }
        }
        val spd = speedKmh
        if (spd != null && spd.isFinite() && spd >= MIN_SPEED_KMH_CALIB) {
            val minS = kmMinSpeedKmh
            val maxS = kmMaxSpeedKmh
            kmMinSpeedKmh = if (minS == null) spd else minOf(minS, spd)
            kmMaxSpeedKmh = if (maxS == null) spd else maxOf(maxS, spd)
        }
    }

    private fun currentKmSkipReason(pulseSince: Float): String? {
        if (kmHadReverse) return "rev"
        val meanSteer = if (kmSteerWeight > 0f) kmAbsSteerPulseSum / kmSteerWeight else 0f
        if (kmMaxAbsSteerDeg > KM_DIRTY_PEAK_STEER_DEG || meanSteer > KM_DIRTY_MEAN_STEER_DEG) {
            return "turn"
        }
        val minS = kmMinSpeedKmh
        val maxS = kmMaxSpeedKmh
        if (minS != null && maxS != null && maxS - minS > KM_DIRTY_SPEED_SPAN_KMH) {
            return "span"
        }
        if (pulseSince !in 500f..1500f) return "range"
        return null
    }

    private fun isDirtyKinematicsSkip(reason: String?): Boolean =
        reason == "turn" || reason == "span" || reason == "rev"

    private fun resetKmQualityLocked() {
        kmHadReverse = false
        kmMaxAbsSteerDeg = 0f
        kmAbsSteerPulseSum = 0f
        kmSteerWeight = 0f
        kmMinSpeedKmh = null
        kmMaxSpeedKmh = null
    }

    private fun discardPendingDistanceLocked() {
        drCursorM = totalPathM
        pulseDistanceSinceLastOdoM = 0f
    }

    private fun publishCalibrationLocked() {
        val cur = WheelPulseCalibrationStore.calibration.value
        WheelPulseCalibrationStore.update(
            cur.copy(
                metersPerPulse = kMetersPerPulse,
                confidence = calibrationConfidence,
            ),
        )
    }

    private fun softNudgeOnOdoTickLocked(deltaOdoKm: Int, skipReason: String?) {
        val pulseSince = pulseDistanceSinceLastOdoM
        val expected = deltaOdoKm * 1000f
        lastOdoResidualM = expected - pulseSince
        lastOdoNudgeSkipped = skipReason != null
        lastOdoSkipReason = skipReason
        pulseDistanceSinceLastOdoM = 0f
        if (!isUsableForUseLocked()) return
        if (skipReason != null) return
        val ratio = (expected / pulseSince).coerceIn(0.85f, 1.15f)
        kMetersPerPulse *= ratio.pow(SOFT_NUDGE_ALPHA)
        publishCalibrationLocked()
    }

    private fun maybeHardCalibrateLocked(steerDeg: Float?, reverse: Boolean, speedKmh: Float?) {
        if (reverse) return
        if (!isStraight(steerDeg)) return
        val spd = speedKmh ?: return
        if (spd < MIN_SPEED_KMH_CALIB) return

        val startOdo = calibWindowStartOdoKm ?: return
        val curOdo = lastOdoKm ?: return
        if (curOdo <= startOdo) return
        val deltaOdoKm = (curOdo - startOdo).toInt()
        if (deltaOdoKm < HARD_CALIB_MIN_ODO_KM) return

        val deltaPulse = calibWindowStartPulse
        if (deltaPulse < 1.0) return

        val expectedM = deltaOdoKm * 1000.0
        val kNew = (expectedM / deltaPulse).toFloat()
        if (!kNew.isFinite() || kNew !in MIN_METERS_PER_PULSE..MAX_METERS_PER_PULSE) {
            resetCalibWindowLocked()
            return
        }

        val avgAsym = if (calibWindowAsymSamples > 0) {
            calibWindowAsymSum / calibWindowAsymSamples
        } else {
            0f
        }
        if (avgAsym > ASYM_SLIP_THRESHOLD) {
            resetCalibWindowLocked()
            return
        }

        val staleK = kMetersPerPulse > 0f && run {
            val actualM = deltaPulse * kMetersPerPulse.toDouble()
            actualM > 0.0 &&
                abs(actualM - expectedM) / expectedM > MAX_ODO_PULSE_RATIO_ERROR
        }
        if (staleK) {
            kMetersPerPulse = kNew
            calibrationConfidence = HARD_CALIB_CONFIDENCE_BUMP
        } else {
            kMetersPerPulse = if (kMetersPerPulse <= 0f) {
                kNew
            } else {
                kMetersPerPulse * (1f - HARD_CALIB_ALPHA) + kNew * HARD_CALIB_ALPHA
            }
            calibrationConfidence =
                minOf(1f, calibrationConfidence + HARD_CALIB_CONFIDENCE_BUMP)
        }
        calibWindowStartOdoKm = curOdo
        calibWindowStartPulse = 0.0
        calibWindowAsymSum = 0f
        calibWindowAsymSamples = 0
        publishCalibrationLocked()
    }

    private fun resetCalibWindowLocked() {
        calibWindowStartOdoKm = lastOdoKm
        calibWindowStartPulse = 0.0
        calibWindowAsymSum = 0f
        calibWindowAsymSamples = 0
    }

    internal fun exportCalibration(): Pair<Float, Float> = synchronized(lock) {
        kMetersPerPulse to calibrationConfidence
    }

    /** Test-only: full singleton wipe, including odo anchor. */
    internal fun resetAllForTest() {
        synchronized(lock) {
            lastCounters = null
            kMetersPerPulse = 0f
            calibrationConfidence = 0f
            totalPathM = 0.0
            drCursorM = 0.0
            pulseDistanceSinceLastOdoM = 0f
            lastOdoKm = null
            lastAsymmetryRatio = 0f
            lastSampleCounters = null
            lastDLhf = null
            lastDRhf = null
            lastDLhr = null
            lastDRhr = null
            lastOdoResidualM = null
            lastOdoNudgeSkipped = false
            lastOdoSkipReason = null
            resetCalibWindowLocked()
            resetKmQualityLocked()
        }
    }
}
