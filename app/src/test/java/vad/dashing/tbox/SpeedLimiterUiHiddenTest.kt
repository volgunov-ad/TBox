package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.automation.AutomationCanCatalog
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.ui.carSettingsTabMbCanSignals

class SpeedLimiterUiHiddenTest {
    @Test
    fun flagHidesConfigurableLimiterNotSlaSign() {
        assertTrue(SPEED_LIMITER_UI_HIDDEN)
        assertTrue(isSpeedLimiterHiddenFromWidgetPicker(SPEED_LIMITER_WIDGET_DATA_KEY))
        assertFalse(isSpeedLimiterHiddenFromWidgetPicker(SLA_SPEED_LIMIT_WIDGET_DATA_KEY))
        assertFalse(isSpeedLimiterHiddenFromWidgetPicker(OSM_SPEED_LIMIT_WIDGET_DATA_KEY))
        assertTrue(
            isSpeedLimiterHiddenFromAutomationPicker(
                MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH,
            ),
        )
        assertTrue(
            isSpeedLimiterHiddenFromAutomationPicker(
                MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET,
            ),
        )
        assertFalse(
            isSpeedLimiterHiddenFromAutomationPicker(
                MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH,
            ),
        )
        assertFalse(isSpeedLimiterHiddenFromAutomationPicker(AutomationSignalId.TSR_SWITCH))
    }

    @Test
    fun placedLimiterTilesSurviveConfigRoundTrip() {
        val json = serializeWidgetConfigs(
            listOf(FloatingDashboardWidgetConfig(dataKey = SPEED_LIMITER_WIDGET_DATA_KEY)),
        )
        val parsed = parseWidgetConfigsFromString(json)
        assertEquals(SPEED_LIMITER_WIDGET_DATA_KEY, parsed.single().dataKey)
    }

    @Test
    fun automationPickersOmitLimiterCanIdsAndKeepTsr() {
        assertTrue(
            AutomationCanCatalog.pickerEntries().none {
                it.propertyId in SPEED_LIMITER_AUTOMATION_PROPERTY_IDS
            },
        )
        val stateSignals = AutomationSignalCatalog.pickerSignalsOfType(
            AutomationSignalValueType.STATE,
        )
        assertTrue(AutomationSignalId.TSR_SWITCH in stateSignals)
        assertTrue(SPEED_LIMITER_AUTOMATION_SIGNAL_IDS.none { it in stateSignals })
    }

    @Test
    fun carSettingsDoesNotSubscribeLimiterWhileHidden() {
        assertFalse(carSettingsTabMbCanSignals().contains(MbCanSignal.SpeedLimiter))
        assertTrue(carSettingsTabMbCanSignals().contains(MbCanSignal.SlaSpeedLimit))
    }
}
