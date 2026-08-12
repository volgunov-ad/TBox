package vad.dashing.tbox.location

import kotlin.math.abs

/**
 * Session-long integrals for geo-debug recording (offline calib / USB-loss experiments).
 *
 * Independent of [SpeedIntegrator] / [YawIntegrator] / [SteerHeadingIntegrator] so logging
 * never steals pending DR deltas. Uses **raw** CAN speed (no [DriveCalibrationStore] scale)
 * and **raw / debiased** gyro (no L/R yaw scale) so scales can be estimated from the log.
 *
 * Axes: yaw / pitch / roll rates from [vad.dashing.tbox.drsensor.DrSensorSnapshot].
 * On SensorManager path, Android `values[2]` is mapped to [roll] (device Z).
 *
 * Steering: unit path ∫ (v/L)·δ_eff dt (°) with soft deadzone + wheelbase from
 * [SteerCalibrationStore], scale=1 — offline `k ≈ Δcourse / steerPathDeg` (small angles).
 */
class GeoDebugIntegralAccumulator(
    private val maxSpeedDtSec: Double = SpeedIntegrator.MAX_SAMPLE_DT_SEC,
    private val maxGyroDtSec: Double = YawIntegrator.MAX_SAMPLE_DT_SEC,
    private val maxSteerDtSec: Double = SteerHeadingIntegrator.MAX_SAMPLE_DT_SEC,
    private val maxAbsSpeedKmh: Float = SpeedIntegrator.MAX_ABS_SPEED_KMH,
    private val maxAbsGyroRate: Float = YawIntegrator.MAX_ABS_YAW_RATE_DEG_PER_SEC,
) {
    data class Snapshot(
        val distM: Double = 0.0,
        val yawRawDeg: Double = 0.0,
        val yawDebDeg: Double = 0.0,
        val pitchDeg: Double = 0.0,
        /** Roll integral; SensorManager Z-axis rate maps here. */
        val rollDeg: Double = 0.0,
        /** ∫ (v/L)·δ_eff dt (unit steer scale). */
        val steerPathDeg: Double = 0.0,
        val speedSamples: Long = 0L,
        val gyroSamples: Long = 0L,
        val steerSamples: Long = 0L,
    )

    private val lock = Any()

    private var distM: Double = 0.0
    private var yawRawDeg: Double = 0.0
    private var yawDebDeg: Double = 0.0
    private var pitchDeg: Double = 0.0
    private var rollDeg: Double = 0.0
    private var steerPathDeg: Double = 0.0

    private var speedSamples: Long = 0L
    private var gyroSamples: Long = 0L
    private var steerSamples: Long = 0L

    private var lastSpeedElapsedMs: Long = 0L
    private var lastSpeedMps: Double? = null

    private var lastGyroElapsedMs: Long = 0L

    private var lastSteerElapsedMs: Long = 0L
    private var lastCenteredDeg: Float? = null
    private var lastSteerSpeedMps: Float = 0f

    private var lastSnap: Snapshot = Snapshot()

    fun reset() {
        synchronized(lock) {
            distM = 0.0
            yawRawDeg = 0.0
            yawDebDeg = 0.0
            pitchDeg = 0.0
            rollDeg = 0.0
            steerPathDeg = 0.0
            speedSamples = 0L
            gyroSamples = 0L
            steerSamples = 0L
            lastSpeedElapsedMs = 0L
            lastSpeedMps = null
            lastGyroElapsedMs = 0L
            lastSteerElapsedMs = 0L
            lastCenteredDeg = null
            lastSteerSpeedMps = 0f
            lastSnap = Snapshot()
        }
    }

    /** Raw accounting / HU speed (km/h), no drive speedScale. */
    fun onSpeedKmh(rawKmh: Float?, elapsedMs: Long) {
        if (elapsedMs <= 0L) return
        synchronized(lock) {
            if (rawKmh == null || !rawKmh.isFinite() || rawKmh < 0f) {
                lastSpeedMps = null
                lastSpeedElapsedMs = elapsedMs
                return
            }
            if (rawKmh > maxAbsSpeedKmh) return
            val newMps = rawKmh / 3.6
            val prevT = lastSpeedElapsedMs
            val prevMps = lastSpeedMps
            lastSpeedElapsedMs = elapsedMs
            lastSpeedMps = newMps
            speedSamples++
            lastSteerSpeedMps = newMps.toFloat()
            if (prevT <= 0L || elapsedMs < prevT || prevMps == null) return
            val dtSec = ((elapsedMs - prevT) / 1000.0).coerceAtMost(maxSpeedDtSec)
            if (dtSec <= 0.0) return
            distM += (prevMps + newMps) * 0.5 * dtSec
        }
    }

    /**
     * Gyro rates (°/s) at sensor sample time.
     * [yawRaw] left +, right −. Debiased yaw uses [GyroBiasStore] only (no L/R scale).
     */
    fun onGyro(
        yawRaw: Float?,
        pitch: Float?,
        roll: Float?,
        elapsedMs: Long,
    ) {
        if (elapsedMs <= 0L) return
        if (yawRaw == null && pitch == null && roll == null) return
        synchronized(lock) {
            val prevT = lastGyroElapsedMs
            lastGyroElapsedMs = elapsedMs
            gyroSamples++
            if (prevT <= 0L || elapsedMs < prevT) return
            val dtSec = ((elapsedMs - prevT) / 1000.0).coerceAtMost(maxGyroDtSec)
            if (dtSec <= 0.0) return
            if (yawRaw != null && yawRaw.isFinite() && abs(yawRaw) <= maxAbsGyroRate) {
                yawRawDeg += yawRaw.toDouble() * dtSec
                val deb = GyroBiasStore.applyYaw(yawRaw)
                if (deb != null && deb.isFinite()) {
                    yawDebDeg += deb.toDouble() * dtSec
                }
            }
            addGyroAxis(pitch, dtSec) { pitchDeg += it }
            addGyroAxis(roll, dtSec) { rollDeg += it }
        }
    }

    /**
     * Raw steering wheel angle (°). Integrates unit path with held speed from
     * [onSpeedKmh] / [setSteerSpeedKmh].
     */
    fun onSteerAngle(rawAngleDeg: Float?, elapsedMs: Long) {
        if (elapsedMs <= 0L) return
        synchronized(lock) {
            if (rawAngleDeg == null || !rawAngleDeg.isFinite()) {
                lastCenteredDeg = null
                lastSteerElapsedMs = elapsedMs
                return
            }
            val centered = SteerCalibrationStore.applyZero(rawAngleDeg)
            if (centered == null || !centered.isFinite()) {
                lastCenteredDeg = null
                lastSteerElapsedMs = elapsedMs
                return
            }
            val prevT = lastSteerElapsedMs
            val prevC = lastCenteredDeg
            lastSteerElapsedMs = elapsedMs
            lastCenteredDeg = centered
            steerSamples++
            if (prevT <= 0L || elapsedMs < prevT || prevC == null) return
            val dtSec = (elapsedMs - prevT) / 1000.0
            if (dtSec <= 0.0 || dtSec > maxSteerDtSec) return
            steerPathDeg += SteerHeadingIntegrator
                .pathElementDeg(prevC, lastSteerSpeedMps, dtSec.toFloat())
                .toDouble()
        }
    }

    fun setSteerSpeedKmh(speedKmh: Float?) {
        synchronized(lock) {
            val v = speedKmh?.takeIf { it.isFinite() } ?: 0f
            lastSteerSpeedMps = v / 3.6f
        }
    }

    /**
     * Zero-order hold flush to [elapsedMs] for speed distance and held-steer path
     * (constant-speed / constant-wheel stretches without StateFlow re-emits).
     */
    fun flushTo(elapsedMs: Long) {
        if (elapsedMs <= 0L) return
        synchronized(lock) {
            flushSpeedLocked(elapsedMs)
            flushSteerLocked(elapsedMs)
        }
    }

    /** Session totals at last [snapshotForLog] (for tick deltas). */
    fun previousSnapshot(): Snapshot = synchronized(lock) { lastSnap }

    fun snapshotForLog(elapsedMs: Long = 0L): Snapshot {
        synchronized(lock) {
            if (elapsedMs > 0L) {
                flushSpeedLocked(elapsedMs)
                flushSteerLocked(elapsedMs)
            }
            val snap = Snapshot(
                distM = distM,
                yawRawDeg = yawRawDeg,
                yawDebDeg = yawDebDeg,
                pitchDeg = pitchDeg,
                rollDeg = rollDeg,
                steerPathDeg = steerPathDeg,
                speedSamples = speedSamples,
                gyroSamples = gyroSamples,
                steerSamples = steerSamples,
            )
            lastSnap = snap
            return snap
        }
    }

    private fun addGyroAxis(rate: Float?, dtSec: Double, add: (Double) -> Unit) {
        if (rate == null || !rate.isFinite()) return
        if (abs(rate) > maxAbsGyroRate) return
        add(rate.toDouble() * dtSec)
    }

    private fun flushSpeedLocked(elapsedMs: Long) {
        val prevT = lastSpeedElapsedMs
        val prevMps = lastSpeedMps ?: return
        if (prevT <= 0L || elapsedMs <= prevT) return
        var t = prevT
        while (elapsedMs > t) {
            val remainingSec = (elapsedMs - t) / 1000.0
            val dtSec = remainingSec.coerceAtMost(SpeedIntegrator.MAX_FLUSH_DT_SEC)
                .coerceAtMost(maxSpeedDtSec)
            if (dtSec <= 0.0) break
            distM += prevMps * dtSec
            val advanceMs = (dtSec * 1000.0).toLong().coerceAtLeast(1L)
            val nextT = t + advanceMs
            t = if (nextT >= elapsedMs || dtSec >= remainingSec) elapsedMs else nextT
        }
        lastSpeedElapsedMs = elapsedMs
    }

    private fun flushSteerLocked(elapsedMs: Long) {
        val prevT = lastSteerElapsedMs
        val prevC = lastCenteredDeg ?: return
        if (prevT <= 0L || elapsedMs <= prevT) return
        var t = prevT
        while (elapsedMs > t) {
            val remainingSec = (elapsedMs - t) / 1000.0
            val dtSec = remainingSec.coerceAtMost(maxSteerDtSec)
            if (dtSec <= 0.0) break
            steerPathDeg += SteerHeadingIntegrator
                .pathElementDeg(prevC, lastSteerSpeedMps, dtSec.toFloat())
                .toDouble()
            val advanceMs = (dtSec * 1000.0).toLong().coerceAtLeast(1L)
            val nextT = t + advanceMs
            t = if (nextT >= elapsedMs || dtSec >= remainingSec) elapsedMs else nextT
        }
        lastSteerElapsedMs = elapsedMs
    }
}
