package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
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

    @Test
    fun seatValues_useHeatVentLabelsNotRawCodes() {
        val front = AutomationCanCatalog.get(
            AutomationCanBus.VEHICLE,
            MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
        )
        assertNotNull(front)
        assertEquals("Выкл.", front!!.valueLabel(1))
        assertEquals("Подогрев 1", front.valueLabel(2))
        assertEquals("Подогрев 3", front.valueLabel(4))
        assertEquals("Вентиляция 1", front.valueLabel(5))
        assertEquals("Вентиляция 3", front.valueLabel(7))

        val rear = AutomationCanCatalog.get(
            AutomationCanBus.VEHICLE,
            MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH,
        )
        assertNotNull(rear)
        assertEquals("Выкл.", rear!!.valueLabel(1))
        assertEquals("Подогрев 2", rear.valueLabel(3))
    }

    @Test
    fun discreteCanValues_useReadableLabels() {
        val lights = AutomationCanCatalog.get(
            AutomationCanBus.VEHICLE,
            MbCanKnownVehiclePropertyId.LIGHTCONTROL,
        )
        assertEquals("AUTO", lights!!.valueLabel(1))
        assertEquals("OFF", lights.valueLabel(4))

        val drive = AutomationCanCatalog.get(
            AutomationCanBus.VEHICLE,
            MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        )
        assertEquals("NOR", drive!!.valueLabel(0))
        assertEquals("ECO", drive.valueLabel(2))

        val trunk = AutomationCanCatalog.get(
            AutomationCanBus.VEHICLE,
            MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
        )
        assertEquals("Открыть", trunk!!.valueLabel(1))
        assertEquals("Закрыть", trunk.valueLabel(2))

        val temp = AutomationCanCatalog.get(
            AutomationCanBus.VEHICLE,
            MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
        )
        assertEquals("16 °C", temp!!.valueLabel(160))
        assertEquals("22 °C", temp.valueLabel(220))
    }
}