package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.OSM_SPEED_LIMIT_WIDGET_DATA_KEY
import vad.dashing.tbox.ROAD_MATCH_MAP_WIDGET_DATA_KEY
import vad.dashing.tbox.SLA_SPEED_LIMIT_WIDGET_DATA_KEY
import vad.dashing.tbox.location.MockCanSpeedMode
import vad.dashing.tbox.location.MockPowerState

class RoadMatchDemandTest {

    @Test
    fun toggleOffAndNoWidget_isNone() {
        val demand = RoadMatchDemand.resolve(
            toggleOn = false,
            power = MockPowerState.ALWAYS_ON,
            canMode = MockCanSpeedMode.CONSTANT,
            widgetPresent = false,
        )
        assertEquals(RoadMatchDemand.NONE, demand)
    }

    @Test
    fun widgetOnly_matchesWithoutCorrectingPose() {
        val demand = RoadMatchDemand.resolve(
            toggleOn = false,
            power = MockPowerState.OFF,
            canMode = MockCanSpeedMode.NONE,
            widgetPresent = true,
        )
        assertTrue(demand.matchNeeded)
        assertFalse(demand.correctPose)
    }

    @Test
    fun toggleOnDirectMode_doesNotCorrectPose() {
        val demand = RoadMatchDemand.resolve(
            toggleOn = true,
            power = MockPowerState.ALWAYS_ON,
            canMode = MockCanSpeedMode.NONE,
            widgetPresent = false,
        )
        assertFalse(demand.matchNeeded)
        assertFalse(demand.correctPose)
    }

    @Test
    fun toggleOnDirectModeWithWidget_matchesWithoutCorrectingPose() {
        val demand = RoadMatchDemand.resolve(
            toggleOn = true,
            power = MockPowerState.ALWAYS_ON,
            canMode = MockCanSpeedMode.NONE,
            widgetPresent = true,
        )
        assertTrue(demand.matchNeeded)
        assertFalse(demand.correctPose)
    }

    @Test
    fun toggleOnAdvanced_correctsPose() {
        val demand = RoadMatchDemand.resolve(
            toggleOn = true,
            power = MockPowerState.ALWAYS_ON,
            canMode = MockCanSpeedMode.CONSTANT,
            widgetPresent = false,
        )
        assertTrue(demand.matchNeeded)
        assertTrue(demand.correctPose)
        assertEquals(RoadMatchMode.ORDINARY, demand.mode)
    }

    @Test
    fun resolve_passesRailsModeThrough() {
        val demand = RoadMatchDemand.resolve(
            toggleOn = true,
            power = MockPowerState.ALWAYS_ON,
            canMode = MockCanSpeedMode.CONSTANT,
            widgetPresent = false,
            mode = RoadMatchMode.RAILS,
        )
        assertTrue(demand.correctPose)
        assertEquals(RoadMatchMode.RAILS, demand.mode)
    }

    @Test
    fun resolve_passesFreeTurnsModeThrough() {
        val demand = RoadMatchDemand.resolve(
            toggleOn = true,
            power = MockPowerState.ALWAYS_ON,
            canMode = MockCanSpeedMode.CONSTANT,
            widgetPresent = false,
            mode = RoadMatchMode.FREE_TURNS,
        )
        assertTrue(demand.correctPose)
        assertEquals(RoadMatchMode.FREE_TURNS, demand.mode)
    }

    @Test
    fun toggleOnWhenNoFix_correctsPoseEvenIfStoredModeIsDirect() {
        val demand = RoadMatchDemand.resolve(
            toggleOn = true,
            power = MockPowerState.WHEN_NO_FIX,
            canMode = MockCanSpeedMode.NONE,
            widgetPresent = false,
        )
        assertTrue(demand.matchNeeded)
        assertTrue(demand.correctPose)
    }

    @Test
    fun toggleOnWhilePowerOff_doesNotCorrectPose() {
        val demand = RoadMatchDemand.resolve(
            toggleOn = true,
            power = MockPowerState.OFF,
            canMode = MockCanSpeedMode.CONSTANT,
            widgetPresent = false,
        )
        assertFalse(demand.matchNeeded)
        assertFalse(demand.correctPose)
    }

    @Test
    fun dashboardRoadMatchMapWidgetCounts() {
        assertTrue(
            RoadMatchWidgetPresence.isPresent(
                dashboardWidgets = listOf(
                    FloatingDashboardWidgetConfig(dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY),
                ),
                floatingPanels = emptyList(),
                mainScreenPanels = emptyList(),
            ),
        )
    }

    @Test
    fun dashboardOsmWidgetCounts_slaDoesNot() {
        assertTrue(
            RoadMatchWidgetPresence.isPresent(
                dashboardWidgets = listOf(
                    FloatingDashboardWidgetConfig(dataKey = OSM_SPEED_LIMIT_WIDGET_DATA_KEY),
                ),
                floatingPanels = emptyList(),
                mainScreenPanels = emptyList(),
            ),
        )
        assertFalse(
            RoadMatchWidgetPresence.isPresent(
                dashboardWidgets = listOf(
                    FloatingDashboardWidgetConfig(dataKey = SLA_SPEED_LIMIT_WIDGET_DATA_KEY),
                ),
                floatingPanels = emptyList(),
                mainScreenPanels = emptyList(),
            ),
        )
    }

    @Test
    fun disabledPanelsDoNotCount_enabledDo() {
        val osm = listOf(FloatingDashboardWidgetConfig(dataKey = OSM_SPEED_LIMIT_WIDGET_DATA_KEY))
        assertFalse(
            RoadMatchWidgetPresence.isPresent(
                dashboardWidgets = emptyList(),
                floatingPanels = listOf(floatingPanel(enabled = false, widgets = osm)),
                mainScreenPanels = listOf(mainPanel(enabled = false, widgets = osm)),
            ),
        )
        assertTrue(
            RoadMatchWidgetPresence.isPresent(
                dashboardWidgets = emptyList(),
                floatingPanels = listOf(floatingPanel(enabled = true, widgets = osm)),
                mainScreenPanels = emptyList(),
            ),
        )
        assertTrue(
            RoadMatchWidgetPresence.isPresent(
                dashboardWidgets = emptyList(),
                floatingPanels = emptyList(),
                mainScreenPanels = listOf(mainPanel(enabled = true, widgets = osm)),
            ),
        )
    }

    private fun floatingPanel(
        enabled: Boolean,
        widgets: List<FloatingDashboardWidgetConfig>,
    ) = FloatingDashboardConfig(
        id = "f1",
        name = "Float",
        enabled = enabled,
        widgetsConfig = widgets,
        rows = 1,
        cols = 1,
        width = 100,
        height = 100,
        startX = 0,
        startY = 0,
        background = false,
        clickAction = false,
    )

    private fun mainPanel(
        enabled: Boolean,
        widgets: List<FloatingDashboardWidgetConfig>,
    ) = MainScreenPanelConfig(
        id = "p1",
        name = "Main",
        enabled = enabled,
        widgetsConfig = widgets,
        rows = 1,
        cols = 1,
        relX = 0f,
        relY = 0f,
        relWidth = 0.5f,
        relHeight = 0.5f,
        background = false,
        clickAction = false,
    )
}
