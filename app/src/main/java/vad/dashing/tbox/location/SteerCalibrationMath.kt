package vad.dashing.tbox.location

import kotlin.math.abs

/**
 * Batch road calibration for steering→heading scale L/R + sign vs GNSS course.
 * Mirrors [DriveCalibrationMath] yaw-segment logic, with wider scale bounds
 * (wheel angle ≫ vehicle yaw).
 */
object SteerCalibrationMath {
    const val MIN_SPEED_KMH = 15f
    const val MIN_TURN_ABS_DEG = 20f
    const val MIN_SEGMENTS_FOR_ESTIMATE = 2
    /** Steering-wheel ° → heading ° is often ≪ 1 (ratio ~10–20:1). */
    const val SCALE_MIN = 0.02f
    const val SCALE_MAX = 1.5f

    data class SteerSample(
        val centeredSteerDeg: Float,
        val bearingDeg: Float,
        val speedKmh: Float,
        val elapsedMs: Long,
    )

    data class SteerSegmentResult(
        val steerIntegralDeg: Float,
        val gnssDeltaDeg: Float,
    )

    data class SteerScaleEstimate(
        val sign: Int,
        val scaleLeft: Float?,
        val scaleRight: Float?,
        val leftCount: Int,
        val rightCount: Int,
        val segmentCount: Int,
    ) {
        val hasAny: Boolean get() = scaleLeft != null || scaleRight != null
        val meanScale: Float
            get() {
                val parts = listOfNotNull(scaleLeft, scaleRight)
                return if (parts.isEmpty()) 1f else parts.sum() / parts.size
            }
    }

    fun wrapDeltaDeg(fromDeg: Float, toDeg: Float): Float {
        var d = toDeg - fromDeg
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    fun median(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) * 0.5f
    }

    /**
     * Build turn segments: accumulate Δcentered steer while moving; close when
     * |∫steer| and |ΔGNSS| are large enough and ratio is plausible.
     */
    fun collectSteerSegments(samples: List<SteerSample>): Pair<List<SteerSegmentResult>, Int> {
        if (samples.size < 4) return emptyList<SteerSegmentResult>() to 0
        val out = ArrayList<SteerSegmentResult>()
        var rejected = 0
        var i = 0
        while (i < samples.size - 2) {
            while (i < samples.size && samples[i].speedKmh < MIN_SPEED_KMH) i++
            if (i >= samples.size - 2) break
            val start = samples[i]
            var steerSum = 0f
            var j = i + 1
            var lastCentered = start.centeredSteerDeg
            while (j < samples.size) {
                val s = samples[j]
                if (s.speedKmh < MIN_SPEED_KMH * 0.5f) break
                val dSteer = s.centeredSteerDeg - lastCentered
                lastCentered = s.centeredSteerDeg
                if (dSteer.isFinite() && abs(dSteer) <= SteerHeadingIntegrator.MAX_ABS_DELTA_DEG) {
                    steerSum += dSteer
                }
                val gnssSoFar = abs(wrapDeltaDeg(start.bearingDeg, s.bearingDeg))
                if (abs(steerSum) >= MIN_TURN_ABS_DEG && gnssSoFar >= MIN_TURN_ABS_DEG * 0.45f) {
                    break
                }
                // Cap segment length ~8 s
                if (s.elapsedMs - start.elapsedMs > 8_000L) break
                j++
            }
            if (j >= samples.size) break
            val end = samples[j]
            val gnssDelta = wrapDeltaDeg(start.bearingDeg, end.bearingDeg)
            if (abs(steerSum) < MIN_TURN_ABS_DEG * 0.5f || abs(gnssDelta) < MIN_TURN_ABS_DEG * 0.4f) {
                rejected++
                i = j + 1
                continue
            }
            val magRatio = abs(gnssDelta / steerSum)
            if (magRatio !in SCALE_MIN..SCALE_MAX) {
                rejected++
                i = j + 1
                continue
            }
            out.add(SteerSegmentResult(steerIntegralDeg = steerSum, gnssDeltaDeg = gnssDelta))
            i = j + 1
        }
        return out to rejected
    }

    fun estimateSteerScalesAndSign(segments: List<SteerSegmentResult>): SteerScaleEstimate? {
        if (segments.size < MIN_SEGMENTS_FOR_ESTIMATE) return null
        val scalesPos = ArrayList<Float>()
        val scalesNeg = ArrayList<Float>()
        for (s in segments) {
            if (abs(s.steerIntegralDeg) < 1f) continue
            // sign=+1: bearingDelta = −scale · steerIntegral → scale = −gnss/steer
            val sp = -s.gnssDeltaDeg / s.steerIntegralDeg
            if (sp in SCALE_MIN..SCALE_MAX) scalesPos.add(sp)
            val sn = s.gnssDeltaDeg / s.steerIntegralDeg
            if (sn in SCALE_MIN..SCALE_MAX) scalesNeg.add(sn)
        }
        val medPos = median(scalesPos)
        val medNeg = median(scalesNeg)
        val steerSign = when {
            medPos != null && scalesPos.size >= MIN_SEGMENTS_FOR_ESTIMATE &&
                (medNeg == null || scalesPos.size >= scalesNeg.size) -> 1
            medNeg != null && scalesNeg.size >= MIN_SEGMENTS_FOR_ESTIMATE -> -1
            else -> return null
        }
        val leftScales = ArrayList<Float>()
        val rightScales = ArrayList<Float>()
        for (s in segments) {
            if (abs(s.steerIntegralDeg) < 1f) continue
            val scale = if (steerSign < 0) {
                s.gnssDeltaDeg / s.steerIntegralDeg
            } else {
                -s.gnssDeltaDeg / s.steerIntegralDeg
            }
            if (scale !in SCALE_MIN..SCALE_MAX) continue
            if (s.steerIntegralDeg >= 0f) leftScales.add(scale) else rightScales.add(scale)
        }
        val scaleLeft = median(leftScales)
        val scaleRight = median(rightScales)
        if (scaleLeft == null && scaleRight == null) return null
        return SteerScaleEstimate(
            sign = steerSign,
            scaleLeft = scaleLeft,
            scaleRight = scaleRight,
            leftCount = leftScales.size,
            rightCount = rightScales.size,
            segmentCount = leftScales.size + rightScales.size,
        )
    }

    fun mergeWithPrevious(
        estimate: SteerScaleEstimate,
        previous: SteerCalibrationOffsets,
        nowEpochMs: Long,
    ): SteerCalibrationOffsets {
        fun clamp(v: Float): Float = v.coerceIn(SCALE_MIN, SCALE_MAX)
        return previous.copy(
            scaleLeft = estimate.scaleLeft?.let { clamp(it) } ?: previous.scaleLeft,
            scaleRight = estimate.scaleRight?.let { clamp(it) } ?: previous.scaleRight,
            sign = if (estimate.sign < 0) -1 else 1,
            calibratedAtEpochMs = nowEpochMs,
            scaleEstimated = estimate.hasAny,
        )
    }
}
