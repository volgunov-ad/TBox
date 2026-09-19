package vad.dashing.tbox.speedcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainScreenPanelConfig

class SpeedCamWidgetPresenceTest {
    @Test
    fun aggregateNullWhenAbsent() {
        assertNull(
            SpeedCamWidgetPresence.aggregate(
                dashboardWidgets = emptyList(),
                floatingPanels = emptyList(),
                mainScreenPanels = emptyList(),
            ),
        )
        assertFalse(
            SpeedCamWidgetPresence.isPresent(emptyList(), emptyList(), emptyList()),
        )
    }

    @Test
    fun aggregateMaxRadiusMinOverageAnyMap() {
        val dash = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = SPEED_CAM_WIDGET_DATA_KEY,
                speedCamOverageKmh = 18,
                speedCamRadiusM = 400,
                speedCamShowOnMap = false,
            ),
        )
        val floating = listOf(
            floatingPanel(
                enabled = true,
                widgets = listOf(
                    FloatingDashboardWidgetConfig(
                        dataKey = SPEED_CAM_WIDGET_DATA_KEY,
                        speedCamOverageKmh = 10,
                        speedCamRadiusM = 900,
                        speedCamShowOnMap = true,
                    ),
                ),
            ),
        )
        val agg = SpeedCamWidgetPresence.aggregate(dash, floating, emptyList())
        assertNotNull(agg)
        assertEquals(900, agg!!.radiusM)
        assertEquals(10, agg.overageKmh)
        assertTrue(agg.showOnMap)
        assertTrue(SpeedCamWidgetPresence.isPresent(dash, floating, emptyList()))
    }

    @Test
    fun ignoresDisabledPanels() {
        val panels = listOf(
            mainPanel(
                enabled = false,
                widgets = listOf(
                    FloatingDashboardWidgetConfig(dataKey = SPEED_CAM_WIDGET_DATA_KEY),
                ),
            ),
        )
        assertNull(SpeedCamWidgetPresence.aggregate(emptyList(), emptyList(), panels))
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
