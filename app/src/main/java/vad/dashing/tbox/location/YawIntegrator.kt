package vad.dashing.tbox.location

import kotlin.math.abs

/**
 * High-rate yaw integration for mock dead-reckoning.
 *
 * HU gyro (A9 UDS / A10 NaviDR / SensorManager) updates much faster than the mock
 * push period (often 0.5–1 s). Sampling one instantaneous rate per mock tick and
 * capping dt at 0.25 s under-rotates turns. Instead, [onRawSample] accumulates
 * heading change on every sensor sample; [MockLocationJob] [consumeDeltaDeg] once
 * per push and applies the full integral.
 *
 * Nav convention matches [MockLocationJob.integrateYawIntoBearing]: left yaw (+)
 * decreases bearing → accumulated delta is `−yawCal · Δt`.
 */
object YawIntegrator {
    /**
     * Max gap between consecutive gyro samples used for one step.
     * Longer gaps (sensor stall) are clamped so a reconnect does not invent a huge turn.
     */
    const val MAX_SAMPLE_DT_SEC = 0.25

    /** Same absurd-rate gate as [MockLocationJob.MAX_ABS_YAW_RATE_DEG_PER_SEC]. */
    const val MAX_ABS_YAW_RATE_DEG_PER_SEC = 80f

    /** Same deadband as [MockLocationJob.YAW_DEADBAND_DEG_PER_SEC]. */
    const val YAW_DEADBAND_DEG_PER_SEC = 0.5f

    private val lock = Any()
    private var lastSampleElapsedMs: Long = 0L
    private var pendingDeltaDeg: Double = 0.0

    /** Pending nav bearing delta (deg), for tests / diagnostics. */
    fun pendingDeltaDeg(): Float = synchronized(lock) { pendingDeltaDeg.toFloat() }

    fun reset() {
        synchronized(lock) {
            lastSampleElapsedMs = 0L
            pendingDeltaDeg = 0.0
        }
    }

    /**
     * Ingest one raw gyro yaw sample (°/s, left +, right −) at [elapsedMs]
     * ([android.os.SystemClock.elapsedRealtime]).
     * Applies [GyroBiasStore] then [DriveCalibrationStore] before integrating.
     */
    fun onRawSample(rawYawDegPerSec: Float?, elapsedMs: Long) {
        if (rawYawDegPerSec == null || !rawYawDegPerSec.isFinite() || elapsedMs <= 0L) return
        val debiased = GyroBiasStore.applyYaw(rawYawDegPerSec) ?: return
        if (!debiased.isFinite()) return
        val calibrated = DriveCalibrationStore.applyYawRate(debiased)
        onCalibratedSample(calibrated, elapsedMs)
    }

    /**
     * Ingest already bias+scale corrected yaw (°/s). Pure path for unit tests.
     */
    fun onCalibratedSample(yawCalDegPerSec: Float, elapsedMs: Long) {
        if (!yawCalDegPerSec.isFinite() || elapsedMs <= 0L) return
        synchronized(lock) {
            val prev = lastSampleElapsedMs
            lastSampleElapsedMs = elapsedMs
            if (prev <= 0L || elapsedMs < prev) return
            val dtSec = ((elapsedMs - prev) / 1000.0).coerceAtMost(MAX_SAMPLE_DT_SEC)
            if (dtSec <= 0.0) return
            if (abs(yawCalDegPerSec) > MAX_ABS_YAW_RATE_DEG_PER_SEC) return
            if (abs(yawCalDegPerSec) < YAW_DEADBAND_DEG_PER_SEC) return
            // Nav bearing delta: subtract yaw×dt (left + → bearing decreases).
            pendingDeltaDeg += (-yawCalDegPerSec.toDouble() * dtSec)
        }
    }

    /**
     * Return and clear accumulated nav bearing delta (degrees).
     * Call once per mock DR step (or [discard] when DR is idle).
     */
    fun consumeDeltaDeg(): Float {
        synchronized(lock) {
            val out = pendingDeltaDeg.toFloat()
            pendingDeltaDeg = 0.0
            return out
        }
    }

    /** Drop pending delta without applying (GNSS-live ticks, mock off, etc.). */
    fun discard() {
        synchronized(lock) {
            pendingDeltaDeg = 0.0
        }
    }
}
