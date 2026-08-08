package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeedIntegratorTest {

    @Before
    fun reset() {
        SpeedIntegrator.reset()
        DriveCalibrationStore.reset()
    }

    @Test
    fun trapezoidOnLinearAccelMatchesMeanSpeed() {
        // 0 → 36 km/h (0 → 10 m/s) over 1.0 s in 10 steps → ∫ = 5 m (trapezoid).
        SpeedIntegrator.onCalibratedSample(0f, 1_000L)
        for (i in 1..10) {
            val v = 36f * i / 10f
            SpeedIntegrator.onCalibratedSample(v, 1_000L + i * 100L)
        }
        assertEquals(5.0, SpeedIntegrator.consumeDistanceM(), 0.05)
        assertEquals(0.0, SpeedIntegrator.consumeDistanceM(), 0.0)
    }

    @Test
    fun endpointWouldOvershootAccel_trapezoidDoesNot() {
        // Single 1 s step 36 → 72 km/h (10 → 20 m/s).
        // Endpoint (right): 20 m; trapezoid: 15 m.
        SpeedIntegrator.onCalibratedSample(36f, 1_000L)
        SpeedIntegrator.onCalibratedSample(72f, 2_000L)
        assertEquals(15.0, SpeedIntegrator.consumeDistanceM(), 1e-6)
    }

    @Test
    fun flushToHoldsConstantSpeedBetweenEmits() {
        SpeedIntegrator.onCalibratedSample(36f, 1_000L) // 10 m/s
        SpeedIntegrator.flushTo(2_000L)
        assertEquals(10.0, SpeedIntegrator.consumeDistanceM(), 1e-6)
    }

    @Test
    fun flushToCoversFiveSecondMockPeriod() {
        // UI allows 5 s mock period; constant cruise often has no StateFlow re-emits.
        SpeedIntegrator.onCalibratedSample(36f, 1_000L) // 10 m/s
        SpeedIntegrator.flushTo(6_000L)
        assertEquals(50.0, SpeedIntegrator.consumeDistanceM(), 1e-6)
    }

    /**
     * Mimics [MockLocationJob.takeDrDistanceM]: flushTo(now) then onRawSample(v, now).
     * Constant cruise must not lose distance when StateFlow does not re-emit.
     */
    @Test
    fun takeDrDistancePattern_constantCruiseAcrossManyTicks() {
        val v = 36f // 10 m/s
        var t = 1_000L
        SpeedIntegrator.onCalibratedSample(v, t)
        var total = 0.0
        // 60 s at 1 Hz mock period, no intermediate emits.
        repeat(60) {
            t += 1_000L
            SpeedIntegrator.flushTo(t)
            SpeedIntegrator.onCalibratedSample(v, t) // refresh hold at same t
            total += SpeedIntegrator.consumeDistanceM()
        }
        assertEquals(600.0, total, 0.5)
    }

    @Test
    fun takeDrDistancePattern_constantCruiseAtFiveSecondPeriod() {
        val v = 36f // 10 m/s
        var t = 1_000L
        SpeedIntegrator.onCalibratedSample(v, t)
        var total = 0.0
        repeat(12) { // 60 s / 5 s
            t += 5_000L
            SpeedIntegrator.flushTo(t)
            SpeedIntegrator.onCalibratedSample(v, t)
            total += SpeedIntegrator.consumeDistanceM()
        }
        assertEquals(600.0, total, 0.5)
    }

    @Test
    fun takeDrDistancePattern_idleThenPullAway() {
        // Parked at 0: gated path refreshes hold then discards pending (no drift).
        SpeedIntegrator.onCalibratedSample(0f, 1_000L)
        var t = 1_000L
        repeat(30) {
            t += 1_000L
            SpeedIntegrator.flushTo(t)
            SpeedIntegrator.onCalibratedSample(0f, t)
            SpeedIntegrator.discard()
            assertEquals(0.0, SpeedIntegrator.consumeDistanceM(), 0.0)
        }
        // Collector sees accel between ticks: 0→18→36 over 2 s.
        SpeedIntegrator.onCalibratedSample(18f, t + 1_000L)
        SpeedIntegrator.onCalibratedSample(36f, t + 2_000L)
        t += 2_000L
        // Mock tick: flush (no-op, already at t) + refresh hold.
        SpeedIntegrator.flushTo(t)
        SpeedIntegrator.onCalibratedSample(36f, t)
        val pullAway = SpeedIntegrator.consumeDistanceM()
        // Trapezoid 0→5 m/s over 1 s = 2.5; 5→10 over 1 s = 7.5; total 10 m.
        assertEquals(10.0, pullAway, 0.2)

        var cruise = 0.0
        repeat(3) {
            t += 1_000L
            SpeedIntegrator.flushTo(t)
            SpeedIntegrator.onCalibratedSample(36f, t)
            cruise += SpeedIntegrator.consumeDistanceM()
        }
        assertEquals(30.0, cruise, 0.2)
    }

    @Test
    fun gatedIdleRefresh_firstCruiseTickGetsFullPeriod() {
        // After long idle with gated refresh, first cruise tick must not clamp a huge gap.
        SpeedIntegrator.onCalibratedSample(0f, 1_000L)
        var t = 1_000L
        repeat(60) {
            t += 1_000L
            SpeedIntegrator.flushTo(t)
            SpeedIntegrator.onCalibratedSample(0f, t)
            SpeedIntegrator.discard()
        }
        // Jump to cruise at next tick (collector + takeDrDistance pattern).
        SpeedIntegrator.onCalibratedSample(36f, t + 200L) // small gap after last zero
        t += 1_000L
        SpeedIntegrator.flushTo(t)
        SpeedIntegrator.onCalibratedSample(36f, t)
        val first = SpeedIntegrator.consumeDistanceM()
        // ~trapezoid 0→10 over 0.2 s + hold 10 m/s for 0.8 s ≈ 1 + 8 = 9 m (near 10).
        assertEquals(10.0 * 0.2 / 2 + 10.0 * 0.8, first, 0.3)
    }

    @Test
    fun clampsLargeSampleGap() {
        SpeedIntegrator.onCalibratedSample(36f, 1_000L)
        // 5 s gap → only MAX_SAMPLE_DT_SEC (1.25) counted → 12.5 m
        SpeedIntegrator.onCalibratedSample(36f, 6_000L)
        assertEquals(10.0 * SpeedIntegrator.MAX_SAMPLE_DT_SEC, SpeedIntegrator.consumeDistanceM(), 1e-6)
    }

    @Test
    fun discardClearsPendingKeepsHeldSpeed() {
        SpeedIntegrator.onCalibratedSample(36f, 1_000L)
        SpeedIntegrator.onCalibratedSample(36f, 1_500L)
        SpeedIntegrator.discard()
        assertEquals(0.0, SpeedIntegrator.consumeDistanceM(), 0.0)
        SpeedIntegrator.flushTo(2_000L)
        assertEquals(5.0, SpeedIntegrator.consumeDistanceM(), 1e-6)
    }

    @Test
    fun nullSampleClearsHoldSoOutageDoesNotInventDistance() {
        SpeedIntegrator.onCalibratedSample(36f, 1_000L)
        SpeedIntegrator.onRawSample(null, 1_500L)
        SpeedIntegrator.flushTo(3_000L)
        assertEquals(0.0, SpeedIntegrator.consumeDistanceM(), 0.0)
        // Re-seed after outage: first sample after null does not integrate a gap.
        SpeedIntegrator.onCalibratedSample(36f, 3_000L)
        SpeedIntegrator.onCalibratedSample(36f, 4_000L)
        assertEquals(10.0, SpeedIntegrator.consumeDistanceM(), 1e-6)
    }

    @Test
    fun onRawSampleAppliesSpeedScale() {
        DriveCalibrationStore.update(DriveCalibrationOffsets(speedScale = 2f))
        SpeedIntegrator.onRawSample(18f, 1_000L) // calibrated 36 km/h = 10 m/s
        SpeedIntegrator.onRawSample(18f, 2_000L)
        assertEquals(10.0, SpeedIntegrator.consumeDistanceM(), 1e-6)
    }

    @Test
    fun manySamplesOverOneSecondBeatEndpointOnAccel() {
        // Mimic CAN pushes during hard accel; mock would only see the last value.
        SpeedIntegrator.onCalibratedSample(36f, 1_000L)
        var t = 1_000L
        for (i in 1..10) {
            t += 100L
            SpeedIntegrator.onCalibratedSample(36f + 3.6f * i, t) // +1 m/s per step → 20 m/s end
        }
        val integrated = SpeedIntegrator.consumeDistanceM()
        val endpoint = 20.0 * 1.0
        val trapIdeal = (10.0 + 20.0) * 0.5 * 1.0
        assertEquals(trapIdeal, integrated, 0.15)
        // Endpoint overshoots the trapezoid / true linear integral.
        assertTrue(endpoint > integrated + 1.0)
    }
}
