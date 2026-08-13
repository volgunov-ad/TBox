package vad.dashing.tbox.location

import kotlin.math.abs
import kotlin.math.tan

/**
 * High-rate steering→heading for mock DR using the bicycle / Ackermann model:
 *
 * `ψ̇ = (v / L) · tan(δ_road)`, `δ_road = scale(|v|) · δ_eff`
 * where `δ_eff` is soft-deadzoned centered wheel angle from [SteerCalibrationStore].
 * The store interpolates scale between 20/40/60/80 km/h knots.
 *
 * Holding a non-zero wheel angle while moving accumulates heading; returning the
 * wheel to center stops the turn (does **not** unwind heading).
 *
 * [tick] / [onSpeedKmh] chunk long holds (mock period 1–5 s) in steps of
 * [MAX_SAMPLE_DT_SEC], matching [SpeedIntegrator.flushTo]. Angle-sample gaps
 * larger than [MAX_SAMPLE_DT_SEC] still re-seed without inventing a stall turn.
 *
 * Held-wheel integration trusts an angle only for [MAX_ANGLE_SAMPLE_AGE_MS] after
 * the last real sample (A9 mbCAN often polls ~30 s). After that the hold is dropped
 * so GYRO_STEER falls back to gyro instead of turning on a frozen wheel reading.
 */
object SteerHeadingIntegrator {
    /** Default Jetour Dashing wheelbase (m); runtime uses [SteerCalibrationOffsets.wheelbaseM]. */
    const val DEFAULT_WHEELBASE_M = 2.72f

    /** Alias of [DEFAULT_WHEELBASE_M] for callers that expect a fixed constant. */
    const val WHEELBASE_M = DEFAULT_WHEELBASE_M

    /** Max |road-wheel| angle after scale (°). */
    const val MAX_ROAD_WHEEL_DEG = 40f

    /** Default deadzone; runtime uses [SteerCalibrationOffsets.deadzoneDeg]. */
    const val DEADZONE_WHEEL_DEG = SteerCalibrationOffsets.DEFAULT_DEADZONE_DEG

    /** Below this speed (m/s) do not integrate (standstill / creep). */
    const val MIN_SPEED_MPS = 0.4f

    /**
     * Max gap for one integration chunk.
     * Longer held intervals are filled in multiple chunks (see [tick]).
     * A single angle-sample jump larger than this re-seeds without integrating
     * (sensor stall / reconnect).
     */
    const val MAX_SAMPLE_DT_SEC = 0.5

    /**
     * Max age of the last real wheel-angle sample for held integration / hybrid trust.
     * Upper end of the 0.5–1 s band — covers one default 1 s mock period, then drops
     * the hold so a ~30 s mbCAN poll cannot keep turning the bicycle model.
     */
    const val MAX_ANGLE_SAMPLE_AGE_MS = 1_000L

    private val lock = Any()
    private var lastSampleElapsedMs: Long = 0L
    /** Wall of the last [onRawSample] / [onCenteredSample]; not advanced by [tick]. */
    private var lastAngleSampleElapsedMs: Long = 0L
    private var lastCenteredDeg: Float? = null
    private var lastSpeedMps: Float = 0f
    private var pendingDeltaDeg: Double = 0.0

    fun pendingDeltaDeg(): Float = synchronized(lock) { pendingDeltaDeg.toFloat() }

    fun lastSampleElapsedMs(): Long = synchronized(lock) { lastSampleElapsedMs }

    fun lastAngleSampleElapsedMs(): Long = synchronized(lock) { lastAngleSampleElapsedMs }

    /** True when a held centered angle exists and is not older than [MAX_ANGLE_SAMPLE_AGE_MS]. */
    fun isAngleFresh(nowElapsedMs: Long): Boolean = synchronized(lock) {
        if (nowElapsedMs <= 0L || lastCenteredDeg == null || lastAngleSampleElapsedMs <= 0L) {
            return false
        }
        return nowElapsedMs - lastAngleSampleElapsedMs <= MAX_ANGLE_SAMPLE_AGE_MS
    }

    fun reset() {
        synchronized(lock) {
            lastSampleElapsedMs = 0L
            lastAngleSampleElapsedMs = 0L
            lastCenteredDeg = null
            lastSpeedMps = 0f
            pendingDeltaDeg = 0.0
        }
    }

    /**
     * Update calibrated CAN/GNSS speed (km/h). Sign: negative = reverse.
     * When [elapsedMs] > 0 and a wheel angle is held, integrates heading up to
     * that timestamp with the previous speed (chunked), then stores the new speed.
     */
    fun onSpeedKmh(speedKmh: Float?, elapsedMs: Long = 0L) {
        synchronized(lock) {
            if (elapsedMs > 0L && lastCenteredDeg != null && lastSampleElapsedMs > 0L) {
                integrateHeldThroughLocked(elapsedMs)
            }
            val v = speedKmh?.takeIf { it.isFinite() } ?: 0f
            lastSpeedMps = v / 3.6f
        }
    }

    fun onRawSample(rawAngleDeg: Float?, elapsedMs: Long) {
        if (rawAngleDeg == null || !rawAngleDeg.isFinite() || elapsedMs <= 0L) {
            clearHold()
            return
        }
        val centered = SteerCalibrationStore.applyZero(rawAngleDeg) ?: run {
            clearHold()
            return
        }
        onCenteredSample(centered, elapsedMs)
    }

    fun onCenteredSample(centeredDeg: Float, elapsedMs: Long) {
        if (!centeredDeg.isFinite() || elapsedMs <= 0L) {
            clearHold()
            return
        }
        synchronized(lock) {
            integrateLockedSample(elapsedMs)
            lastCenteredDeg = centeredDeg
            lastAngleSampleElapsedMs = elapsedMs
        }
    }

    /**
     * Extend held-wheel integration up to [elapsedMs] in chunks of
     * [MAX_SAMPLE_DT_SEC] (covers mock periods where StateFlow does not re-emit
     * a constant steering angle). Stops and drops the hold once the angle is stale.
     */
    fun tick(elapsedMs: Long) {
        if (elapsedMs <= 0L) return
        synchronized(lock) {
            if (lastCenteredDeg == null) return
            integrateHeldThroughLocked(elapsedMs)
        }
    }

    fun consumeDeltaDeg(): Float {
        synchronized(lock) {
            val out = pendingDeltaDeg.toFloat()
            pendingDeltaDeg = 0.0
            return out
        }
    }

    fun discard() {
        synchronized(lock) {
            pendingDeltaDeg = 0.0
        }
    }

    /**
     * Drop pending delta and retire the held-angle interval through [elapsedMs].
     * Prevents a later [tick] from integrating across a live-GNSS / gated gap.
     * Clears the held angle when [elapsedMs] is past the freshness window.
     */
    fun discardThrough(elapsedMs: Long) {
        if (elapsedMs <= 0L) {
            discard()
            return
        }
        synchronized(lock) {
            pendingDeltaDeg = 0.0
            if (lastCenteredDeg != null && elapsedMs > lastSampleElapsedMs) {
                lastSampleElapsedMs = elapsedMs
            }
            if (lastAngleSampleElapsedMs > 0L &&
                elapsedMs - lastAngleSampleElapsedMs > MAX_ANGLE_SAMPLE_AGE_MS
            ) {
                lastCenteredDeg = null
                lastAngleSampleElapsedMs = 0L
            }
        }
    }

    /** Angle-sample path: skip gaps larger than [MAX_SAMPLE_DT_SEC] (stall). */
    private fun integrateLockedSample(elapsedMs: Long) {
        val prevT = lastSampleElapsedMs
        val prevC = lastCenteredDeg
        lastSampleElapsedMs = elapsedMs
        if (prevT <= 0L || elapsedMs < prevT || prevC == null) return
        val dtSec = (elapsedMs - prevT) / 1000.0
        if (dtSec <= 0.0) return
        if (dtSec > MAX_SAMPLE_DT_SEC) return
        accumulateStep(prevC, dtSec)
    }

    /**
     * Hold / speed / mock-tick path: fill in MAX_SAMPLE_DT_SEC chunks, but never
     * past [lastAngleSampleElapsedMs] + [MAX_ANGLE_SAMPLE_AGE_MS].
     */
    private fun integrateHeldThroughLocked(elapsedMs: Long) {
        val angleAt = lastAngleSampleElapsedMs
        if (lastCenteredDeg == null || angleAt <= 0L) return
        val freshUntil = angleAt + MAX_ANGLE_SAMPLE_AGE_MS
        val integrateTo = minOf(elapsedMs, freshUntil)
        if (integrateTo > lastSampleElapsedMs) {
            integrateLockedChunked(integrateTo)
        }
        if (elapsedMs > freshUntil) {
            // Drop frozen wheel angle; next real sample re-seeds.
            lastCenteredDeg = null
            lastAngleSampleElapsedMs = 0L
            if (elapsedMs > lastSampleElapsedMs) {
                lastSampleElapsedMs = elapsedMs
            }
        }
    }

    /** Hold / speed / mock-tick path: fill long intervals in MAX_SAMPLE_DT_SEC chunks. */
    private fun integrateLockedChunked(elapsedMs: Long) {
        val prevT = lastSampleElapsedMs
        val prevC = lastCenteredDeg ?: return
        if (prevT <= 0L || elapsedMs <= prevT) return
        var t = prevT
        while (elapsedMs > t) {
            val remainingSec = (elapsedMs - t) / 1000.0
            val dtSec = remainingSec.coerceAtMost(MAX_SAMPLE_DT_SEC)
            if (dtSec <= 0.0) break
            accumulateStep(prevC, dtSec)
            val advanceMs = (dtSec * 1000.0).toLong().coerceAtLeast(1L)
            val nextT = t + advanceMs
            t = if (nextT >= elapsedMs || dtSec >= remainingSec) {
                elapsedMs
            } else {
                nextT
            }
        }
        lastSampleElapsedMs = elapsedMs
    }

    private fun accumulateStep(centeredDeg: Float, dtSec: Double) {
        val speed = lastSpeedMps
        if (!speed.isFinite() || abs(speed) < MIN_SPEED_MPS) return
        pendingDeltaDeg += SteerCalibrationStore
            .yawDeltaDeg(centeredDeg, speed, dtSec)
            .toDouble()
    }

    private fun clearHold() {
        synchronized(lock) {
            lastSampleElapsedMs = 0L
            lastAngleSampleElapsedMs = 0L
            lastCenteredDeg = null
        }
    }

    /**
     * Unit path element for coarse gates: ∫ (v/L)·δ_eff dt (° if scale=1, linear).
     * Uses store soft deadzone and wheelbase.
     */
    fun pathElementDeg(centeredWheelDeg: Float, speedMps: Float, dtSec: Float): Float {
        if (!centeredWheelDeg.isFinite() || !speedMps.isFinite() || dtSec <= 0f) return 0f
        if (abs(speedMps) < MIN_SPEED_MPS) return 0f
        val d = SteerCalibrationStore.softDeadzone(centeredWheelDeg)
        if (d == 0f) return 0f
        val l = resolveWheelbaseM(SteerCalibrationStore.offsets.wheelbaseM)
        return (speedMps / l) * d * dtSec
    }

    /**
     * Heading delta (°) for one step with [tan] road-wheel model.
     * When [applyInternalDeadzone] is true, applies [SteerCalibrationStore.softDeadzone].
     */
    fun yawDeltaDeg(
        centeredWheelDeg: Float,
        speedMps: Float,
        dtSec: Double,
        scale: Float,
        sign: Int,
        applyInternalDeadzone: Boolean = true,
        deadzoneDeg: Float = SteerCalibrationStore.offsets.deadzoneDeg,
        wheelbaseM: Float = SteerCalibrationStore.offsets.wheelbaseM,
    ): Float {
        if (!centeredWheelDeg.isFinite() || !speedMps.isFinite() || dtSec <= 0.0) return 0f
        if (abs(speedMps) < MIN_SPEED_MPS) return 0f
        val wheel = if (applyInternalDeadzone) {
            SteerCalibrationStore.softDeadzone(centeredWheelDeg, deadzoneDeg)
        } else {
            centeredWheelDeg
        }
        if (wheel == 0f) return 0f
        val k = scale.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_SCALE
        val roadDeg = (k * wheel).coerceIn(-MAX_ROAD_WHEEL_DEG, MAX_ROAD_WHEEL_DEG)
        val roadRad = Math.toRadians(roadDeg.toDouble())
        val l = resolveWheelbaseM(wheelbaseM)
        val yawRateRad = (speedMps / l) * tan(roadRad)
        val yawDeg = Math.toDegrees(yawRateRad * dtSec).toFloat()
        val s = if (sign < 0) -1 else 1
        return -s * yawDeg
    }

    fun resolveWheelbaseM(wheelbaseM: Float): Float {
        if (!wheelbaseM.isFinite() || wheelbaseM <= 0f) return DEFAULT_WHEELBASE_M
        return wheelbaseM.coerceIn(
            SteerCalibrationOffsets.WHEELBASE_EDIT_MIN,
            SteerCalibrationOffsets.WHEELBASE_EDIT_MAX,
        )
    }

    /**
     * Fallback scalar when an explicit scale is missing/invalid.
     * Runtime prefers [SteerScaleProfile] defaults (0.072…0.033 by speed).
     */
    const val DEFAULT_SCALE = SteerScaleProfile.DEFAULT_SCALE_40_KMH
}
