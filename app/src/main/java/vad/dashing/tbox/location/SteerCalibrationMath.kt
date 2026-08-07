package vad.dashing.tbox.location

import kotlin.math.abs

/**
 * Batch road calibration for steering→heading (bicycle **tan** model): **one**
 * wheel→road scale + sign vs GNSS. Scale is fitted so predicted
 * `∫ (v/L)·tan(scale·δ_eff)·dt` matches GNSS Δcourse (same formula as runtime).
 */
object SteerCalibrationMath {
    const val MIN_SPEED_KMH = 15f
    /** Longer arcs than before — reduces over-rotation from short noisy segments. */
    const val MIN_TURN_ABS_DEG = 30f
    /** Minimum accepted turn arcs **per side** (left and right both required). */
    const val MIN_SEGMENTS_PER_SIDE = 5
    const val MIN_SEGMENTS_FOR_ESTIMATE = MIN_SEGMENTS_PER_SIDE * 2
    const val STEER_SEGMENTS_TARGET = MIN_SEGMENTS_FOR_ESTIMATE
    const val SCALE_MIN = 0.02f
    const val SCALE_MAX = 0.35f
    /** Reject if (max−min)/median of per-arc scales exceeds this. */
    const val MAX_SCALE_RELATIVE_SPREAD = 0.35f
    const val SEGMENT_MAX_MS = 12_000L

    data class SteerSample(
        val centeredSteerDeg: Float,
        val bearingDeg: Float,
        val speedKmh: Float,
        val elapsedMs: Long,
    )

    data class PathStep(
        val centeredSteerDeg: Float,
        val speedMps: Float,
        val dtSec: Float,
    )

    data class SteerSegmentResult(
        val steps: List<PathStep>,
        val gnssDeltaDeg: Float,
        /** Coarse linear unit path for gates / side sign. */
        val pathIntegralDeg: Float,
    )

    data class SteerScaleEstimate(
        val sign: Int,
        val scale: Float,
        val segmentCount: Int,
        val leftCount: Int = 0,
        val rightCount: Int = 0,
    )

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

    fun relativeSpread(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val med = median(values) ?: return Float.POSITIVE_INFINITY
        if (abs(med) < 1e-6f) return Float.POSITIVE_INFINITY
        val minV = values.minOrNull() ?: return Float.POSITIVE_INFINITY
        val maxV = values.maxOrNull() ?: return Float.POSITIVE_INFINITY
        return (maxV - minV) / abs(med)
    }

    fun collectSteerSegments(samples: List<SteerSample>): Pair<List<SteerSegmentResult>, Int> {
        if (samples.size < 4) return emptyList<SteerSegmentResult>() to 0
        val out = ArrayList<SteerSegmentResult>()
        var rejected = 0
        var i = 0
        while (i < samples.size - 2) {
            while (i < samples.size && samples[i].speedKmh < MIN_SPEED_KMH) i++
            if (i >= samples.size - 2) break
            val start = samples[i]
            val steps = ArrayList<PathStep>()
            var pathSum = 0f
            var j = i + 1
            var prev = start
            while (j < samples.size) {
                val s = samples[j]
                if (s.speedKmh < MIN_SPEED_KMH * 0.5f) break
                val dt = (s.elapsedMs - prev.elapsedMs) / 1000f
                if (dt > 0f && dt <= SteerHeadingIntegrator.MAX_SAMPLE_DT_SEC) {
                    val vMps = prev.speedKmh / 3.6f
                    val elem = SteerHeadingIntegrator.pathElementDeg(
                        centeredWheelDeg = prev.centeredSteerDeg,
                        speedMps = vMps,
                        dtSec = dt,
                    )
                    pathSum += elem
                    steps.add(
                        PathStep(
                            centeredSteerDeg = prev.centeredSteerDeg,
                            speedMps = vMps,
                            dtSec = dt,
                        ),
                    )
                }
                prev = s
                val gnssSoFar = abs(wrapDeltaDeg(start.bearingDeg, s.bearingDeg))
                if (abs(pathSum) >= MIN_TURN_ABS_DEG * 0.5f &&
                    gnssSoFar >= MIN_TURN_ABS_DEG * 0.45f
                ) {
                    break
                }
                if (s.elapsedMs - start.elapsedMs > SEGMENT_MAX_MS) break
                j++
            }
            if (j >= samples.size) break
            val end = samples[j]
            val gnssDelta = wrapDeltaDeg(start.bearingDeg, end.bearingDeg)
            if (steps.isEmpty() ||
                abs(pathSum) < MIN_TURN_ABS_DEG * 0.25f ||
                abs(gnssDelta) < MIN_TURN_ABS_DEG * 0.4f
            ) {
                rejected++
                i = j + 1
                continue
            }
            // Coarse linear ratio gate (order-of-magnitude); fine fit uses tan.
            val magRatio = abs(gnssDelta / pathSum)
            if (magRatio !in (SCALE_MIN * 0.5f)..(SCALE_MAX * 2f)) {
                rejected++
                i = j + 1
                continue
            }
            out.add(
                SteerSegmentResult(
                    steps = steps.toList(),
                    gnssDeltaDeg = gnssDelta,
                    pathIntegralDeg = pathSum,
                ),
            )
            i = j + 1
        }
        return out to rejected
    }

    fun countSides(segments: List<SteerSegmentResult>): Pair<Int, Int> {
        var left = 0
        var right = 0
        for (s in segments) {
            if (s.pathIntegralDeg >= 0f) left++ else right++
        }
        return left to right
    }

    /** Predicted nav bearing delta (°) with given scale/sign using runtime tan model. */
    fun predictGnssDelta(
        steps: List<PathStep>,
        scale: Float,
        sign: Int,
        deadzoneDeg: Float = SteerCalibrationStore.offsets.deadzoneDeg,
    ): Float {
        var sum = 0f
        for (st in steps) {
            sum += SteerHeadingIntegrator.yawDeltaDeg(
                centeredWheelDeg = st.centeredSteerDeg,
                speedMps = st.speedMps,
                dtSec = st.dtSec.toDouble(),
                scale = scale,
                sign = sign,
                applyInternalDeadzone = true,
                deadzoneDeg = deadzoneDeg,
            )
        }
        return sum
    }

    /**
     * Fit wheel→road [scale] so tan-model prediction matches [gnssDeltaDeg]
     * (grid search — same formula as runtime).
     */
    fun fitScaleForSegment(
        segment: SteerSegmentResult,
        sign: Int,
        deadzoneDeg: Float = SteerCalibrationStore.offsets.deadzoneDeg,
    ): Float? {
        val target = segment.gnssDeltaDeg
        if (abs(target) < 1f || segment.steps.isEmpty()) return null
        var bestScale = Float.NaN
        var bestErr = Float.POSITIVE_INFINITY
        val steps = 48
        for (i in 0..steps) {
            val scale = SCALE_MIN + (SCALE_MAX - SCALE_MIN) * (i.toFloat() / steps)
            val pred = predictGnssDelta(segment.steps, scale, sign, deadzoneDeg)
            if (pred * target < 0f && abs(pred) > 1f) continue
            val err = abs(pred - target)
            if (err < bestErr) {
                bestErr = err
                bestScale = scale
            }
        }
        if (!bestScale.isFinite()) return null
        val maxErr = abs(target) * 0.30f + 2.5f
        return if (bestErr <= maxErr) bestScale else null
    }

    fun estimateSteerScaleAndSign(
        segments: List<SteerSegmentResult>,
        deadzoneDeg: Float = SteerCalibrationStore.offsets.deadzoneDeg,
    ): SteerScaleEstimate? {
        if (segments.size < MIN_SEGMENTS_FOR_ESTIMATE) return null

        fun scoresForSign(sign: Int): List<Float> {
            val out = ArrayList<Float>()
            for (s in segments) {
                fitScaleForSegment(s, sign, deadzoneDeg)?.let { out.add(it) }
            }
            return out
        }

        val pos = scoresForSign(1)
        val neg = scoresForSign(-1)
        val steerSign = when {
            pos.size >= MIN_SEGMENTS_FOR_ESTIMATE &&
                (neg.size < MIN_SEGMENTS_FOR_ESTIMATE || pos.size >= neg.size) -> 1
            neg.size >= MIN_SEGMENTS_FOR_ESTIMATE -> -1
            else -> return null
        }
        val fitted = if (steerSign < 0) neg else pos
        if (fitted.size < MIN_SEGMENTS_FOR_ESTIMATE) return null

        val leftScales = ArrayList<Float>()
        val rightScales = ArrayList<Float>()
        for (s in segments) {
            val sc = fitScaleForSegment(s, steerSign, deadzoneDeg) ?: continue
            if (s.pathIntegralDeg >= 0f) leftScales.add(sc) else rightScales.add(sc)
        }
        if (leftScales.size < MIN_SEGMENTS_PER_SIDE || rightScales.size < MIN_SEGMENTS_PER_SIDE) {
            return null
        }
        val all = leftScales + rightScales
        if (relativeSpread(all) > MAX_SCALE_RELATIVE_SPREAD) return null
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

    fun migrateScale(stored: Float): Float {
        if (!stored.isFinite() || stored <= 0f) return SteerHeadingIntegrator.DEFAULT_SCALE
        if (stored > SCALE_MAX) return SteerHeadingIntegrator.DEFAULT_SCALE
        return stored.coerceIn(SCALE_MIN, SCALE_MAX)
    }

    fun migrateDeadzone(stored: Float?): Float {
        if (stored == null || !stored.isFinite()) {
            return SteerCalibrationOffsets.DEFAULT_DEADZONE_DEG
        }
        return stored.coerceIn(
            SteerCalibrationOffsets.DEADZONE_MIN_DEG,
            SteerCalibrationOffsets.DEADZONE_MAX_DEG,
        )
    }
}
