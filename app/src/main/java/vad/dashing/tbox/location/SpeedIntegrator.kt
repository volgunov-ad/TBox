package vad.dashing.tbox.location

import java.io.File

/**
 * High-rate CAN speed integration for mock dead-reckoning distance.
 *
 * Vehicle speed updates several times per second, while [MockLocationJob] often
 * pushes at ~1 Hz. Sampling one instantaneous speed per mock tick
 * (`v_end · Δt`) overshoots on hard accel and undershoots on braking.
 *
 * Instead, [onRawSample] accumulates path length on every speed sample
 * (trapezoid between consecutive samples); [flushTo] extends with the last
 * known speed up to the mock tick (covers constant-speed stretches where
 * StateFlow does not re-emit identical values). [MockLocationJob] then
 * [consumeDistanceM] once per DR step.
 *
 * Feed only the accounting speed stream ([TripTelemetryRepository.carSpeed]) —
 * not a second HU/CAN collector — so each transition is integrated once.
 */
object SpeedIntegrator {
    /**
     * Max gap between consecutive CAN samples used for one trapezoid step.
     * Longer gaps (stall / reconnect) are clamped so a dead interval does not
     * invent a huge jump from a single late emit.
     */
    const val MAX_SAMPLE_DT_SEC = 1.25

    /**
     * Max hold extension in [flushTo] for one chunk.
     * Covers UI mock periods up to 5 s when StateFlow does not re-emit a
     * constant speed; larger gaps are filled in multiple chunks.
     */
    const val MAX_FLUSH_DT_SEC = 5.0

    /** Reject absurd calibrated speeds (km/h). */
    const val MAX_ABS_SPEED_KMH = 300f

    private val lock = Any()
    private var lastSampleElapsedMs: Long = 0L
    private var lastSpeedMps: Double? = null
    private var pendingDistanceM: Double = 0.0

    /** Pending path length (m), for tests / diagnostics. */
    fun pendingDistanceM(): Double = synchronized(lock) { pendingDistanceM }

    fun lastSpeedKmh(): Float? = synchronized(lock) {
        lastSpeedMps?.let { (it * 3.6).toFloat() }
    }

    fun reset() {
        synchronized(lock) {
            lastSampleElapsedMs = 0L
            lastSpeedMps = null
            pendingDistanceM = 0.0
        }
    }

    /**
     * Ingest one raw CAN / accounting speed sample (km/h) at [elapsedMs]
     * ([android.os.SystemClock.elapsedRealtime]).
     * Applies [DriveCalibrationStore.applyCanSpeed] before integrating.
     * Null / invalid clears the held speed (next sample re-seeds) but keeps
     * already accumulated pending distance for a later [consumeDistanceM].
     */
    fun onRawSample(rawKmh: Float?, elapsedMs: Long) {
        if (rawKmh == null || !rawKmh.isFinite() || rawKmh < 0f || elapsedMs <= 0L) {
            clearHeldSpeed()
            return
        }
        val calibrated = DriveCalibrationStore.applyCanSpeed(rawKmh)
        onCalibratedSample(calibrated, elapsedMs)
    }

    /**
     * Ingest already scale-corrected speed (km/h). Pure path for unit tests.
     */
    fun onCalibratedSample(speedKmh: Float, elapsedMs: Long) {
        // #region agent log
        debugLog("D", "SpeedIntegrator.kt:onCalibratedSample", "sample", """{"speedKmh":$speedKmh,"elapsedMs":$elapsedMs}""")
        // #endregion
        if (!speedKmh.isFinite() || speedKmh < 0f || elapsedMs <= 0L) {
            clearHeldSpeed()
            return
        }
        if (speedKmh > MAX_ABS_SPEED_KMH) return
        val newMps = speedKmh / 3.6
        synchronized(lock) {
            val prevT = lastSampleElapsedMs
            val prevMps = lastSpeedMps
            lastSampleElapsedMs = elapsedMs
            lastSpeedMps = newMps
            if (prevT <= 0L || elapsedMs < prevT || prevMps == null) return
            val dtSec = ((elapsedMs - prevT) / 1000.0).coerceAtMost(MAX_SAMPLE_DT_SEC)
            if (dtSec <= 0.0) return
            // Trapezoid: closer to ∫v dt than endpoint (right Riemann) on accel/brake.
            pendingDistanceM += (prevMps + newMps) * 0.5 * dtSec
            // #region agent log
            debugLog("B,D", "SpeedIntegrator.kt:onCalibratedSample", "integrated sample", """{"prevT":$prevT,"elapsedMs":$elapsedMs,"prevMps":$prevMps,"newMps":$newMps,"dtSec":$dtSec,"pendingM":$pendingDistanceM}""")
            // #endregion
        }
    }

    /**
     * Extend pending distance with the last held speed up to [elapsedMs]
     * (zero-order hold). Call before [consumeDistanceM] on a mock tick so
     * constant-speed intervals without StateFlow re-emits still advance.
     * Long gaps are filled in chunks of [MAX_FLUSH_DT_SEC] so UI mock periods
     * of 2–5 s are fully covered.
     */
    fun flushTo(elapsedMs: Long) {
        if (elapsedMs <= 0L) return
        synchronized(lock) {
            val prevMps = lastSpeedMps ?: return
            if (lastSampleElapsedMs <= 0L || elapsedMs <= lastSampleElapsedMs) return
            // #region agent log
            debugLog("A,C,E", "SpeedIntegrator.kt:flushTo", "flush start", """{"fromMs":$lastSampleElapsedMs,"toMs":$elapsedMs,"heldMps":$prevMps,"pendingM":$pendingDistanceM}""")
            // #endregion
            while (elapsedMs > lastSampleElapsedMs) {
                val remainingSec = (elapsedMs - lastSampleElapsedMs) / 1000.0
                val dtSec = remainingSec.coerceAtMost(MAX_FLUSH_DT_SEC)
                if (dtSec <= 0.0) break
                pendingDistanceM += prevMps * dtSec
                val advanceMs = (dtSec * 1000.0).toLong().coerceAtLeast(1L)
                val nextT = lastSampleElapsedMs + advanceMs
                lastSampleElapsedMs = if (nextT >= elapsedMs || dtSec >= remainingSec) {
                    elapsedMs
                } else {
                    nextT
                }
            }
            // #region agent log
            debugLog("A,C,E", "SpeedIntegrator.kt:flushTo", "flush end", """{"atMs":$lastSampleElapsedMs,"pendingM":$pendingDistanceM}""")
            // #endregion
        }
    }

    /**
     * Return and clear accumulated distance (meters).
     * Call once per mock DR step (or [discard] when DR is idle).
     */
    fun consumeDistanceM(): Double {
        synchronized(lock) {
            val out = pendingDistanceM
            pendingDistanceM = 0.0
            // #region agent log
            debugLog("B,D", "SpeedIntegrator.kt:consumeDistanceM", "consume", """{"distanceM":$out,"heldAtMs":$lastSampleElapsedMs}""")
            // #endregion
            return out
        }
    }

    /** Drop pending distance without applying (GNSS-live ticks, mock off, etc.). */
    fun discard() {
        synchronized(lock) {
            // #region agent log
            debugLog("A,B,C,F", "SpeedIntegrator.kt:discard", "discard", """{"distanceM":$pendingDistanceM,"heldAtMs":$lastSampleElapsedMs,"hasHeldSpeed":${lastSpeedMps != null}}""")
            // #endregion
            pendingDistanceM = 0.0
        }
    }

    private fun clearHeldSpeed() {
        synchronized(lock) {
            // #region agent log
            debugLog("C", "SpeedIntegrator.kt:clearHeldSpeed", "clear hold", """{"heldAtMs":$lastSampleElapsedMs,"pendingM":$pendingDistanceM}""")
            // #endregion
            lastSampleElapsedMs = 0L
            lastSpeedMps = null
        }
    }

    private fun debugLog(hypothesisId: String, location: String, message: String, data: String) {
        // #region agent log
        File("/opt/cursor/logs/debug.log").appendText(
            """{"hypothesisId":"$hypothesisId","location":"$location","message":"$message","data":$data,"timestamp":${System.currentTimeMillis()}}""" + "\n",
        )
        // #endregion
    }
}
