package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.AutomationCodec
import vad.dashing.tbox.automation.AutomationCruiseActions
import vad.dashing.tbox.automation.AutomationDefinition
import vad.dashing.tbox.automation.AutomationDocument
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.automation.AutomationSystemEvent
import vad.dashing.tbox.automation.AutomationTrigger
import vad.dashing.tbox.automation.AutomationValidator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VehicleFeatureTypesTest {

    @Test
    fun defaults_areAccAndDualZone() {
        assertEquals(GlobalCruiseControlType.ACC, GlobalCruiseControlType.DEFAULT)
        assertEquals(ClimateControlType.DUAL_ZONE, ClimateControlType.DEFAULT)
        assertEquals(
            GlobalCruiseControlType.ACC,
            GlobalCruiseControlType.fromStorageKey(null),
        )
        assertEquals(
            ClimateControlType.DUAL_ZONE,
            ClimateControlType.fromStorageKey(null),
        )
        assertEquals("acc", GlobalCruiseControlType.ACC.storageKey)
        assertEquals("ccs", GlobalCruiseControlType.CCS.storageKey)
        assertEquals("ordinary_ac", ClimateControlType.ORDINARY_AC.storageKey)
        assertEquals("single_zone", ClimateControlType.SINGLE_ZONE.storageKey)
        assertEquals("dual_zone", ClimateControlType.DUAL_ZONE.storageKey)
    }

    @Test
    fun climateUiCapabilities_matchModes() {
        val ordinary = ClimateControlType.ORDINARY_AC.uiCapabilities()
        assertFalse(ordinary.showAuto)
        assertFalse(ordinary.showSync)
        assertFalse(ordinary.showPassengerTemp)

        val single = ClimateControlType.SINGLE_ZONE.uiCapabilities()
        assertTrue(single.showAuto)
        assertFalse(single.showSync)
        assertFalse(single.showPassengerTemp)

        val dual = ClimateControlType.DUAL_ZONE.uiCapabilities()
        assertTrue(dual.showAuto)
        assertTrue(dual.showSync)
        assertTrue(dual.showPassengerTemp)
    }

    @Test
    fun widgetPicker_filtersClimateByType() {
        assertTrue(
            isClimateWidgetHiddenFromPicker("hvacAutoWidget", ClimateControlType.ORDINARY_AC),
        )
        assertFalse(
            isClimateWidgetHiddenFromPicker("hvacAutoWidget", ClimateControlType.SINGLE_ZONE),
        )
        assertTrue(
            isClimateWidgetHiddenFromPicker(
                HVAC_SYNC_WIDGET_DATA_KEY,
                ClimateControlType.SINGLE_ZONE,
            ),
        )
        assertTrue(
            isClimateWidgetHiddenFromPicker(
                HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY,
                ClimateControlType.ORDINARY_AC,
            ),
        )
        assertFalse(
            isClimateWidgetHiddenFromPicker(
                HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY,
                ClimateControlType.ORDINARY_AC,
            ),
        )
        assertFalse(
            isClimateWidgetHiddenFromPicker(
                HVAC_SYNC_WIDGET_DATA_KEY,
                ClimateControlType.DUAL_ZONE,
            ),
        )

        val ordinaryKeys = WidgetsRepository.getAvailableDataKeysWidgets(
            climateType = ClimateControlType.ORDINARY_AC,
        )
        assertFalse(ordinaryKeys.contains("hvacAutoWidget"))
        assertFalse(ordinaryKeys.contains(HVAC_SYNC_WIDGET_DATA_KEY))
        assertFalse(ordinaryKeys.contains(HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY))
        assertTrue(ordinaryKeys.contains(HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY))
        assertTrue(ordinaryKeys.contains(ACC_CRUISE_WIDGET_DATA_KEY))
        assertTrue(ordinaryKeys.contains(CRUISE_STATUS_WIDGET_DATA_KEY))
    }

    @Test
    fun automationPicker_hidesNonMatchingCruiseSignal() {
        VehicleFeatureSettings.updateCruise(GlobalCruiseControlType.ACC)
        val accPicker = AutomationSignalCatalog.pickerSignalsOfType(AutomationSignalValueType.STATE)
        assertTrue(accPicker.contains(AutomationSignalId.ACC_CRUISE_STATE))
        assertFalse(accPicker.contains(AutomationSignalId.CCS_CRUISE_STATE))

        VehicleFeatureSettings.updateCruise(GlobalCruiseControlType.CCS)
        val ccsPicker = AutomationSignalCatalog.pickerSignalsOfType(AutomationSignalValueType.STATE)
        assertFalse(ccsPicker.contains(AutomationSignalId.ACC_CRUISE_STATE))
        assertTrue(ccsPicker.contains(AutomationSignalId.CCS_CRUISE_STATE))

        // Restore default for other suites sharing process state.
        VehicleFeatureSettings.updateCruise(GlobalCruiseControlType.DEFAULT)
    }

    @Test
    fun automationCodec_legacyCruiseKindStillDecodes_emptyModeValid() {
        val definition = AutomationDefinition(
            id = "cruise-migrate",
            name = "cruise",
            triggers = listOf(
                AutomationTrigger.SystemEvent(
                    id = "1",
                    event = AutomationSystemEvent.MENU_OPENED,
                ),
            ),
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET,
                    intValue = 100,
                    stringValue = "ccs:200:100",
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.CRUISE_PAUSE,
                    stringValue = "",
                ),
            ),
        )
        val document = AutomationDocument(automations = listOf(definition))
        val decoded = AutomationCodec.decode(AutomationCodec.encode(document)).getOrThrow()
        assertEquals(document, decoded)
        assertTrue(AutomationValidator.validate(decoded).isEmpty())

        VehicleFeatureSettings.updateCruise(GlobalCruiseControlType.CCS)
        val engage = AutomationCruiseActions.parseEngageParams(
            AutomationAction.Builtin(
                type = AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET,
                intValue = 100,
                stringValue = "acc:200:100",
            ),
        )
        // Runtime always follows global setting, not the legacy stringValue prefix.
        assertEquals(CruiseControlType.CCS, engage.cruiseControlType)
        assertEquals(200, engage.increaseIntervalMs)
        assertEquals(100, engage.decreaseIntervalMs)
        assertEquals(
            CruiseControlType.CCS,
            AutomationCruiseActions.resolveRuntimeMode(),
        )
        VehicleFeatureSettings.updateCruise(GlobalCruiseControlType.DEFAULT)
    }

    @Test
    fun bleWire_roundTripsClimateLayout() {
        assertEquals(0, ClimateControlType.ORDINARY_AC.toBleWire())
        assertEquals(1, ClimateControlType.SINGLE_ZONE.toBleWire())
        assertEquals(2, ClimateControlType.DUAL_ZONE.toBleWire())
        assertEquals(
            ClimateControlType.SINGLE_ZONE,
            climateControlTypeFromBleWire(1),
        )
    }
}
