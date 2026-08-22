package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRetentionAccuracyTest {

    @Test
    fun ageMs_zeroWhenNotStarted() {
        assertEquals(0L, MockRetentionAccuracy.ageMs(0L, 5_000L))
        assertEquals(0L, MockRetentionAccuracy.ageMs(-1L, 5_000L))
        assertEquals(0L, MockRetentionAccuracy.ageMs(10_000L, 5_000L))
    }

    @Test
    fun ageMs_countsElapsed() {
        assertEquals(3_500L, MockRetentionAccuracy.ageMs(1_000L, 4_500L))
    }

    @Test
    fun horizontal_atAgeZeroUsesBase() {
        assertEquals(8f, MockRetentionAccuracy.horizontalM(8f, 0L), 1e-3f)
        assertEquals(5f, MockRetentionAccuracy.horizontalM(Float.NaN, 0L), 1e-3f)
        assertEquals(5f, MockRetentionAccuracy.horizontalM(-1f, 0L), 1e-3f)
    }

    @Test
    fun horizontal_growsLinearlyTowardDefaultCeiling() {
        val start = 5f
        val after60s = MockRetentionAccuracy.horizontalM(start, 60_000L)
        val after210s = MockRetentionAccuracy.horizontalM(start, 210_000L)
        val afterLong = MockRetentionAccuracy.horizontalM(start, 600_000L)
        assertTrue(after60s > start)
        assertEquals(MockRetentionAccuracy.DEFAULT_CEILING_M, after210s, 0.5f)
        assertEquals(MockRetentionAccuracy.DEFAULT_CEILING_M, afterLong, 0f)
        assertEquals(
            start + MockRetentionAccuracy.growthMPerS(MockRetentionAccuracy.DEFAULT_CEILING_M) * 60f,
            after60s,
            1e-2f,
        )
    }

    @Test
    fun horizontal_respectsCustomCeiling() {
        val ceiling = 40f
        val after210s = MockRetentionAccuracy.horizontalM(5f, 210_000L, ceilingM = ceiling)
        assertEquals(ceiling, after210s, 0.5f)
    }

    @Test
    fun normalizeCeilingM_clampsToRange() {
        assertEquals(10f, MockRetentionAccuracy.normalizeCeilingM(3f), 0f)
        assertEquals(100f, MockRetentionAccuracy.normalizeCeilingM(150f), 0f)
        assertEquals(75, MockRetentionAccuracy.normalizeCeilingM(75))
    }

    @Test
    fun horizontal_highBaseStillCapsAtCeiling() {
        assertEquals(60f, MockRetentionAccuracy.horizontalM(60f, 0L), 1e-3f)
        assertEquals(
            MockRetentionAccuracy.DEFAULT_CEILING_M,
            MockRetentionAccuracy.horizontalM(60f, 120_000L),
            1e-3f,
        )
        assertEquals(
            MockRetentionAccuracy.DEFAULT_CEILING_M,
            MockRetentionAccuracy.horizontalM(90f, 0L),
            1e-3f,
        )
    }
}
