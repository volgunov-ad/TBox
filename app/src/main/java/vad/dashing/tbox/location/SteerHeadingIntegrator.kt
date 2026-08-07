package vad.dashing.tbox.location

import kotlin.math.abs
import kotlin.math.tan

/**
 * High-rate steering→heading for mock DR using the bicycle / Ackermann model:
 *
 * `ψ̇ = (v / L) · tan(δ_road)`, `δ_road = scale · δ_wheel`
 *
 * Holding a non-zero wheel angle while moving accumulates heading; returning the
 * wheel to center stops the turn (does **not** unwind heading). That is the
 * opposite of a naïve `Δheading ∝ Δsteer` model.
 *
 * [L] is the Jetour Dashing wheelbase; [SteerCalibrationStore.scale] absorbs
 * steering-wheel→road-wheel ratio. Nav convention matches [YawIntegrator].
 */
object SteerHeadingIntegrator {
    /** Jetour Dashing wheelbase (m). */
    const val WHEELBASE_M = 2.72f

    /** Max |road-wheel| angle after scale (°). */
    const val MAX_ROAD_WHEEL_DEG = 40f

    /** Ignore tiny wheel angles as straight. */
    const val DEADZONE_WHEEL_DEG = 2f

    /** Below this speed (m/s) do not integrate (standstill / creep). */
    const val MIN_SPEED_MPS = 0.4f

    /**
     * Max gap between samples / ticks used for one step.
     * Longer gaps re-seed without integrating across a stall.
     */
    const val MAX_SAMPLE_DT_SEC = 0.5

    private val lock = Any()
    private var lastSampleElapsedMs: Long = 0L
    private var lastCenteredDeg: Float? = null
    private var lastSpeedMps: Float = 0f
    private var pendingDeltaDeg: Double = 0.0

    fun pendingDeltaDeg(): Float = synchronized(lock) { pendingDeltaDeg.toFloat() }

    fun reset() {
        synchronized(lock) {
            lastSampleElapsedMs = 0L
            lastCenteredDeg = null
            lastSpeedMps = 0f
            pendingDeltaDeg = 0.0
        }
    }

    /** Update calibrated CAN/GNSS speed (km/h). Sign: negative = reverse. */
    fun onSpeedKmh(speedKmh: Float?) {
        synchronized(lock) {
            val v = speedKmh?.takeIf { it.isFinite() } ?: 0f
            lastSpeedMps = v / 3.6f
        }
    }

    /**
     * Ingest one raw steering-wheel angle (°) at [elapsedMs]
     * ([android.os.SystemClock.elapsedRealtime]).
     */
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

    /** Ingest already zero-corrected steering angle (°). Pure path for unit tests. */
    fun onCenteredSample(centeredDeg: Float, elapsedMs: Long) {
        if (!centeredDeg.isFinite() || elapsedMs <= 0L) {
            clearHold()
            return
        }
        synchronized(lock) {
            integrateLocked(elapsedMs)
            lastCenteredDeg = centeredDeg
        }
    }

    /**
     * Flush integration up to [elapsedMs] with the last held wheel angle
     * (call before [consumeDeltaDeg] on each mock tick).
     */
    fun tick(elapsedMs: Long) {
        if (elapsedMs <= 0L) return
        synchronized(lock) {
            integrateLocked(elapsedMs)
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

    private fun integrateLocked(elapsedMs: Long) {
        val prevT = lastSampleElapsedMs
        val prevC = lastCenteredDeg
        lastSampleElapsedMs = elapsedMs
        if (prevT <= 0L || elapsedMs < prevT || prevC == null) return
        val dtSec = (elapsedMs - prevT) / 1000.0
        if (dtSec <= 0.0) return
        if (dtSec > MAX_SAMPLE_DT_SEC) {
            // Gap too large — re-seed without integrating across the stall.
            return
        }
        val speed = lastSpeedMps
        if (!speed.isFinite() || abs(speed) < MIN_SPEED_MPS) return
        pendingDeltaDeg += SteerCalibrationStore
            .yawDeltaDeg(prevC, speed, dtSec)
            .toDouble()
    }

    private fun clearHold() {
        synchronized(lock) {
            lastSampleElapsedMs = 0L
            lastCenteredDeg = null
        }
    }

    /**
     * Unit path integral for calibration: ∫ (v/L)·δ_wheel dt (° of heading if scale=1,
     * small-angle / before tan). Prefer [pathIntegralTanDeg] when scale is known.
     */
    fun pathElementDeg(centeredWheelDeg: Float, speedMps: Float, dtSec: Float): Float {
        if (!centeredWheelDeg.isFinite() || !speedMps.isFinite() || dtSec <= 0f) return 0f
        if (abs(speedMps) < MIN_SPEED_MPS) return 0f
        var d = centeredWheelDeg
        if (abs(d) < DEADZONE_WHEEL_DEG) d = 0f
        return (speedMps / WHEELBASE_M) * d * dtSec
    }

    /** Heading delta (°) for one step with [tan] road-wheel model. */
    fun yawDeltaDeg(
        centeredWheelDeg: Float,
        speedMps: Float,
        dtSec: Double,
        scale: Float,
        sign: Int,
    ): Float {
        if (!centeredWheelDeg.isFinite() || !speedMps.isFinite() || dtSec <= 0.0) return 0f
        if (abs(speedMps) < MIN_SPEED_MPS) return 0f
        var wheel = centeredWheelDeg
        if (abs(wheel) < DEADZONE_WHEEL_DEG) wheel = 0f
        val k = scale.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_SCALE
        val roadDeg = (k * wheel).coerceIn(-MAX_ROAD_WHEEL_DEG, MAX_ROAD_WHEEL_DEG)
        val roadRad = Math.toRadians(roadDeg.toDouble())
        val yawRateRad = (speedMps / WHEELBASE_M) * tan(roadRad)
        val yawDeg = Math.toDegrees(yawRateRad * dtSec).toFloat()
        val s = if (sign < 0) -1 else 1
        // Nav: left+ wheel with sign=+1 decreases bearing.
        return -s * yawDeg
    }

    /** Default wheel→road scale (~1/15 steering ratio). */
    const val DEFAULT_SCALE = 1f / 15f
}
