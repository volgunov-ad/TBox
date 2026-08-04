package vad.dashing.tbox.location

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure helpers for CONSTANT (Advanced) mock mode: continuous shadow DR,
 * HWGPS-like soft GNSS blend weights, mismatch streak for optional auto-calib,
 * reverse nose/travel conversions.
 */
object ConstantDrMath {
    /** Consecutive large mismatches before requesting calibration (normal). */
    const val MISMATCH_STREAK_TO_CALIBRATE = 10

    /**
     * When last drive calibration is fresher than [FRESH_CALIB_MS], require this longer
     * streak so a short-term mismatch does not start auto-calib.
     */
    const val MISMATCH_STREAK_WHEN_FRESH = 20

    /** Fresh calibration window: short mismatches are ignored for auto-calib. */
    const val FRESH_CALIB_MS = 60L * 60L * 1_000L

    /** Below this CAN/mock speed, do not count DR↔GNSS mismatch (GNSS wander). */
    const val MISMATCH_MIN_SPEED_KMH = 10f

    /** Floor for adaptive mismatch threshold (m). */
    const val MISMATCH_THRESHOLD_FLOOR_M = 25.0

    /** Sample interval used inside adaptive threshold (~mock period). */
    const val BLEND_INTERVAL_SEC = 1.0

    /** Min speed (m/s) to integrate yaw / advance shadow — same as HWGPS 0.5 m/s. */
    const val MIN_MOVE_SPEED_MPS = 0.5f

    val MIN_MOVE_SPEED_KMH: Float get() = MIN_MOVE_SPEED_MPS * 3.6f

    /**
     * Hard resync when shadow↔GNSS distance is at least this many meters
     * (and soft-blend weight would already be ~0).
     */
    const val HARD_RESYNC_MIN_DIST_M = 80.0

    /** GNSS must stay trustworthy this long before snapping shadow. */
    const val HARD_RESYNC_TRUST_MS = 3_000L

    /** Relative CAN↔GNSS speed tolerance for hard-resync trust. */
    const val HARD_RESYNC_SPEED_REL_TOL = 0.25f

    /** Absolute CAN↔GNSS speed tolerance (km/h) for hard-resync trust. */
    const val HARD_RESYNC_SPEED_ABS_TOL_KMH = 8f

    private const val METERS_PER_DEG_LAT = 111_320.0

    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        if (!lat1.isFinite() || !lon1.isFinite() || !lat2.isFinite() || !lon2.isFinite()) {
            return Double.POSITIVE_INFINITY
        }
        val meanLatRad = Math.toRadians((lat1 + lat2) * 0.5)
        val dLatM = (lat2 - lat1) * METERS_PER_DEG_LAT
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(meanLatRad).coerceAtLeast(1e-6)
        val dLonM = (lon2 - lon1) * metersPerDegLon
        return sqrt(dLatM * dLatM + dLonM * dLonM)
    }

    /**
     * Adaptive horizontal mismatch threshold (m).
     * ~max(25, 0.5·v·Δt + 2·hAcc) with default hAcc when unknown.
     */
    fun mismatchThresholdM(
        speedKmh: Float,
        intervalSec: Double = BLEND_INTERVAL_SEC,
        horizontalAccuracyM: Float? = null,
    ): Double {
        val v = speedKmh.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val speedBased = 0.5 * (v / 3.6) * intervalSec.coerceAtLeast(0.0)
        val acc = horizontalAccuracyM
            ?.takeIf { it.isFinite() && it > 0f }
            ?.toDouble()
            ?: 15.0
        return max(MISMATCH_THRESHOLD_FLOOR_M, speedBased + 2.0 * acc)
    }

    fun isLargeMismatch(distanceM: Double, thresholdM: Double): Boolean =
        distanceM.isFinite() && distanceM >= thresholdM

    fun shouldCountMismatch(speedKmh: Float): Boolean =
        speedKmh.isFinite() && speedKmh >= MISMATCH_MIN_SPEED_KMH

    fun nextMismatchStreak(previousStreak: Int, largeMismatch: Boolean): Int =
        if (largeMismatch) previousStreak + 1 else 0

    fun requiredMismatchStreak(
        nowEpochMs: Long,
        lastCalibratedAtEpochMs: Long,
    ): Int {
        if (lastCalibratedAtEpochMs <= 0L) return MISMATCH_STREAK_TO_CALIBRATE
        val age = nowEpochMs - lastCalibratedAtEpochMs
        return if (age in 0L until FRESH_CALIB_MS) {
            MISMATCH_STREAK_WHEN_FRESH
        } else {
            MISMATCH_STREAK_TO_CALIBRATE
        }
    }

    fun shouldRequestCalibration(streak: Int, requiredStreak: Int): Boolean =
        streak >= requiredStreak

    /**
     * HWGPS-like confidence 0..1 from horizontal accuracy (m).
     * Better accuracy → higher confidence.
     */
    fun confidenceFromAccuracyM(horizontalAccuracyM: Float?): Float {
        val acc = horizontalAccuracyM?.takeIf { it.isFinite() && it > 0f } ?: return 0.55f
        return when {
            acc <= 3f -> 1.0f
            acc <= 5f -> 0.97f
            acc <= 8f -> 0.9f
            acc <= 12f -> 0.85f
            acc <= 20f -> 0.7f
            acc <= 35f -> 0.55f
            else -> 0.0f
        }
    }

    /**
     * Position blend weight from confidence (HWGPS H0.j style).
     */
    fun positionWeightFromConfidence(confidence: Float): Float = when {
        confidence > 0.95f -> 0.8f
        confidence > 0.9f -> 0.6f
        confidence > 0.8f -> 0.4f
        confidence > 0.7f -> 0.25f
        confidence > 0.55f -> 0.15f
        else -> 0f
    }

    /**
     * Course blend weight from confidence + residual heading (HWGPS H0.i style).
     */
    fun courseWeightFromConfidence(confidence: Float, residualDeg: Float): Float {
        val r = abs(residualDeg)
        return when {
            confidence > 0.9f && r < 5f -> 1.0f
            confidence > 0.9f && r < 15f -> 0.7f
            confidence > 0.9f -> 0.5f
            confidence > 0.8f -> 0.3f
            else -> 0f
        }
    }

    /**
     * Scale blend by shadow↔GNSS distance vs adaptive threshold (HWGPS V.c style).
     * Large mismatch → weight toward 0.
     */
    fun mismatchScale(distanceM: Double, thresholdM: Double): Float {
        if (!distanceM.isFinite() || distanceM <= 0.0) return 1f
        if (!thresholdM.isFinite() || thresholdM <= 0.0) return 1f
        if (distanceM >= thresholdM * 1.5) return 0f
        if (distanceM >= thresholdM) return 0.15f
        if (distanceM >= thresholdM * 0.5) {
            val t = ((distanceM - thresholdM * 0.5) / (thresholdM * 0.5)).coerceIn(0.0, 1.0)
            return (1.0 - 0.5 * t).toFloat()
        }
        return 1f
    }

    /**
     * Shadow is far enough that soft blend no longer pulls toward GNSS —
     * hard resync may snap if GNSS stays trustworthy for [HARD_RESYNC_TRUST_MS].
     */
    fun shouldHardResync(distanceM: Double, thresholdM: Double): Boolean {
        if (!distanceM.isFinite() || distanceM <= 0.0) return false
        val softZeroAt = if (thresholdM.isFinite() && thresholdM > 0.0) {
            thresholdM * 1.5
        } else {
            HARD_RESYNC_MIN_DIST_M
        }
        val gate = max(HARD_RESYNC_MIN_DIST_M, softZeroAt)
        return distanceM >= gate
    }

    /**
     * GNSS speed looks sane and (when CAN is present) agrees with vehicle speed.
     */
    fun gnssSpeedAgreesForHardResync(gnssKmh: Float, canKmh: Float?): Boolean {
        if (!gnssKmh.isFinite() || gnssKmh < MIN_MOVE_SPEED_KMH || gnssKmh >= 250f) {
            return false
        }
        val can = canKmh?.takeIf { it.isFinite() && it >= 0f } ?: return true
        val diff = abs(gnssKmh - can)
        val tol = max(HARD_RESYNC_SPEED_ABS_TOL_KMH, abs(can) * HARD_RESYNC_SPEED_REL_TOL)
        return diff <= tol
    }

    /**
     * GNSS course pull by vehicle speed (m/s): HWGPS 0 / 0.3 / 1.0.
     */
    fun speedScaleForGnssCourse(speedMps: Float): Float = when {
        speedMps < MIN_MOVE_SPEED_MPS -> 0f
        speedMps < 3f -> 0.3f
        else -> 1f
    }

    fun blendLatLon(
        shadowLat: Double,
        shadowLon: Double,
        gnssLat: Double,
        gnssLon: Double,
        alpha: Float,
    ): Pair<Double, Double> {
        val a = alpha.coerceIn(0f, 1f).toDouble()
        if (a >= 1.0) return gnssLat to gnssLon
        if (a <= 0.0) return shadowLat to shadowLon
        return (shadowLat + (gnssLat - shadowLat) * a) to
            (shadowLon + (gnssLon - shadowLon) * a)
    }

    fun blendBearingDeg(shadowNoseDeg: Float, gnssNoseDeg: Float, alpha: Float): Float {
        val a = alpha.coerceIn(0f, 1f)
        if (a <= 0f) return wrapBearingDeg(shadowNoseDeg)
        if (a >= 1f) return wrapBearingDeg(gnssNoseDeg)
        val d = DriveCalibrationMath.wrapDeltaDeg(shadowNoseDeg, gnssNoseDeg)
        return wrapBearingDeg(shadowNoseDeg + d * a)
    }

    fun noseHeadingFromCourseOverGround(courseOverGroundDeg: Float, reverse: Boolean): Float {
        val cog = wrapBearingDeg(courseOverGroundDeg)
        return if (reverse) wrapBearingDeg(cog + 180f) else cog
    }

    fun travelBearingFromNoseHeading(noseHeadingDeg: Float, reverse: Boolean): Float {
        val nose = wrapBearingDeg(noseHeadingDeg)
        return if (reverse) wrapBearingDeg(nose + 180f) else nose
    }

    fun wrapBearingDeg(bearingDeg: Float): Float {
        var b = bearingDeg % 360f
        if (b < 0f) b += 360f
        return b
    }

    fun extrapolateLatLon(
        lat: Double,
        lon: Double,
        bearingDeg: Float,
        distanceM: Double,
    ): Pair<Double, Double> {
        if (distanceM <= 0.0 || !distanceM.isFinite()) return lat to lon
        val bearingRad = Math.toRadians(bearingDeg.toDouble())
        val north = distanceM * cos(bearingRad)
        val east = distanceM * sin(bearingRad)
        val latRad = Math.toRadians(lat)
        val dLat = north / METERS_PER_DEG_LAT
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(latRad).coerceAtLeast(1e-6)
        val dLon = east / metersPerDegLon
        return (lat + dLat) to (lon + dLon)
    }
}
