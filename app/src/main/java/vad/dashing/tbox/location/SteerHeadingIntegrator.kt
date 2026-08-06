package vad.dashing.tbox.location

import kotlin.math.abs

/**
 * High-rate steering-angle integration for mock dead-reckoning heading.
 *
 * Uses Δ(steering angle) × calibrated L/R scale (see [SteerCalibrationStore]), not
 * `steerSpeed` (unavailable on A10). [MockLocationJob] [consumeDeltaDeg] once per
 * DR tick, same pattern as [YawIntegrator].
 *
 * Nav convention matches [MockLocationJob.applyYawDeltaToBearing]: accumulated delta
 * already includes the left+ → bearing decrease mapping from [SteerCalibrationStore].
 */
object SteerHeadingIntegrator {
    /**
     * Max gap between consecutive steer samples used for one step.
     * Longer gaps are ignored for Δ (re-seed) so a stall does not invent a huge turn.
     */
    const val MAX_SAMPLE_DT_SEC = 0.5

    /** Reject absurd |Δsteer| in one sample (°). */
    const val MAX_ABS_DELTA_DEG = 90f

    private val lock = Any()
    private var lastSampleElapsedMs: Long = 0L
    private var lastCenteredDeg: Float? = null
    private var pendingDeltaDeg: Double = 0.0

    fun pendingDeltaDeg(): Float = synchronized(lock) { pendingDeltaDeg.toFloat() }

    fun reset() {
        synchronized(lock) {
            lastSampleElapsedMs = 0L
            lastCenteredDeg = null
            pendingDeltaDeg = 0.0
        }
    }

    /**
     * Ingest one raw steering-wheel angle (°) at [elapsedMs]
     * ([android.os.SystemClock.elapsedRealtime]).
     * Applies [SteerCalibrationStore] zero then Δ→bearing via scales/sign.
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
            val prevT = lastSampleElapsedMs
            val prevC = lastCenteredDeg
            lastSampleElapsedMs = elapsedMs
            lastCenteredDeg = centeredDeg
            if (prevT <= 0L || elapsedMs < prevT || prevC == null) return
            val dtSec = (elapsedMs - prevT) / 1000.0
            if (dtSec <= 0.0) return
            if (dtSec > MAX_SAMPLE_DT_SEC) {
                // Gap too large — re-seed without integrating across the stall.
                return
            }
            val deltaSteer = centeredDeg - prevC
            if (!deltaSteer.isFinite() || abs(deltaSteer) > MAX_ABS_DELTA_DEG) return
            if (abs(deltaSteer) < 1e-4f) return
            pendingDeltaDeg += SteerCalibrationStore.applyDeltaToBearingDelta(deltaSteer).toDouble()
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

    private fun clearHold() {
        synchronized(lock) {
            lastSampleElapsedMs = 0L
            lastCenteredDeg = null
        }
    }
}
