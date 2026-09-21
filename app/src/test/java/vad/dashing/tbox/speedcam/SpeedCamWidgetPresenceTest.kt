package vad.dashing.tbox.speedcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.OSM_SPEED_LIMIT_WIDGET_DATA_KEY

class SpeedCamWidgetPresenceTest {

    @Test
    fun isPresentWhenOsmWidgetOnDashboard() {
        assertTrue(
            SpeedCamWidgetPresence.isPresent(
                dashboardWidgets = listOf(
                    FloatingDashboardWidgetConfig(dataKey = OSM_SPEED_LIMIT_WIDGET_DATA_KEY),
                ),
                floatingPanels = emptyList(),
                mainScreenPanels = emptyList(),
            ),
        )
    }

    @Test
    fun aggregateUsesMaxLookaheadAndMinOverage() {
        val agg = SpeedCamWidgetPresence.aggregate(
            dashboardWidgets = listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = OSM_SPEED_LIMIT_WIDGET_DATA_KEY,
                    mapsCamLookaheadDistanceM = 400,
                    speedCamOverageKmh = 20,
                    mapsCamRadarHoldDistanceM = 300,
                ),
                FloatingDashboardWidgetConfig(
                    dataKey = OSM_SPEED_LIMIT_WIDGET_DATA_KEY,
                    mapsCamLookaheadDistanceM = 900,
                    speedCamOverageKmh = 10,
                    speedCamShowOnMap = true,
                    mapsCamRadarHoldDistanceM = 700,
                ),
            ),
            floatingPanels = emptyList(),
            mainScreenPanels = emptyList(),
        )
        assertNotNull(agg)
        assertEquals(900, agg!!.radiusM)
        assertEquals(10, agg.overageKmh)
        assertTrue(agg.showOnMap)
        assertEquals(700, agg.radarHoldDistanceM)
    }

    @Test
    fun aggregateNullWithoutOsmWidget() {
        assertNull(
            SpeedCamWidgetPresence.aggregate(
                dashboardWidgets = listOf(
                    FloatingDashboardWidgetConfig(dataKey = SPEED_CAM_WIDGET_DATA_KEY),
                ),
                floatingPanels = emptyList(),
                mainScreenPanels = emptyList(),
            ),
        )
    }
}
