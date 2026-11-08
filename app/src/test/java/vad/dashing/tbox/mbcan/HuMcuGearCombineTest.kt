package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.needsHeadUnitGearNumbers
import vad.dashing.tbox.needsMcuCurrentGear
import vad.dashing.tbox.valueToString

/**
 * Reproduces the DataProvider merge of MCU gear + VHAL gear used by
 * gearBoxCurrentGear_can, and the panel hold gates that must match the renderer.
 */
class HuMcuGearCombineTest {
    @Test
    fun combinePrefersMcuThenVhal() = runBlocking {
        val mcu = MutableStateFlow<Int?>(null)
        val vhal = MutableStateFlow<Int?>(3)
        val merged = combine(mcu, vhal) { fromMcu, fromVhal -> fromMcu ?: fromVhal }
        assertEquals(3, merged.first())
        mcu.value = 2
        assertEquals(2, merged.first())
        mcu.value = null
        vhal.value = null
        assertNull(merged.first())
    }

    @Test
    fun valueToStringAcceptsCombinedNullableInt() {
        assertEquals("2", valueToString(2, 1))
        assertEquals("", valueToString(null as Int?, 1))
    }

    @Test
    fun needsMcuCurrentGear_onlyWhenCanPathOrNoTbox() {
        val tboxOnly = FloatingDashboardWidgetConfig(dataKey = "gearBoxCurrentGear")
        assertFalse(tboxOnly.needsMcuCurrentGear(noTboxConnect = false))
        assertTrue(tboxOnly.needsMcuCurrentGear(noTboxConnect = true))

        val canTile = FloatingDashboardWidgetConfig(
            dataKey = "gearBoxCurrentGear",
            useMbCanVhal = true,
        )
        assertTrue(canTile.needsMcuCurrentGear(noTboxConnect = false))
        assertFalse(
            FloatingDashboardWidgetConfig(dataKey = "voltage")
                .needsMcuCurrentGear(noTboxConnect = true),
        )
    }

    @Test
    fun needsHeadUnitGearNumbers_coversPreparedAndNoTboxCurrent() {
        val prepared = FloatingDashboardWidgetConfig(
            dataKey = "gearBoxPreparedGear",
            useMbCanVhal = true,
        )
        assertTrue(prepared.needsHeadUnitGearNumbers(noTboxConnect = false))

        val currentNoTbox = FloatingDashboardWidgetConfig(dataKey = "gearBoxCurrentGear")
        assertTrue(currentNoTbox.needsHeadUnitGearNumbers(noTboxConnect = true))
        assertFalse(currentNoTbox.needsHeadUnitGearNumbers(noTboxConnect = false))
    }
}
