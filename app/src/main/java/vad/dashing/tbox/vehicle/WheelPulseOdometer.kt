package vad.dashing.tbox.vehicle

import kotlin.math.abs
import kotlin.math.pow
import vad.dashing.tbox.BuildConfig

/**
 * Wheel-pulse path integrator with odometer-anchored calibration.
 *
 * **Uncalibrated pulse (k=0 or confidence below threshold) is never used for distance**
 * — only raw Δpulse accumulates for k estimation. See [WheelPulseCalibrationStore.isUsableForDistance].
 */
object WheelPulseOdometer {
    const val COUNTER_BITS = 16
    const val HARD_CALIB_MIN_ODO_KM = 5
    const val ASYM_SLIP_THRESHOLD = 0.08f
    const val STRAIGHT_STEER_DEG = 15f
    const val MIN_SPEED_KMH_CALIB = 5f
    const val SOFT_NUDGE_ALPHA = 0.03f
    const val HARD_CALIB_ALPHA = 0.3f
    const val MAX_ODO_PULSE_RATIO_ERROR = 0.25f

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
        val usableForDistance: Boolean,
    )

    private val lock = Any()

    private var lastCounters: WheelCounters? = null
    private var kMetersPerPulse: Float = 0f
    private var calibrationConfidence: Float = 0f
    private var pendingDistanceM: Float = 0f
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

    fun configure(metersPerPulse: Float, confidence: Float) {
        synchronized(lock) {
            kMetersPerPulse = metersPerPulse.coerceAtLeast(0f)
            calibrationConfidence = confidence.coerceIn(0f, 1f)
            if (!isUsableForUseLocked()) {
                pendingDistanceM = 0f
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
            usableForDistance = isUsableForUseLocked(),
        )
    }

    fun resetSession() {
        synchronized(lock) {
            lastCounters = null
            pendingDistanceM = 0f
            pulseDistanceSinceLastOdoM = 0f
            lastAsymmetryRatio = 0f
            resetCalibWindowLocked()
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

            val straight = isStraight(steerDeg) && !reverse
            if (straight && meanFront > 0f) {
                calibWindowAsymSum += asym
                calibWindowAsymSamples++
                calibWindowStartPulse += meanFront.toDouble()
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
            pendingDistanceM += deltaM
            pulseDistanceSinceLastOdoM += deltaM
        }
    }

    fun onOdometerKm(odo: UInt, nowElapsedMs: Long) {
        synchronized(lock) {
            val prev = lastOdoKm
            lastOdoKm = odo
            if (prev == null) {
                calibWindowStartOdoKm = odo
                return
            }
            if (odo <= prev) return
            val deltaOdoKm = (odo - prev).toInt()
            if (deltaOdoKm <= 0) return

            if (isUsableForUseLocked()) {
                softNudgeOnOdoTickLocked(deltaOdoKm)
            }

            if (calibWindowStartOdoKm == null) {
                calibWindowStartOdoKm = prev
                calibWindowStartPulse = 0.0
            }
        }
    }

    /** Meters since last flush; 0 until [WheelPulseCalibrationStore.isUsableForDistance]. */
    fun flushDistanceM(): Float = synchronized(lock) {
        if (!isUsableForUseLocked()) {
            pendingDistanceM = 0f
            return 0f
        }
        val out = pendingDistanceM
        pendingDistanceM = 0f
        out.coerceAtLeast(0f)
    }

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

    private fun discardPendingDistanceLocked() {
        pendingDistanceM = 0f
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

    private fun softNudgeOnOdoTickLocked(deltaOdoKm: Int) {
        val pulseSince = pulseDistanceSinceLastOdoM
        val expected = deltaOdoKm * 1000f
        lastOdoResidualM = expected - pulseSince
        lastOdoNudgeSkipped = pulseSince !in 500f..1500f
        pulseDistanceSinceLastOdoM = 0f
        if (!isUsableForUseLocked()) return
        if (lastOdoNudgeSkipped) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    TAG,
                    "odo tick skip nudge: pulseSince=${"%.1f".format(pulseSince)} expected=$expected",
                )
            }
            return
        }
        val ratio = (expected / pulseSince).coerceIn(0.85f, 1.15f)
        kMetersPerPulse *= ratio.pow(SOFT_NUDGE_ALPHA)
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                TAG,
                "odo soft nudge: residual=${"%.1f".format(lastOdoResidualM)} ratio=$ratio k=$kMetersPerPulse",
            )
        }
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
        if (kMetersPerPulse > 0f) {
            val actualM = deltaPulse * kMetersPerPulse.toDouble()
            if (actualM <= 0.0) return
            val err = abs(actualM - expectedM) / expectedM
            if (err > MAX_ODO_PULSE_RATIO_ERROR) {
                resetCalibWindowLocked()
                return
            }
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

        val kNew = (expectedM / deltaPulse).toFloat()
        if (!kNew.isFinite() || kNew <= 0f) return
        kMetersPerPulse = if (kMetersPerPulse <= 0f) {
            kNew
        } else {
            kMetersPerPulse * (1f - HARD_CALIB_ALPHA) + kNew * HARD_CALIB_ALPHA
        }
        calibrationConfidence = minOf(1f, calibrationConfidence + 0.15f)
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                TAG,
                "hard calib: k=$kMetersPerPulse conf=$calibrationConfidence " +
                    "windowKm=$deltaOdoKm pulse=$deltaPulse avgAsym=$avgAsym",
            )
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

    private const val TAG = "WheelPulse"
}
