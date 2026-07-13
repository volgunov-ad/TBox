package vad.dashing.tbox

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.utils.InOutTemperatureNullDebounce

class InOutTemperatureNullDebounceTest {

    private val debounceMs = 300_000L

    @After
    fun tearDown() {
        CanDataRepository.resetInOutTemperatureStateForTest()
    }

    @Test
    fun validReading_updatesValueAndTimestamp() {
        val resolved = InOutTemperatureNullDebounce.resolveAfterProbe(
            current = null,
            lastTimeNotNull = null,
            decodedCelsius = 22f,
            now = 10_000L,
            debounceMs = debounceMs,
        )
        assertEquals(22f, resolved.value)
        assertEquals(10_000L, resolved.lastTimeNotNull)
    }

    @Test
    fun invalidWithinDebounce_holdsPreviousValue() {
        val resolved = InOutTemperatureNullDebounce.resolveAfterProbe(
            current = 18.5f,
            lastTimeNotNull = 100_000L,
            decodedCelsius = 120f,
            now = 200_000L,
            debounceMs = debounceMs,
        )
        assertEquals(18.5f, resolved.value)
        assertEquals(100_000L, resolved.lastTimeNotNull)
    }

    @Test
    fun invalidAfterDebounce_clearsValue() {
        val resolved = InOutTemperatureNullDebounce.resolveAfterProbe(
            current = 18.5f,
            lastTimeNotNull = 100_000L,
            decodedCelsius = -50f,
            now = 100_000L + debounceMs + 1,
            debounceMs = debounceMs,
        )
        assertNull(resolved.value)
        assertEquals(100_000L, resolved.lastTimeNotNull)
    }

    @Test
    fun firstInvalidAfterBoot_clearsImmediatelyWhenElapsedSinceBootExceedsDebounce() {
        val resolved = InOutTemperatureNullDebounce.resolveAfterProbe(
            current = null,
            lastTimeNotNull = null,
            decodedCelsius = 200f,
            now = debounceMs + 1,
            debounceMs = debounceMs,
        )
        assertNull(resolved.value)
    }

    @Test
    fun repository_applyOutsideTemperatureFromCan_holdsValueWithinDebounce() {
        CanDataRepository.applyOutsideTemperatureFromCan(20f, now = 1_000L, nullDebounceMs = debounceMs)
        CanDataRepository.applyOutsideTemperatureFromCan(200f, now = 50_000L, nullDebounceMs = debounceMs)
        assertEquals(20f, CanDataRepository.outsideTemperature.value)
    }

    @Test
    fun repository_applyInsideTemperatureFromCan_clearsAfterDebounce() {
        CanDataRepository.applyInsideTemperatureFromCan(24f, now = 1_000L, nullDebounceMs = debounceMs)
        CanDataRepository.applyInsideTemperatureFromCan(200f, now = 1_000L + debounceMs + 1, nullDebounceMs = debounceMs)
        assertNull(CanDataRepository.insideTemperature.value)
    }
}
