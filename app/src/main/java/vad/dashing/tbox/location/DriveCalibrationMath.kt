package vad.dashing.tbox.location

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure math for on-road speed / yaw calibration (raw GNSS + CAN + gyro).
 * Uses time-aligned windows and lag τ rather than instant GNSS↔CAN pairs.
 */
object DriveCalibrationMath {
    const val MIN_SPEED_KMH = 18f
    const val MAX_ABS_ACCEL_KMH_S = 4f
    const val MIN_TURN_ABS_DEG = 25f
    const val LAG_MAX_MS = 1_500L
    const val LAG_STEP_MS = 100L
    const val YAW_SEGMENT_MAX_MS = 10_000L
    const val MAX_HORIZONTAL_ACCURACY_M = 25f
    const val SPEED_WINDOW_MS = 1_500L
    const val SPEED_WINDOW_STEP_MS = 500L
    /** Residual |gnss−can| after lag that is too wild for a scale sample. */
    const val MAX_RESIDUAL_KMH = 8f
    const val MAX_RESIDUAL_FRAC = 0.22f

    const val SPEED_SAMPLES_TARGET = 40
    const val YAW_SEGMENTS_TARGET = 3
    const val SPEED_BUCKETS_TARGET = 3

    /** Min accepted speed windows / yaw segments to treat estimate as real. */
    const val MIN_SPEED_FOR_ESTIMATE = 8
    const val MIN_YAW_FOR_ESTIMATE = 2

    /** Bearing jump (deg) in a short interval with flat gyro → junk course. */
    const val COURSE_JUMP_DEG = 22f
    const val COURSE_JUMP_MAX_MS = 400L
    const val COURSE_JUMP_MAX_YAW_ABS = 4f

    data class SpeedSample(
        val elapsedMs: Long,
        val gnssKmh: Float,
        val canKmh: Float,
    )

    data class YawSample(
        val elapsedMs: Long,
        val yawRateDegPerSec: Float,
        val bearingDeg: Float,
        val speedKmh: Float,
    )

    data class Estimates(
        val lagMs: Long = 0L,
        val lagStability: Float = 1f,
        val speedScale: Float = 1f,
        val yawScale: Float = 1f,
        val yawSign: Int = 1,
        val speedSampleCount: Int = 0,
        val speedBuckets: Int = 0,
        val yawSegmentCount: Int = 0,
        val yawRejectedCount: Int = 0,
        val speedFill: Float = 0f,
        val yawFill: Float = 0f,
        val speedEstimated: Boolean = false,
        val yawEstimated: Boolean = false,
    ) {
        val ready: Boolean
            get() = speedFill >= 1f && yawFill >= 1f && speedEstimated && yawEstimated

        val hasAnyEstimate: Boolean
            get() = speedEstimated || yawEstimated
    }

    fun wrapBearingDeg(bearingDeg: Float): Float {
        var b = bearingDeg % 360f
        if (b < 0f) b += 360f
        return b
    }

    fun wrapDeltaDeg(fromDeg: Float, toDeg: Float): Float {
        var d = toDeg - fromDeg
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    fun speedBucket(kmh: Float): Int = (kmh / 10f).toInt().coerceAtLeast(0)

    /**
     * Pick lag τ that minimizes mean |gnss(t+τ) − can(t)| on overlapping samples.
     * Models GNSS arriving later than CAN (common on HU).
     */
    fun estimateLagMs(samples: List<SpeedSample>): Long {
        if (samples.size < 8) return 0L
        var bestLag = 0L
        var bestErr = Float.MAX_VALUE
        var lag = 0L
        while (lag <= LAG_MAX_MS) {
            var sum = 0.0
            var n = 0
            for (s in samples) {
                val g = interpolateGnss(samples, s.elapsedMs + lag) ?: continue
                sum += abs(g - s.canKmh)
                n++
            }
            if (n >= 5) {
                val err = (sum / n).toFloat()
                if (err < bestErr) {
                    bestErr = err
                    bestLag = lag
                }
            }
            lag += LAG_STEP_MS
        }
        return bestLag
    }

    /**
     * 1 = stable lag across halves; lower when halves disagree → slow down speed fill.
     */
    fun lagStability(samples: List<SpeedSample>): Float {
        if (samples.size < 16) return 0.85f
        val mid = samples.size / 2
        val lag1 = estimateLagMs(samples.subList(0, mid))
        val lag2 = estimateLagMs(samples.subList(mid, samples.size))
        val diff = abs(lag1 - lag2)
        return when {
            diff <= 200L -> 1f
            diff <= 500L -> 0.7f
            else -> 0.45f
        }
    }

    fun interpolateGnss(samples: List<SpeedSample>, atElapsedMs: Long): Float? {
        if (samples.isEmpty()) return null
        if (atElapsedMs <= samples.first().elapsedMs) return samples.first().gnssKmh
        if (atElapsedMs >= samples.last().elapsedMs) return samples.last().gnssKmh
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            if (atElapsedMs in a.elapsedMs..b.elapsedMs) {
                val span = (b.elapsedMs - a.elapsedMs).coerceAtLeast(1L).toFloat()
                val t = (atElapsedMs - a.elapsedMs) / span
                return a.gnssKmh + (b.gnssKmh - a.gnssKmh) * t
            }
        }
        return null
    }

    fun interpolateBearing(samples: List<YawSample>, atElapsedMs: Long): Float? {
        if (samples.isEmpty()) return null
        if (atElapsedMs <= samples.first().elapsedMs) return samples.first().bearingDeg
        if (atElapsedMs >= samples.last().elapsedMs) return samples.last().bearingDeg
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            if (atElapsedMs in a.elapsedMs..b.elapsedMs) {
                val span = (b.elapsedMs - a.elapsedMs).coerceAtLeast(1L).toFloat()
                val t = (atElapsedMs - a.elapsedMs) / span
                val d = wrapDeltaDeg(a.bearingDeg, b.bearingDeg)
                return wrapBearingDeg(a.bearingDeg + d * t)
            }
        }
        return null
    }

    /**
     * Collect gnss/can ratios from quasi-steady 1–1.5 s windows with GNSS advanced by lag.
     * [k_speed] multiplies CAN so that CAN * k ≈ GNSS.
     */
    fun collectSpeedRatios(
        samples: List<SpeedSample>,
        lagMs: Long,
    ): List<Float> {
        if (samples.size < 6) return emptyList()
        val out = ArrayList<Float>()
        var nextEnd = samples.first().elapsedMs + SPEED_WINDOW_MS
        var i = 0
        while (i < samples.size) {
            val end = samples[i]
            if (end.elapsedMs < nextEnd) {
                i++
                continue
            }
            nextEnd = end.elapsedMs + SPEED_WINDOW_STEP_MS
            val startT = end.elapsedMs - SPEED_WINDOW_MS
            var canSum = 0.0
            var gnssSum = 0.0
            var n = 0
            var canMin = Float.MAX_VALUE
            var canMax = -Float.MAX_VALUE
            for (s in samples) {
                if (s.elapsedMs < startT || s.elapsedMs > end.elapsedMs) continue
                val g = interpolateGnss(samples, s.elapsedMs + lagMs) ?: continue
                canSum += s.canKmh
                gnssSum += g
                n++
                if (s.canKmh < canMin) canMin = s.canKmh
                if (s.canKmh > canMax) canMax = s.canKmh
            }
            if (n < 4) {
                i++
                continue
            }
            val windowSec = SPEED_WINDOW_MS / 1000f
            val accel = abs(canMax - canMin) / windowSec
            if (accel > MAX_ABS_ACCEL_KMH_S) {
                i++
                continue
            }
            val canAvg = (canSum / n).toFloat()
            val gnssAvg = (gnssSum / n).toFloat()
            if (canAvg < MIN_SPEED_KMH || gnssAvg < MIN_SPEED_KMH * 0.7f) {
                i++
                continue
            }
            val residual = abs(gnssAvg - canAvg)
            val maxResid = max(MAX_RESIDUAL_KMH, canAvg * MAX_RESIDUAL_FRAC)
            // After lag alignment a huge residual is a jump, not a scale offset we want.
            if (residual > maxResid && residual / canAvg > 0.35f) {
                i++
                continue
            }
            val ratio = gnssAvg / canAvg
            if (ratio in 0.7f..1.4f) out.add(ratio)
            i++
        }
        return out
    }

    fun median(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2f else s[m]
    }

    data class YawSegmentResult(
        val gyroIntegralDeg: Float,
        val gnssDeltaDeg: Float,
    )

    /**
     * Split yaw stream into turn segments and compare gyro integral vs lag-aligned GNSS course.
     * Nav bearing decreases on left (positive) yaw → gnssDelta ≈ −sign * scale * gyroIntegral.
     */
    fun collectYawSegments(
        samples: List<YawSample>,
        lagMs: Long,
    ): Pair<List<YawSegmentResult>, Int> {
        if (samples.size < 6) return emptyList<YawSegmentResult>() to 0
        val out = ArrayList<YawSegmentResult>()
        var rejected = 0
        var i = 0
        while (i < samples.size - 2) {
            val start = samples[i]
            if (start.speedKmh < MIN_SPEED_KMH || abs(start.yawRateDegPerSec) < 1.5f) {
                i++
                continue
            }
            var gyroSum = 0f
            var j = i
            val endLimit = start.elapsedMs + YAW_SEGMENT_MAX_MS
            while (j + 1 < samples.size && samples[j + 1].elapsedMs <= endLimit) {
                val a = samples[j]
                val b = samples[j + 1]
                val dt = (b.elapsedMs - a.elapsedMs) / 1000f
                if (dt <= 0f || dt > 0.5f) break
                if (b.speedKmh < MIN_SPEED_KMH * 0.8f) break
                gyroSum += a.yawRateDegPerSec * dt
                j++
                if (abs(gyroSum) >= MIN_TURN_ABS_DEG) break
            }
            if (j <= i || abs(gyroSum) < MIN_TURN_ABS_DEG) {
                i++
                continue
            }
            val end = samples[j]
            val b0 = interpolateBearing(samples, start.elapsedMs + lagMs)
            val b1 = interpolateBearing(samples, end.elapsedMs + lagMs)
            if (b0 == null || b1 == null) {
                rejected++
                i = j + 1
                continue
            }
            val gnssDelta = wrapDeltaDeg(b0, b1)
            if (abs(gnssDelta) < MIN_TURN_ABS_DEG * 0.45f) {
                rejected++
                i = j + 1
                continue
            }
            val magRatio = abs(gnssDelta / gyroSum)
            if (magRatio !in 0.4f..2.2f) {
                rejected++
                i = j + 1
                continue
            }
            out.add(YawSegmentResult(gyroIntegralDeg = gyroSum, gnssDeltaDeg = gnssDelta))
            i = j + 1
        }
        return out to rejected
    }

    fun estimateYawScaleAndSign(segments: List<YawSegmentResult>): Pair<Float, Int>? {
        if (segments.size < MIN_YAW_FOR_ESTIMATE) return null
        val scalesPos = ArrayList<Float>()
        val scalesNeg = ArrayList<Float>()
        for (s in segments) {
            if (abs(s.gyroIntegralDeg) < 1f) continue
            val sp = -s.gnssDeltaDeg / s.gyroIntegralDeg
            if (sp in 0.5f..1.8f) scalesPos.add(sp)
            val sn = s.gnssDeltaDeg / s.gyroIntegralDeg
            if (sn in 0.5f..1.8f) scalesNeg.add(sn)
        }
        val medPos = median(scalesPos)
        val medNeg = median(scalesNeg)
        return when {
            medPos != null && scalesPos.size >= MIN_YAW_FOR_ESTIMATE &&
                (medNeg == null || scalesPos.size >= scalesNeg.size) ->
                medPos to 1
            medNeg != null && scalesNeg.size >= MIN_YAW_FOR_ESTIMATE ->
                medNeg to -1
            else -> null
        }
    }

    fun speedFill(sampleCount: Int, buckets: Int, lagStability: Float = 1f): Float {
        val a = sampleCount.toFloat() / SPEED_SAMPLES_TARGET
        val b = buckets.toFloat() / SPEED_BUCKETS_TARGET
        val base = min(1f, min(a, b) * 0.5f + max(a, b) * 0.5f).coerceIn(0f, 1f)
        return (base * lagStability.coerceIn(0.35f, 1f)).coerceIn(0f, 1f)
    }

    fun yawFill(segmentCount: Int): Float =
        (segmentCount.toFloat() / YAW_SEGMENTS_TARGET).coerceIn(0f, 1f)

    fun buildEstimates(
        speedBuf: List<SpeedSample>,
        yawBuf: List<YawSample>,
    ): Estimates {
        val lag = estimateLagMs(speedBuf)
        val stability = lagStability(speedBuf)
        val ratios = collectSpeedRatios(speedBuf, lag)
        val bucketSet = HashSet<Int>()
        for (s in speedBuf) {
            if (s.canKmh >= MIN_SPEED_KMH) bucketSet.add(speedBucket(s.canKmh))
        }
        val speedScale = median(ratios) ?: 1f
        val (yawSegs, yawRejected) = collectYawSegments(yawBuf, lag)
        val yawEst = estimateYawScaleAndSign(yawSegs)
        val speedEstimated = ratios.size >= MIN_SPEED_FOR_ESTIMATE
        val yawEstimated = yawEst != null && yawSegs.size >= MIN_YAW_FOR_ESTIMATE
        return Estimates(
            lagMs = lag,
            lagStability = stability,
            speedScale = speedScale,
            yawScale = yawEst?.first ?: 1f,
            yawSign = yawEst?.second ?: 1,
            speedSampleCount = ratios.size,
            speedBuckets = bucketSet.size,
            yawSegmentCount = yawSegs.size,
            yawRejectedCount = yawRejected,
            speedFill = speedFill(ratios.size, bucketSet.size, stability),
            yawFill = yawFill(yawSegs.size),
            speedEstimated = speedEstimated,
            yawEstimated = yawEstimated,
        )
    }

    /** Course jump while gyro nearly flat → treat as bad GNSS course. */
    fun isCourseJump(
        prev: YawSample?,
        cur: YawSample,
    ): Boolean {
        if (prev == null) return false
        val dt = cur.elapsedMs - prev.elapsedMs
        if (dt <= 0L || dt > COURSE_JUMP_MAX_MS) return false
        val dBearing = abs(wrapDeltaDeg(prev.bearingDeg, cur.bearingDeg))
        val yawAbs = abs(cur.yawRateDegPerSec)
        return dBearing >= COURSE_JUMP_DEG && yawAbs <= COURSE_JUMP_MAX_YAW_ABS
    }

    enum class Hint {
        INTRO,
        WAIT_FIX,
        NO_CAN,
        NO_GYRO,
        COURSE_JUMP,
        SPEED_UP,
        HOLD_STEADY,
        TURN,
        SPEED_DONE_NEED_TURN,
        TURN_DONE_NEED_SPEED,
        READY,
        LOW_QUALITY,
    }

    enum class PauseKind {
        NONE,
        BAD_FIX,
        NO_CAN,
        NO_GYRO,
        COURSE_JUMP,
    }

    fun hint(
        estimates: Estimates,
        pause: PauseKind,
        hasSession: Boolean,
        previewLowQuality: Boolean = false,
    ): Hint {
        if (!hasSession) return Hint.INTRO
        if (previewLowQuality) return Hint.LOW_QUALITY
        when (pause) {
            PauseKind.BAD_FIX -> return Hint.WAIT_FIX
            PauseKind.NO_CAN -> return Hint.NO_CAN
            PauseKind.NO_GYRO -> return Hint.NO_GYRO
            PauseKind.COURSE_JUMP -> return Hint.COURSE_JUMP
            PauseKind.NONE -> Unit
        }
        if (estimates.ready) return Hint.READY
        if (estimates.speedFill >= 1f && estimates.yawFill < 1f) return Hint.SPEED_DONE_NEED_TURN
        if (estimates.yawFill >= 1f && estimates.speedFill < 1f) return Hint.TURN_DONE_NEED_SPEED
        if (estimates.speedFill < 0.3f) return Hint.SPEED_UP
        if (estimates.yawFill < 0.3f) return Hint.TURN
        return Hint.HOLD_STEADY
    }

    /**
     * Merge session estimates with previously saved offsets: keep old scale when
     * this session did not produce a reliable estimate for that channel.
     */
    fun mergeWithPrevious(
        estimates: Estimates,
        previous: DriveCalibrationOffsets,
        nowEpochMs: Long,
    ): DriveCalibrationOffsets {
        val speedScale = if (estimates.speedEstimated) {
            estimates.speedScale.coerceIn(0.7f, 1.4f)
        } else {
            previous.speedScale
        }
        val yawScale = if (estimates.yawEstimated) {
            estimates.yawScale.coerceIn(0.5f, 1.8f)
        } else {
            previous.yawScale
        }
        val yawSign = if (estimates.yawEstimated) {
            if (estimates.yawSign < 0) -1 else 1
        } else {
            previous.yawSign
        }
        return DriveCalibrationOffsets(
            speedScale = speedScale,
            yawScale = yawScale,
            yawSign = yawSign,
            lagMs = estimates.lagMs,
            calibratedAtEpochMs = nowEpochMs,
            speedEstimated = estimates.speedEstimated,
            yawEstimated = estimates.yawEstimated,
        )
    }
}
