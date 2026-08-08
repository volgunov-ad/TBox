package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.Wheels

class TirePressureDomainTest {

    @Test
    fun vhalPressure_stockScaleAndInvalidBounds() {
        assertEquals(2.2f, TirePressureDomain.decodeVhalPressureBar(80)!!, 0.001f)
        assertNull(TirePressureDomain.decodeVhalPressureBar(0))
        assertNull(TirePressureDomain.decodeVhalPressureBar(-1))
        // 3.5 / 0.0275 ≈ 127.27 → 128 exceeds max
        assertNull(TirePressureDomain.decodeVhalPressureBar(128))
    }

    @Test
    fun vhalTemperature_offsetAndInvalidBounds() {
        assertEquals(25f, TirePressureDomain.decodeVhalTemperatureC(85)!!, 0.001f)
        assertNull(TirePressureDomain.decodeVhalTemperatureC(0))
        assertNull(TirePressureDomain.decodeVhalTemperatureC(150))
        assertNull(TirePressureDomain.decodeVhalTemperatureC(151))
    }

    @Test
    fun mbCanPressure_invalidSentinel() {
        assertEquals(2.3f, TirePressureDomain.decodeMbCanPressureBar(2.3f)!!, 0.001f)
        assertNull(TirePressureDomain.decodeMbCanPressureBar(-1f))
        assertNull(TirePressureDomain.decodeMbCanPressureBar(0f))
        assertNull(TirePressureDomain.decodeMbCanPressureBar(Float.NaN))
    }

    @Test
    fun mbCanTemperature_invalidSentinel() {
        assertEquals(30f, TirePressureDomain.decodeMbCanTemperatureC(30)!!, 0.001f)
        assertEquals(-5f, TirePressureDomain.decodeMbCanTemperatureC(-5)!!, 0.001f)
        assertNull(TirePressureDomain.decodeMbCanTemperatureC(-100))
    }

    @Test
    fun resolvePressureAfterSample_keepsValueInsideDebounce() {
        val (value, last) = TirePressureDomain.resolvePressureAfterSample(
            current = 2.1f,
            lastTimeNotNull = 1_000L,
            incoming = null,
            now = 1_500L,
            debounceMs = 2_000L,
        )
        assertEquals(2.1f, value!!, 0.001f)
        assertEquals(1_000L, last)
    }

    @Test
    fun resolvePressureAfterSample_clearsAfterDebounce() {
        val (value, last) = TirePressureDomain.resolvePressureAfterSample(
            current = 2.1f,
            lastTimeNotNull = 1_000L,
            incoming = null,
            now = 4_000L,
            debounceMs = 2_000L,
        )
        assertNull(value)
        assertEquals(1_000L, last)
    }

    @Test
    fun resolvePressureAfterSample_validUpdatesTimestamp() {
        val (value, last) = TirePressureDomain.resolvePressureAfterSample(
            current = 2.0f,
            lastTimeNotNull = 1_000L,
            incoming = 2.4f,
            now = 9_000L,
            debounceMs = 2_000L,
        )
        assertEquals(2.4f, value!!, 0.001f)
        assertEquals(9_000L, last)
    }

    @Test
    fun mergeWheelsPressureCorner_onlyTouchesOneWheel() {
        val current = Wheels(
            wheel1 = 2.0f,
            wheel2 = 2.1f,
            wheel1LastTimeNotNull = 100L,
            wheel2LastTimeNotNull = 100L,
        )
        val merged = TirePressureDomain.mergeWheelsPressureCorner(
            current = current,
            corner = 0,
            incoming = null,
            now = 150L,
            debounceMs = 2_000L,
        )
        assertEquals(2.0f, merged.wheel1!!, 0.001f)
        assertEquals(2.1f, merged.wheel2!!, 0.001f)
    }

    @Test
    fun restoreMissingPressures_fillsNullsAndRefreshesTimestamps() {
        val current = Wheels(wheel1 = null, wheel2 = 2.2f, wheel2LastTimeNotNull = 50L)
        val saved = Wheels(wheel1 = 2.0f, wheel2 = 9.9f, wheel3 = 2.3f)
        val restored = TirePressureDomain.restoreMissingPressures(current, saved, now = 500L)
        assertEquals(2.0f, restored.wheel1!!, 0.001f)
        assertEquals(500L, restored.wheel1LastTimeNotNull)
        assertEquals(2.2f, restored.wheel2!!, 0.001f)
        assertEquals(50L, restored.wheel2LastTimeNotNull)
        assertEquals(2.3f, restored.wheel3!!, 0.001f)
        assertEquals(500L, restored.wheel3LastTimeNotNull)
    }

    @Test
    fun pressureNullDebounceMs_matchesTboxDurations() {
        assertEquals(2_000L, TirePressureDomain.pressureNullDebounceMs(persistAcrossStops = false))
        assertEquals(300_000L, TirePressureDomain.pressureNullDebounceMs(persistAcrossStops = true))
    }
}
