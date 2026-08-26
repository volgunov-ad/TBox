package vad.dashing.tbox.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.MbCanCommandPolicy
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

class AutomationCanCatalogTest {
    @Test
    fun catalog_excludesRawRebootAndCruisePulses() {
        val ids = AutomationCanCatalog.entries
            .filter { it.bus == AutomationCanBus.VEHICLE }
            .map { it.propertyId }
            .toSet()

        assertFalse(MbCanKnownVehiclePropertyId.SYSTEM_REBOOT in ids)
        assertFalse(MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH in ids)
        assertFalse(MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET in ids)
        assertFalse(MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL in ids)
        assertFalse(MbCanKnownVehiclePropertyId.MFS_CANCEL in ids)
        assertFalse(MbCanKnownVehiclePropertyId.MFS_RES_PLUS in ids)
        assertFalse(MbCanKnownVehiclePropertyId.MFS_SET_MINUS in ids)
        assertTrue(AutomationCanCatalog.entries.none { it.policy is MbCanCommandPolicy.SetAnyInt })
    }

    @Test
    fun trunk_isAvailableOnlyAsStaffPulse() {
        val entry = AutomationCanCatalog.get(
            AutomationCanBus.VEHICLE,
            MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
        )

        assertNotNull(entry)
        assertTrue(entry?.allowedOperations == setOf(AutomationCanOperation.TRUNK_PULSE))
        assertFalse(
            AutomationCanCatalog.isAllowed(
                AutomationAction.CanCommand(
                    bus = AutomationCanBus.VEHICLE,
                    propertyId = MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
                    operation = AutomationCanOperation.SET,
                    value = 1,
                ),
            ),
        )
        assertTrue(
            AutomationCanCatalog.isAllowed(
                AutomationAction.CanCommand(
                    bus = AutomationCanBus.VEHICLE,
                    propertyId = MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
                    operation = AutomationCanOperation.TRUNK_PULSE,
                    value = 1,
                ),
            ),
        )
    }

    @Test
    fun entries_haveNonEmptyLabelsAndAllowedOperations() {
        assertTrue(AutomationCanCatalog.entries.isNotEmpty())
        AutomationCanCatalog.entries.forEach { entry ->
            assertTrue(entry.label.isNotBlank())
            assertTrue(entry.allowedOperations.isNotEmpty())
            assertTrue(entry.supportedModes.isNotEmpty())
        }
    }
}