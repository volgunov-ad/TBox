package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeoDebugIntegralAccumulatorTest {

    private lateinit var acc: GeoDebugIntegralAccumulator

    @Before
    fun setUp() {
        GyroBiasStore.update(GyroBiasOffsets.ZERO)
        SteerCalibrationStore.reset()
        DriveCalibrationStore.reset()
        acc = GeoDebugIntegralAccumulator()
        acc.reset()
    }

    @Test
    fun speedTrapezoidAccumulatesDistance() {
        acc.onSpeedKmh(36f, 1_000L) // 10 m/s
        acc.onSpeedKmh(36f, 2_000L)
        val snap = acc.snapshotForLog()
        assertEquals(10.0, snap.distM, 1e-3)
        assertEquals(2L, snap.speedSamples)
    }

    @Test
    fun speedFlushExtendsConstantHold() {
        acc.onSpeedKmh(36f, 1_000L)
        acc.flushTo(2_000L)
        assertEquals(10.0, acc.snapshotForLog().distM, 1e-3)
    }

    @Test
    fun gyroIntegratesYawPitchRoll() {
        acc.onGyro(yawRaw = 10f, pitch = 2f, roll = -3f, elapsedMs = 1_000L)
        acc.onGyro(yawRaw = 10f, pitch = 2f, roll = -3f, elapsedMs = 1_200L)
        val snap = acc.snapshotForLog()
        assertEquals(2.0, snap.yawRawDeg, 1e-3) // 10°/s * 0.2s
        assertEquals(0.4, snap.pitchDeg, 1e-3)
        assertEquals(-0.6, snap.rollDeg, 1e-3)
        assertEquals(2.0, snap.yawDebDeg, 1e-3) // bias 0
    }

    @Test
    fun gyroDebiasedUsesBiasStore() {
        GyroBiasStore.update(GyroBiasOffsets(yawDegPerSec = 1f))
        acc.onGyro(yawRaw = 11f, pitch = null, roll = null, elapsedMs = 1_000L)
        acc.onGyro(yawRaw = 11f, pitch = null, roll = null, elapsedMs = 1_100L)
        val snap = acc.snapshotForLog()
        assertEquals(1.1, snap.yawRawDeg, 1e-3)
        assertEquals(1.0, snap.yawDebDeg, 1e-3) // (11-1)*0.1
    }

    @Test
    fun steerUnitPathWithSpeed() {
        acc.onSpeedKmh(36f, 1_000L) // 10 m/s
        acc.onSteerAngle(90f, 1_000L)
        acc.onSteerAngle(90f, 1_200L)
        val snap = acc.snapshotForLog()
        assertTrue(snap.steerPathDeg != 0.0)
        assertEquals(2L, snap.steerSamples)
    }

    @Test
    fun steerPathUsesPiecewiseSpeedOnAccel() {
        // Held wheel + mid-interval speed change must not attribute the whole gap
        // to the new speed (matches SteerHeadingIntegrator.onSpeedKmh behavior).
        acc.onSpeedKmh(36f, 1_000L) // 10 m/s
        acc.onSteerAngle(90f, 1_000L)
        acc.onSpeedKmh(72f, 1_500L) // flush 0.5 s @ 10 m/s, then hold 20 m/s
        acc.onSteerAngle(90f, 2_000L) // 0.5 s @ 20 m/s
        val path = acc.snapshotForLog().steerPathDeg
        val centered = SteerCalibrationStore.applyZero(90f)!!
        val expected =
            SteerHeadingIntegrator.pathElementDeg(centered, 10f, 0.5f).toDouble() +
                SteerHeadingIntegrator.pathElementDeg(centered, 20f, 0.5f).toDouble()
        assertEquals(expected, path, 1e-3)
        // Naive "whole 1 s at final speed" would be ~2× the first half — reject that.
        val naiveAllFast =
            SteerHeadingIntegrator.pathElementDeg(centered, 20f, 1.0f).toDouble()
        assertTrue(kotlin.math.abs(path - naiveAllFast) > 1e-3)
    }

    @Test
    fun tickDeltasViaPreviousSnapshot() {
        acc.onSpeedKmh(36f, 1_000L)
        acc.onSpeedKmh(36f, 2_000L)
        val first = acc.snapshotForLog()
        assertEquals(10.0, first.distM, 1e-3)
        val prev = acc.previousSnapshot()
        assertEquals(first.distM, prev.distM, 0.0)
        acc.onSpeedKmh(36f, 3_000L)
        val second = acc.snapshotForLog()
        assertEquals(10.0, second.distM - prev.distM, 1e-3)
    }

    @Test
    fun rawSpeedIgnoresDriveScale() {
        DriveCalibrationStore.update(DriveCalibrationOffsets(speedScale = 2f))
        acc.onSpeedKmh(36f, 1_000L)
        acc.onSpeedKmh(36f, 2_000L)
        // Still 10 m for 36 km/h × 1 s — not scaled.
        assertEquals(10.0, acc.snapshotForLog().distM, 1e-3)
        DriveCalibrationStore.reset()
    }
}
