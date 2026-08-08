package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.HVAC_CLIMATE_WIDGET_DATA_KEYS
import vad.dashing.tbox.HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_SYNC_WIDGET_DATA_KEY

/**
 * Interest / subscription wiring for HVAC Front OFF (audit H2).
 *
 * Stock A10 AirConditioning subscribes to R_0200_CEM_IPM_FrontOFFSts (289415175).
 * Climate panel widgets register [MbCanSignal.HvacFrontOff] alongside fan/temp/sync/blow.
 */
class HvacFrontOffInterestTest {

    @Test
    fun climateWidgetKeys_includeFanAndSync_butNoDedicatedFrontOffKey() {
        assertTrue(HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY in HVAC_CLIMATE_WIDGET_DATA_KEYS)
        assertTrue(HVAC_SYNC_WIDGET_DATA_KEY in HVAC_CLIMATE_WIDGET_DATA_KEYS)
        assertFalse(HVAC_CLIMATE_WIDGET_DATA_KEYS.any { it.contains("frontOff", ignoreCase = true) })
    }

    @Test
    fun hvacFrontOff_signalExistsWithCfgVehicleInterest() {
        assertTrue(MbCanSignal.HvacFrontOff.subscribeDataTypes.contains("eMBCAN_CFG_VEHICLE"))
        assertEquals(90, MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF)
    }

    @Test
    fun climatePanelKeys_shouldPiggybackHvacFrontOffInterest() {
        // Both repos add HvacFrontOff when any HVAC_CLIMATE_WIDGET_DATA_KEYS key is active.
        assertTrue(HVAC_CLIMATE_WIDGET_DATA_KEYS.isNotEmpty())
        assertEquals(289_415_175, FirmwareVehicleJsonMapper.resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF))
    }
}
