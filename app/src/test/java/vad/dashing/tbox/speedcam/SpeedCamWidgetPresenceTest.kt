package vad.dashing.tbox.speedcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.DEFAULT_MAPS_CAM_LOOKAHEAD_M
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.OSM_SPEED_LIMIT_WIDGET_DATA_KEY
import vad.dashing.tbox.ROAD_MATCH_MAP_WIDGET_DATA_KEY

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
    fun isPresentWhenMapWantsShowOnMap() {
        assertTrue(
            SpeedCamWidgetPresence.isPresent(
                dashboardWidgets = listOf(
                    FloatingDashboardWidgetConfig(
                        dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY,
                        speedCamShowOnMap = true,
                    ),
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
                    mapsCamRadarHoldDistanceM = 700,
                ),
                FloatingDashboardWidgetConfig(
                    dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY,
                    speedCamShowOnMap = true,
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
    fun aggregateShowOnMapFalseWithoutMapToggle() {
        val agg = SpeedCamWidgetPresence.aggregate(
            dashboardWidgets = listOf(
                FloatingDashboardWidgetConfig(dataKey = OSM_SPEED_LIMIT_WIDGET_DATA_KEY),
                FloatingDashboardWidgetConfig(dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY),
            ),
            floatingPanels = emptyList(),
            mainScreenPanels = emptyList(),
        )
        assertNotNull(agg)
        assertFalse(agg!!.showOnMap)
    }

    @Test
    fun aggregateMapOnlyUsesDefaults() {
        val agg = SpeedCamWidgetPresence.aggregate(
            dashboardWidgets = listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY,
                    speedCamShowOnMap = true,
                ),
            ),
            floatingPanels = emptyList(),
            mainScreenPanels = emptyList(),
        )
        assertNotNull(agg)
        assertTrue(agg!!.showOnMap)
        assertEquals(DEFAULT_MAPS_CAM_LOOKAHEAD_M, agg.radiusM)
        assertEquals(DEFAULT_SPEED_CAM_OVERAGE_KMH, agg.overageKmh)
    }

    @Test
    fun aggregateNullWithoutOsmWidgetOrMapMarkers() {
        assertNull(
            SpeedCamWidgetPresence.aggregate(
                dashboardWidgets = listOf(
                    FloatingDashboardWidgetConfig(dataKey = SPEED_CAM_WIDGET_DATA_KEY),
                    FloatingDashboardWidgetConfig(dataKey = ROAD_MATCH_MAP_WIDGET_DATA_KEY),
                ),
                floatingPanels = emptyList(),
                mainScreenPanels = emptyList(),
            ),
        )
    }
}
