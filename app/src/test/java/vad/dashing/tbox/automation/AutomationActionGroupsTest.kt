package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

class AutomationActionGroupsTest {
    @Test
    fun builtinActionGroup_coversEveryType() {
        AutomationBuiltinActionType.entries.forEach { type ->
            val group = builtinActionGroup(type)
            assertTrue("No group for $type", group.label.isNotBlank())
        }
        assertEquals(
            "Круиз показан в группе ADAS",
            AutomationActionGroup.CAN_ADAS,
            builtinActionGroup(AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET),
        )
        assertEquals(
            "ESP_RELAY_SET скрыт из пикера, но группа всё равно задана",
            AutomationActionGroup.ESP,
            builtinActionGroup(AutomationBuiltinActionType.ESP_RELAY_SET),
        )
    }

    @Test
    fun canCatalog_everyEntryHasGroup() {
        AutomationCanCatalog.entries.forEach { entry ->
            assertTrue("No group for ${entry.bus}:${entry.propertyId}", entry.group.label.isNotBlank())
        }
    }

    @Test
    fun canCatalog_groupsFollowDomains() {
        assertEquals(
            AutomationActionGroup.CAN_BODY,
            AutomationCanCatalog.get(
                AutomationCanBus.VEHICLE,
                MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
            )?.group,
        )
        assertEquals(
            AutomationActionGroup.CAN_CLIMATE,
            AutomationCanCatalog.get(
                AutomationCanBus.VEHICLE,
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION,
            )?.group,
        )
        assertEquals(
            AutomationActionGroup.CAN_SEATS,
            AutomationCanCatalog.get(
                AutomationCanBus.VEHICLE,
                MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
            )?.group,
        )
        assertEquals(
            AutomationActionGroup.CAN_AUDIO,
            canActionGroup(AutomationCanBus.AUDIO, 0),
        )
    }

    @Test
    fun canKeywords_coverPopularSearchTerms() {
        assertTrue(
            canActionKeywords(
                AutomationCanBus.VEHICLE,
                MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
            ).contains("багажник"),
        )
        assertTrue(
            canActionKeywords(
                AutomationCanBus.VEHICLE,
                MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
            ).contains("обогрев"),
        )
        assertTrue(
            builtinActionKeywords(AutomationBuiltinActionType.RESTART_TBOX)
                .contains("перезагрузка"),
        )
    }

    @Test
    fun canEntries_a10OnlyHaveBothModesWhereMapped() {
        val trunk = AutomationCanCatalog.get(
            AutomationCanBus.VEHICLE,
            MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
        )
        assertTrue(trunk?.supports(HeadUnitCanMode.Android9MbCan) == true)
        // Audio CAN commands stay A9-only.
        AutomationCanCatalog.entries
            .filter { it.bus == AutomationCanBus.AUDIO }
            .forEach { entry ->
                assertTrue(
                    "Audio entry ${entry.propertyId} must be A9-only",
                    !entry.supports(HeadUnitCanMode.Android10Vhal),
                )
            }
    }
}
