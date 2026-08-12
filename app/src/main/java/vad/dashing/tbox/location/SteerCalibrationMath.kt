package vad.dashing.tbox.location

import kotlin.math.abs

/**
 * Batch road calibration for steering→heading (bicycle **tan** model): a
 * four-knot speed-dependent wheel→road scale + sign vs GNSS. Per-arc scale is
 * fitted so predicted
 * `∫ (v/L)·tan(scale·δ_eff)·dt` matches GNSS Δcourse (same formula as runtime).
 *
 * Progress bars must use **successfully fitted** L/R counts (not merely collected
 * coarse segments) — otherwise the UI can show a full bar with an empty draft.
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
    /** At least three of the four 20/40/60/80 km/h knots need fitted arcs. */
    const val MIN_PROFILE_SPEED_BUCKETS = 3
    /**
     * Reject if trimmed (max−min)/median of per-arc scales exceeds this.
     * Trim drops one extreme on each end when n≥6 so a single noisy GNSS arc
     * does not block an otherwise consistent set.
     */
    const val MAX_SCALE_RELATIVE_SPREAD = 0.40f
    /** Keep scales within this relative deviation of the tentative median. */
    const val MAX_SCALE_REL_DEV_FROM_MEDIAN = 0.40f
    const val SEGMENT_MAX_MS = 12_000L
    /**
     * Do not open a turn candidate until |centered wheel| exceeds this
     * (above soft deadzone). Prevents straight / tiny-wiggle windows from
     * being counted as «отброшено».
     */
    const val MIN_STEER_ABS_DEG_TO_START = 10f
    /**
     * If unit-path already looks like a turn but GNSS course is still flat
     * after this long — abandon without counting a reject (held slight steer
     * on an almost-straight road).
     */
    const val STEER_GNSS_STALL_MS = 4_000L

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
        val scaleProfile: SteerScaleProfile,
        val segmentCount: Int,
        val leftCount: Int = 0,
        val rightCount: Int = 0,
    ) {
        val scale: Float
            get() = scaleProfile.at40Kmh
    }

    enum class SteerEstimateFailure {
        NEED_MORE_ARCS,
        NEED_BOTH_SIDES,
        FIT_QUALITY,
        SPREAD,
        NEED_SPEED_RANGE,
    }

    /**
     * Full attempt result for UI: progress uses [fittedLeft]/[fittedRight],
     * draft uses [estimate], hint uses [failure].
     */
    data class SteerScaleAttempt(
        val estimate: SteerScaleEstimate?,
        val fittedLeft: Int,
        val fittedRight: Int,
        val collectedLeft: Int,
        val collectedRight: Int,
        val profileSpeedBuckets: Int = 0,
        val failure: SteerEstimateFailure? = null,
    )

    private data class ScaleObservation(
        val segment: SteerSegmentResult,
        val scale: Float,
        val speedKmh: Float,
    )

    fun steerFill(leftCount: Int, rightCount: Int): Float {
        val l = sideFill(leftCount)
        val r = sideFill(rightCount)
        return ((l + r) * 0.5f).coerceIn(0f, 1f)
    }

    /** Progress for one turn side (0…1). */
    fun sideFill(count: Int): Float =
        (count.toFloat() / MIN_SEGMENTS_PER_SIDE).coerceIn(0f, 1f)

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

    /**
     * Drop one extreme on each end when n≥6 so a single outlier does not
     * dominate (max−min)/median.
     */
    fun trimmedRelativeSpread(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val sorted = values.sorted()
        val work = if (sorted.size >= 6) {
            sorted.subList(1, sorted.size - 1)
        } else {
            sorted
        }
        return relativeSpread(work)
    }

    /** Keep scales near the tentative median (rejects wild GNSS arcs). */
    fun filterConsistentScales(
        scales: List<Float>,
        maxRelDev: Float = MAX_SCALE_REL_DEV_FROM_MEDIAN,
    ): List<Float> {
        val med = median(scales) ?: return emptyList()
        if (abs(med) < 1e-6f) return emptyList()
        return scales.filter { abs(it - med) / abs(med) <= maxRelDev }
    }

    fun collectSteerSegments(samples: List<SteerSample>): Pair<List<SteerSegmentResult>, Int> {
        if (samples.size < 4) return emptyList<SteerSegmentResult>() to 0
        val out = ArrayList<SteerSegmentResult>()
        var rejected = 0
        var i = 0
        while (i < samples.size - 2) {
            // Skip standstill and near-center wheel — not a turn attempt.
            while (
                i < samples.size &&
                (
                    samples[i].speedKmh < MIN_SPEED_KMH ||
                        abs(samples[i].centeredSteerDeg) < MIN_STEER_ABS_DEG_TO_START
                    )
            ) {
                i++
            }
            if (i >= samples.size - 2) break
            val start = samples[i]
            val steps = ArrayList<PathStep>()
            var pathSum = 0f
            var j = i + 1
            var prev = start
            var stalledFlatGnss = false
            while (j < samples.size) {
                val s = samples[j]
                if (s.speedKmh < MIN_SPEED_KMH * 0.5f) break
                // Wheel returned to center — end candidate (may still be too short).
                if (abs(s.centeredSteerDeg) < MIN_STEER_ABS_DEG_TO_START * 0.5f &&
                    abs(pathSum) < MIN_TURN_ABS_DEG * 0.5f
                ) {
                    break
                }
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
                // Slight hold on a nearly straight road: path grows, course does not.
                if (abs(pathSum) >= MIN_TURN_ABS_DEG * 0.5f &&
                    gnssSoFar < MIN_TURN_ABS_DEG * 0.15f &&
                    s.elapsedMs - start.elapsedMs >= STEER_GNSS_STALL_MS
                ) {
                    stalledFlatGnss = true
                    break
                }
                if (s.elapsedMs - start.elapsedMs > SEGMENT_MAX_MS) break
                j++
            }
            if (j >= samples.size) break
            val end = samples[j]
            val gnssDelta = wrapDeltaDeg(start.bearingDeg, end.bearingDeg)
            val weakPath = steps.isEmpty() || abs(pathSum) < MIN_TURN_ABS_DEG * 0.25f
            val weakGnss = abs(gnssDelta) < MIN_TURN_ABS_DEG * 0.4f
            if (weakPath || weakGnss || stalledFlatGnss) {
                // Incomplete / straight / tiny wiggle — do NOT count as discarded turn
                // (same spirit as gyro: only quality-fail real arcs).
                i = if (j > i) j else i + 1
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

    /** Curvature/path-weighted speed represented by one fitted turn arc. */
    fun representativeSpeedKmh(steps: List<PathStep>): Float {
        var weightedSpeed = 0f
        var weightSum = 0f
        for (step in steps) {
            val speedKmh = abs(step.speedMps) * 3.6f
            val weight = abs(step.centeredSteerDeg) * abs(step.speedMps) * step.dtSec
            if (speedKmh.isFinite() && weight.isFinite() && weight > 0f) {
                weightedSpeed += speedKmh * weight
                weightSum += weight
            }
        }
        return if (weightSum > 0f) weightedSpeed / weightSum else 0f
    }

    private fun observationsForSign(
        segments: List<SteerSegmentResult>,
        sign: Int,
        deadzoneDeg: Float,
    ): List<ScaleObservation> = segments.mapNotNull { segment ->
        fitScaleForSegment(segment, sign, deadzoneDeg)?.let { scale ->
            ScaleObservation(segment, scale, representativeSpeedKmh(segment.steps))
        }
    }

    private fun nearestProfileKnotIndex(speedKmh: Float): Int =
        SteerScaleProfile.SPEED_KNOTS_KMH.indices.minByOrNull { index ->
            abs(speedKmh - SteerScaleProfile.SPEED_KNOTS_KMH[index])
        } ?: 0

    /**
     * Reject scale outliers against other arcs in the same speed band. A single
     * global median would incorrectly reject the speed dependence being fitted.
     */
    private fun consistentProfileObservations(
        observations: List<ScaleObservation>,
    ): List<ScaleObservation> {
        val out = ArrayList<ScaleObservation>()
        for (index in SteerScaleProfile.SPEED_KNOTS_KMH.indices) {
            val bucket = observations.filter { nearestProfileKnotIndex(it.speedKmh) == index }
            val median = median(bucket.map { it.scale })
            if (median == null || abs(median) < 1e-6f) continue
            out += bucket.filter {
                abs(it.scale - median) / abs(median) <= MAX_SCALE_REL_DEV_FROM_MEDIAN
            }
        }
        return out
    }

    private fun profileFromObservations(
        observations: List<ScaleObservation>,
    ): Pair<SteerScaleProfile?, Int> {
        val knots = SteerScaleProfile.SPEED_KNOTS_KMH
        val medians = MutableList<Float?>(knots.size) { null }
        for (index in knots.indices) {
            val bucket = observations
                .filter { obs -> nearestProfileKnotIndex(obs.speedKmh) == index }
                .map { it.scale }
            medians[index] = median(bucket)
        }
        val populated = medians.count { it != null }
        if (populated < MIN_PROFILE_SPEED_BUCKETS) return null to populated

        fun valueAt(index: Int): Float {
            medians[index]?.let { return it.coerceIn(SCALE_MIN, SCALE_MAX) }
            val lower = (index - 1 downTo 0).firstOrNull { medians[it] != null }
            val upper = ((index + 1)..knots.lastIndex).firstOrNull { medians[it] != null }
            val value = when {
                lower != null && upper != null -> {
                    val t = (knots[index] - knots[lower]) / (knots[upper] - knots[lower])
                    medians[lower]!! + (medians[upper]!! - medians[lower]!!) * t
                }
                lower != null -> medians[lower]!!
                upper != null -> medians[upper]!!
                else -> SteerHeadingIntegrator.DEFAULT_SCALE
            }
            return value.coerceIn(SCALE_MIN, SCALE_MAX)
        }

        return SteerScaleProfile(
            at20Kmh = valueAt(0),
            at40Kmh = valueAt(1),
            at60Kmh = valueAt(2),
            at80Kmh = valueAt(3),
        ) to populated
    }

    fun estimateSteerScaleAndSign(
        segments: List<SteerSegmentResult>,
        deadzoneDeg: Float = SteerCalibrationStore.offsets.deadzoneDeg,
    ): SteerScaleEstimate? = attemptSteerScaleAndSign(segments, deadzoneDeg).estimate

    fun attemptSteerScaleAndSign(
        segments: List<SteerSegmentResult>,
        deadzoneDeg: Float = SteerCalibrationStore.offsets.deadzoneDeg,
    ): SteerScaleAttempt {
        val (collectedLeft, collectedRight) = countSides(segments)
        if (segments.size < MIN_SEGMENTS_FOR_ESTIMATE) {
            return SteerScaleAttempt(
                estimate = null,
                fittedLeft = 0,
                fittedRight = 0,
                collectedLeft = collectedLeft,
                collectedRight = collectedRight,
                failure = SteerEstimateFailure.NEED_MORE_ARCS,
            )
        }

        val posObservations = observationsForSign(segments, 1, deadzoneDeg)
        val negObservations = observationsForSign(segments, -1, deadzoneDeg)
        val pos = posObservations.map { it.scale }
        val neg = negObservations.map { it.scale }
        val steerSign = when {
            pos.size >= MIN_SEGMENTS_FOR_ESTIMATE &&
                (neg.size < MIN_SEGMENTS_FOR_ESTIMATE || pos.size >= neg.size) -> 1
            neg.size >= MIN_SEGMENTS_FOR_ESTIMATE -> -1
            else -> null
        }
        if (steerSign == null) {
            // Show best-effort fitted sides under the more populous sign for progress.
            val trySign = when {
                pos.size >= neg.size && pos.isNotEmpty() -> 1
                neg.isNotEmpty() -> -1
                else -> 1
            }
            val (fl, fr) = fittedSideCounts(segments, trySign, deadzoneDeg)
            return SteerScaleAttempt(
                estimate = null,
                fittedLeft = fl,
                fittedRight = fr,
                collectedLeft = collectedLeft,
                collectedRight = collectedRight,
                failure = SteerEstimateFailure.FIT_QUALITY,
            )
        }

        val observations = consistentProfileObservations(
            if (steerSign > 0) posObservations else negObservations,
        )
        val leftScales = ArrayList<Float>()
        val rightScales = ArrayList<Float>()
        for (observation in observations) {
            if (observation.segment.pathIntegralDeg >= 0f) {
                leftScales.add(observation.scale)
            } else {
                rightScales.add(observation.scale)
            }
        }
        val fittedLeft = leftScales.size
        val fittedRight = rightScales.size
        if (fittedLeft < MIN_SEGMENTS_PER_SIDE || fittedRight < MIN_SEGMENTS_PER_SIDE) {
            return SteerScaleAttempt(
                estimate = null,
                fittedLeft = fittedLeft,
                fittedRight = fittedRight,
                collectedLeft = collectedLeft,
                collectedRight = collectedRight,
                failure = if (leftScales.size < MIN_SEGMENTS_PER_SIDE ||
                    rightScales.size < MIN_SEGMENTS_PER_SIDE
                ) {
                    SteerEstimateFailure.NEED_BOTH_SIDES
                } else {
                    SteerEstimateFailure.FIT_QUALITY
                },
            )
        }
        val (profile, profileBuckets) = profileFromObservations(observations)
        if (profile == null) {
            return SteerScaleAttempt(
                estimate = null,
                fittedLeft = fittedLeft,
                fittedRight = fittedRight,
                collectedLeft = collectedLeft,
                collectedRight = collectedRight,
                profileSpeedBuckets = profileBuckets,
                failure = SteerEstimateFailure.NEED_SPEED_RANGE,
            )
        }
        return SteerScaleAttempt(
            estimate = SteerScaleEstimate(
                sign = steerSign,
                scaleProfile = profile,
                segmentCount = fittedLeft + fittedRight,
                leftCount = fittedLeft,
                rightCount = fittedRight,
            ),
            fittedLeft = fittedLeft,
            fittedRight = fittedRight,
            collectedLeft = collectedLeft,
            collectedRight = collectedRight,
            profileSpeedBuckets = profileBuckets,
            failure = null,
        )
    }

    private fun fittedSideCounts(
        segments: List<SteerSegmentResult>,
        sign: Int,
        deadzoneDeg: Float,
    ): Pair<Int, Int> {
        var left = 0
        var right = 0
        val observations = consistentProfileObservations(
            observationsForSign(segments, sign, deadzoneDeg),
        )
        val leftScales = ArrayList<Float>()
        val rightScales = ArrayList<Float>()
        for (observation in observations) {
            if (observation.segment.pathIntegralDeg >= 0f) {
                leftScales.add(observation.scale)
            } else {
                rightScales.add(observation.scale)
            }
        }
        left = leftScales.size
        right = rightScales.size
        return left to right
    }

    fun mergeWithPrevious(
        estimate: SteerScaleEstimate,
        previous: SteerCalibrationOffsets,
        nowEpochMs: Long,
    ): SteerCalibrationOffsets {
        return previous.copy(
            scaleProfile = estimate.scaleProfile,
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

    fun migrateWheelbase(stored: Float?): Float {
        if (stored == null || !stored.isFinite() || stored <= 0f) {
            return SteerHeadingIntegrator.DEFAULT_WHEELBASE_M
        }
        return stored.coerceIn(
            SteerCalibrationOffsets.WHEELBASE_EDIT_MIN,
            SteerCalibrationOffsets.WHEELBASE_EDIT_MAX,
        )
    }
}
