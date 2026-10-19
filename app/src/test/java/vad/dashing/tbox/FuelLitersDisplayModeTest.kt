package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class FuelLitersDisplayModeTest {

    @Test
    fun normalizeDefaultsInvalidToRemaining() {
        assertEquals(FUEL_LITERS_DISPLAY_REMAINING, normalizeFuelLitersDisplayMode(0))
        assertEquals(FUEL_LITERS_DISPLAY_TO_FULL, normalizeFuelLitersDisplayMode(1))
        assertEquals(FUEL_LITERS_DISPLAY_REMAINING, normalizeFuelLitersDisplayMode(99))
        assertEquals(FUEL_LITERS_DISPLAY_REMAINING, normalizeFuelLitersDisplayMode(-1))
    }

    @Test
    fun appliesOnlyToLitersWidgets() {
        assertTrue(isFuelLitersDisplayModeWidgetDataKey(FUEL_LEVEL_LITERS_WIDGET_DATA_KEY))
        assertTrue(isFuelLitersDisplayModeWidgetDataKey(FUEL_LEVEL_LITERS_ACTUAL_WIDGET_DATA_KEY))
        assertTrue(isFuelLitersDisplayModeWidgetDataKey(FUEL_LEVEL_WIDGET_DATA_KEY))
        assertFalse(isFuelLitersDisplayModeWidgetDataKey(FUEL_LEVEL_PERCENTAGE_WIDGET_DATA_KEY))
        assertFalse(isFuelLitersDisplayModeWidgetDataKey("fuelLevelPercentageFiltered"))
        assertFalse(isFuelLitersDisplayModeWidgetDataKey(AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY))
    }

    @Test
    fun displayRemainingPassthrough() {
        assertEquals(
            12.5f,
            fuelLitersForDisplay(12.5f, tankLiters = 57, displayMode = FUEL_LITERS_DISPLAY_REMAINING),
        )
        assertNull(fuelLitersForDisplay(null, tankLiters = 57, displayMode = FUEL_LITERS_DISPLAY_REMAINING))
    }

    @Test
    fun displayToFullUsesTankMinusCurrent() {
        assertEquals(
            32.5f,
            fuelLitersForDisplay(24.5f, tankLiters = 57, displayMode = FUEL_LITERS_DISPLAY_TO_FULL),
        )
        assertEquals(
            0f,
            fuelLitersForDisplay(60f, tankLiters = 57, displayMode = FUEL_LITERS_DISPLAY_TO_FULL),
        )
        assertEquals(
            0f,
            fuelLitersForDisplay(57f, tankLiters = 57, displayMode = FUEL_LITERS_DISPLAY_TO_FULL),
        )
        assertNull(fuelLitersForDisplay(null, tankLiters = 57, displayMode = FUEL_LITERS_DISPLAY_TO_FULL))
    }

    @Test
    fun displayToFullClampsNegativeTankToZero() {
        assertEquals(
            0f,
            fuelLitersForDisplay(5f, tankLiters = -10, displayMode = FUEL_LITERS_DISPLAY_TO_FULL),
        )
    }
}
