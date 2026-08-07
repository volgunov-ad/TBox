package vad.dashing.tbox.location

import kotlin.math.abs

/**
 * Batch road calibration for steering→heading (bicycle model): **one** wheel→road
 * scale + sign vs GNSS. Segments compare GNSS Δcourse to
 * ∫ (v/L)·δ_wheel dt (linear unit path); estimated scale ≈ gnss / pathIntegral.
 */
object SteerCalibrationMath {
    const val MIN_SPEED_KMH = 15f
    const val MIN_TURN_ABS_DEG = 20f
    /** Minimum accepted turn arcs **per side** (left and right both required). */
    const val MIN_SEGMENTS_PER_SIDE = 2
    /** Total arcs gate (= 2×[MIN_SEGMENTS_PER_SIDE]). */
    const val MIN_SEGMENTS_FOR_ESTIMATE = MIN_SEGMENTS_PER_SIDE * 2
    /** Progress bar target (same as estimate gate). */
    const val STEER_SEGMENTS_TARGET = MIN_SEGMENTS_FOR_ESTIMATE
    /**
     * Wheel→road scale range (~1/50 … 1/3 steering ratio).
     * Legacy Δsteer scales (~1) are coerced down on load.
     */
    const val SCALE_MIN = 0.02f
    const val SCALE_MAX = 0.35f

    data class SteerSample(
        val centeredSteerDeg: Float,
        val bearingDeg: Float,
        val speedKmh: Float,
        val elapsedMs: Long,
    )

    data class SteerSegmentResult(
        /**
         * Unit path ∫ (v/L)·δ_wheel dt (°). Predicted heading ≈ scale · pathIntegral
         * (before nav sign).
         */
        val pathIntegralDeg: Float,
        val gnssDeltaDeg: Float,
    )

    data class SteerScaleEstimate(
        val sign: Int,
        val scale: Float,
        val segmentCount: Int,
        val leftCount: Int = 0,
        val rightCount: Int = 0,
    )

    /** Fill 0…1 from accepted left/right arc counts (both sides required for 1). */
    fun steerFill(leftCount: Int, rightCount: Int): Float {
        val l = (leftCount.toFloat() / MIN_SEGMENTS_PER_SIDE).coerceIn(0f, 1f)
        val r = (rightCount.toFloat() / MIN_SEGMENTS_PER_SIDE).coerceIn(0f, 1f)
        return ((l + r) * 0.5f).coerceIn(0f, 1f)
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
     * Build turn segments: accumulate unit path ∫(v/L)·δ while moving; close when
     * |path| and |ΔGNSS| are large enough and ratio is a plausible scale.
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
            var pathSum = 0f
            var j = i + 1
            var prev = start
            while (j < samples.size) {
                val s = samples[j]
                if (s.speedKmh < MIN_SPEED_KMH * 0.5f) break
                val dt = (s.elapsedMs - prev.elapsedMs) / 1000f
                if (dt > 0f && dt <= SteerHeadingIntegrator.MAX_SAMPLE_DT_SEC) {
                    val vMps = prev.speedKmh / 3.6f
                    pathSum += SteerHeadingIntegrator.pathElementDeg(
                        centeredWheelDeg = prev.centeredSteerDeg,
                        speedMps = vMps,
                        dtSec = dt,
                    )
                }
                prev = s
                val gnssSoFar = abs(wrapDeltaDeg(start.bearingDeg, s.bearingDeg))
                if (abs(pathSum) >= MIN_TURN_ABS_DEG * 0.5f &&
                    gnssSoFar >= MIN_TURN_ABS_DEG * 0.45f
                ) {
                    break
                }
                if (s.elapsedMs - start.elapsedMs > 8_000L) break
                j++
            }
            if (j >= samples.size) break
            val end = samples[j]
            val gnssDelta = wrapDeltaDeg(start.bearingDeg, end.bearingDeg)
            if (abs(pathSum) < MIN_TURN_ABS_DEG * 0.25f ||
                abs(gnssDelta) < MIN_TURN_ABS_DEG * 0.4f
            ) {
                rejected++
                i = j + 1
                continue
            }
            val magRatio = abs(gnssDelta / pathSum)
            if (magRatio !in SCALE_MIN..SCALE_MAX) {
                rejected++
                i = j + 1
                continue
            }
            out.add(SteerSegmentResult(pathIntegralDeg = pathSum, gnssDeltaDeg = gnssDelta))
            i = j + 1
        }
        return out to rejected
    }

    /** Count left (+) / right (−) arcs from unit path sign. */
    fun countSides(segments: List<SteerSegmentResult>): Pair<Int, Int> {
        var left = 0
        var right = 0
        for (s in segments) {
            if (s.pathIntegralDeg >= 0f) left++ else right++
        }
        return left to right
    }

    /** Single wheel→road scale + best sign; needs ≥2 arcs each side. */
    fun estimateSteerScaleAndSign(segments: List<SteerSegmentResult>): SteerScaleEstimate? {
        if (segments.size < MIN_SEGMENTS_FOR_ESTIMATE) return null
        val scalesPos = ArrayList<Float>()
        val scalesNeg = ArrayList<Float>()
        for (s in segments) {
            if (abs(s.pathIntegralDeg) < 1f) continue
            // sign=+1: bearingDelta = −scale · path → scale = −gnss/path
            val sp = -s.gnssDeltaDeg / s.pathIntegralDeg
            if (sp in SCALE_MIN..SCALE_MAX) scalesPos.add(sp)
            val sn = s.gnssDeltaDeg / s.pathIntegralDeg
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
            if (abs(s.pathIntegralDeg) < 1f) continue
            val scale = if (steerSign < 0) {
                s.gnssDeltaDeg / s.pathIntegralDeg
            } else {
                -s.gnssDeltaDeg / s.pathIntegralDeg
            }
            if (scale !in SCALE_MIN..SCALE_MAX) continue
            if (s.pathIntegralDeg >= 0f) leftScales.add(scale) else rightScales.add(scale)
        }
        if (leftScales.size < MIN_SEGMENTS_PER_SIDE || rightScales.size < MIN_SEGMENTS_PER_SIDE) {
            return null
        }
        val all = leftScales + rightScales
        val scale = median(all) ?: return null
        return SteerScaleEstimate(
            sign = steerSign,
            scale = scale,
            segmentCount = all.size,
            leftCount = leftScales.size,
            rightCount = rightScales.size,
        )
    }

    fun mergeWithPrevious(
        estimate: SteerScaleEstimate,
        previous: SteerCalibrationOffsets,
        nowEpochMs: Long,
    ): SteerCalibrationOffsets {
        return previous.copy(
            scale = estimate.scale.coerceIn(SCALE_MIN, SCALE_MAX),
            sign = if (estimate.sign < 0) -1 else 1,
            calibratedAtEpochMs = nowEpochMs,
            scaleEstimated = true,
        )
    }

    /**
     * Migrate stored scale from the old Δsteer model (often ~0.2–1.0) to a sane
     * wheel→road default when clearly out of range.
     */
    fun migrateScale(stored: Float): Float {
        if (!stored.isFinite() || stored <= 0f) return SteerHeadingIntegrator.DEFAULT_SCALE
        if (stored > SCALE_MAX) return SteerHeadingIntegrator.DEFAULT_SCALE
        return stored.coerceIn(SCALE_MIN, SCALE_MAX)
    }
}
