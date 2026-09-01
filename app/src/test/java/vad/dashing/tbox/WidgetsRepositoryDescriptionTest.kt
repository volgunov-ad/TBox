package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetsRepositoryDescriptionTest {

    @Test
    fun everyAvailableWidgetTypeHasDescription() {
        val missingDescriptions = WidgetsRepository.getAvailableDataKeysWidgets()
            .filter { WidgetsRepository.getDescriptionResForDataKey(it) == null }

        assertEquals(emptyList<String>(), missingDescriptions)
    }

    @Test
    fun nonInteractiveWidgetDoesNotHaveActionsDescription() {
        assertNull(WidgetsRepository.getActionsDescriptionResForDataKey("voltage"))
    }

    @Test
    fun interactiveWidgetHasActionsDescription() {
        assertNotNull(
            WidgetsRepository.getActionsDescriptionResForDataKey(DAY_NIGHT_THEME_WIDGET_DATA_KEY)
        )
    }

    @Test
    fun slaSpeedLimitWidgetIsOfferedInPicker() {
        assertTrue(
            WidgetsRepository.getAvailableDataKeysWidgets().contains(SLA_SPEED_LIMIT_WIDGET_DATA_KEY),
        )
        assertNotNull(WidgetsRepository.getDescriptionResForDataKey(SLA_SPEED_LIMIT_WIDGET_DATA_KEY))
    }

    @Test
    fun osmSpeedLimitWidgetIsOfferedInPicker() {
        assertTrue(
            WidgetsRepository.getAvailableDataKeysWidgets().contains(OSM_SPEED_LIMIT_WIDGET_DATA_KEY),
        )
        assertNotNull(WidgetsRepository.getDescriptionResForDataKey(OSM_SPEED_LIMIT_WIDGET_DATA_KEY))
        assertNull(WidgetsRepository.getActionsDescriptionResForDataKey(OSM_SPEED_LIMIT_WIDGET_DATA_KEY))
    }

    @Test
    fun speedLimiterWidgetIsOfferedInPicker() {
        assertTrue(
            WidgetsRepository.getAvailableDataKeysWidgets().contains(SPEED_LIMITER_WIDGET_DATA_KEY),
        )
        assertNotNull(WidgetsRepository.getDescriptionResForDataKey(SPEED_LIMITER_WIDGET_DATA_KEY))
        assertNotNull(WidgetsRepository.getActionsDescriptionResForDataKey(SPEED_LIMITER_WIDGET_DATA_KEY))
    }

    @Test
    fun cpuAndFreeRamWidgetsAreOfferedInPicker() {
        val keys = WidgetsRepository.getAvailableDataKeysWidgets()
        assertTrue(keys.contains(CPU_USAGE_WIDGET_DATA_KEY))
        assertTrue(keys.contains(FREE_RAM_PERCENT_WIDGET_DATA_KEY))
        assertNotNull(WidgetsRepository.getDescriptionResForDataKey(CPU_USAGE_WIDGET_DATA_KEY))
        assertNotNull(WidgetsRepository.getDescriptionResForDataKey(FREE_RAM_PERCENT_WIDGET_DATA_KEY))
        assertTrue(WidgetsRepository.supportsShowUnit(CPU_USAGE_WIDGET_DATA_KEY))
        assertTrue(WidgetsRepository.supportsShowUnit(FREE_RAM_PERCENT_WIDGET_DATA_KEY))
        assertTrue(WidgetsRepository.supportsValueAccuracy(CPU_USAGE_WIDGET_DATA_KEY))
        assertTrue(WidgetsRepository.supportsValueAccuracy(FREE_RAM_PERCENT_WIDGET_DATA_KEY))
    }

    @Test
    fun roadMatchCanvasWidgetIsOfferedWithoutMapKit() {
        val keys = WidgetsRepository.getAvailableDataKeysWidgets()
        assertTrue(keys.contains(ROAD_MATCH_MAP_WIDGET_DATA_KEY))
        assertNotNull(WidgetsRepository.getDescriptionResForDataKey(ROAD_MATCH_MAP_WIDGET_DATA_KEY))
        assertFalse(WidgetsRepository.supportsShowUnit(ROAD_MATCH_MAP_WIDGET_DATA_KEY))
        assertFalse(WidgetsRepository.supportsValueAccuracy(ROAD_MATCH_MAP_WIDGET_DATA_KEY))
    }

    @Test
    fun gnssDebugWidgetIsOfferedInPicker() {
        val keys = WidgetsRepository.getAvailableDataKeysWidgets()
        assertTrue(keys.contains(GNSS_DEBUG_WIDGET_DATA_KEY))
        assertNotNull(WidgetsRepository.getDescriptionResForDataKey(GNSS_DEBUG_WIDGET_DATA_KEY))
        assertNotNull(WidgetsRepository.getActionsDescriptionResForDataKey(GNSS_DEBUG_WIDGET_DATA_KEY))
        assertFalse(WidgetsRepository.supportsShowUnit(GNSS_DEBUG_WIDGET_DATA_KEY))
        assertFalse(WidgetsRepository.requiresTboxConnection(GNSS_DEBUG_WIDGET_DATA_KEY))
    }

    @Test
    fun gasBrakeWidgetIsOfferedWithoutTbox() {
        val keys = WidgetsRepository.getAvailableDataKeysWidgets()
        assertTrue(keys.contains(GAS_BRAKE_WIDGET_DATA_KEY))
        assertTrue(
            WidgetsRepository.getAvailableDataKeysWidgets(noTboxConnect = true)
                .contains(GAS_BRAKE_WIDGET_DATA_KEY),
        )
        assertTrue(WidgetsRepository.isWidgetOfferedWhenNoTbox(GAS_BRAKE_WIDGET_DATA_KEY))
        assertNotNull(WidgetsRepository.getDescriptionResForDataKey(GAS_BRAKE_WIDGET_DATA_KEY))
        assertTrue(WidgetsRepository.supportsShowUnit(GAS_BRAKE_WIDGET_DATA_KEY))
        assertTrue(WidgetsRepository.supportsValueAccuracy(GAS_BRAKE_WIDGET_DATA_KEY))
        assertFalse(WidgetsRepository.supportsUseMbCanVhal(GAS_BRAKE_WIDGET_DATA_KEY))
        assertFalse(WidgetsRepository.requiresTboxConnection(GAS_BRAKE_WIDGET_DATA_KEY))
    }

    @Test
    fun averageFuelConsumptionWidgetIsOfferedWithoutTbox() {
        val keys = WidgetsRepository.getAvailableDataKeysWidgets()
        assertTrue(keys.contains(AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY))
        assertTrue(
            WidgetsRepository.getAvailableDataKeysWidgets(noTboxConnect = true)
                .contains(AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY),
        )
        assertNotNull(
            WidgetsRepository.getDescriptionResForDataKey(AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY),
        )
        assertTrue(WidgetsRepository.supportsShowUnit(AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY))
        assertTrue(WidgetsRepository.supportsValueAccuracy(AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY))
        assertFalse(WidgetsRepository.supportsUseMbCanVhal(AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY))
        assertFalse(WidgetsRepository.requiresTboxConnection(AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY))
    }
}
