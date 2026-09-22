package vad.dashing.tbox.speedcam

import vad.dashing.tbox.DEFAULT_MAPS_CAM_LOOKAHEAD_M
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.DEFAULT_MAPS_CAM_RADAR_HOLD_M
import vad.dashing.tbox.isOsmSpeedLimitWidgetDataKey
import vad.dashing.tbox.isRoadMatchMapWidgetDataKey
import vad.dashing.tbox.normalizeMapsCamLookaheadM
import vad.dashing.tbox.normalizeMapsCamRadarHoldM

/**
 * Legacy key for the removed standalone SpeedCam tile.
 * Kept for [vad.dashing.tbox.REMOVED_WIDGET_DATA_KEYS] / old JSON; not offered in the picker.
 */
const val SPEED_CAM_WIDGET_DATA_KEY = "speedCamWidget"

fun isSpeedCamWidgetDataKey(dataKey: String): Boolean =
    dataKey == SPEED_CAM_WIDGET_DATA_KEY

const val DEFAULT_SPEED_CAM_OVERAGE_KMH = 18
const val MIN_SPEED_CAM_OVERAGE_KMH = 0
const val MAX_SPEED_CAM_OVERAGE_KMH = 40

/** @deprecated Prefer [DEFAULT_MAPS_CAM_LOOKAHEAD_M]; kept for codec compatibility. */
const val DEFAULT_SPEED_CAM_RADIUS_M = DEFAULT_MAPS_CAM_LOOKAHEAD_M
const val MIN_SPEED_CAM_RADIUS_M = 300
const val MAX_SPEED_CAM_RADIUS_M = 3000

fun normalizeSpeedCamOverageKmh(raw: Int): Int =
    raw.coerceIn(MIN_SPEED_CAM_OVERAGE_KMH, MAX_SPEED_CAM_OVERAGE_KMH)

fun normalizeSpeedCamRadiusM(raw: Int): Int =
    normalizeMapsCamLookaheadM(raw)

/**
 * Aggregated SpeedCam demand from dashboard tiles.
 *
 * Lookahead / overage / radar-hold come from `osmSpeedLimitWidget` tiles.
 * [showOnMap] comes from `roadMatchMapWidget` tiles (`speedCamShowOnMap`).
 * Map-only demand (markers without an OSM limit tile) still keeps the ticker alive
 * with default radius/overage so nearby points can be published.
 */
data class SpeedCamAggregateConfig(
    val radiusM: Int,
    val overageKmh: Int,
    val showOnMap: Boolean,
    val radarHoldDistanceM: Int = DEFAULT_MAPS_CAM_RADAR_HOLD_M,
)

object SpeedCamWidgetPresence {
    fun isPresent(
        dashboardWidgets: List<FloatingDashboardWidgetConfig>,
        floatingPanels: List<FloatingDashboardConfig>,
        mainScreenPanels: List<MainScreenPanelConfig>,
    ): Boolean {
        if (dashboardWidgets.any { isOsmSpeedLimitWidgetDataKey(it.dataKey) }) return true
        if (floatingPanels.any { it.enabled && it.widgetsConfig.any { w -> isOsmSpeedLimitWidgetDataKey(w.dataKey) } }) {
            return true
        }
        if (mainScreenPanels.any { it.enabled && it.widgetsConfig.any { w -> isOsmSpeedLimitWidgetDataKey(w.dataKey) } }) {
            return true
        }
        return anyMapWantsShowOnMap(dashboardWidgets, floatingPanels, mainScreenPanels)
    }

    fun aggregate(
        dashboardWidgets: List<FloatingDashboardWidgetConfig>,
        floatingPanels: List<FloatingDashboardConfig>,
        mainScreenPanels: List<MainScreenPanelConfig>,
    ): SpeedCamAggregateConfig? {
        val configs = ArrayList<FloatingDashboardWidgetConfig>(4)
        dashboardWidgets.filterTo(configs) { isOsmSpeedLimitWidgetDataKey(it.dataKey) }
        for (panel in floatingPanels) {
            if (!panel.enabled) continue
            panel.widgetsConfig.filterTo(configs) { isOsmSpeedLimitWidgetDataKey(it.dataKey) }
        }
        for (panel in mainScreenPanels) {
            if (!panel.enabled) continue
            panel.widgetsConfig.filterTo(configs) { isOsmSpeedLimitWidgetDataKey(it.dataKey) }
        }
        val showOnMap = anyMapWantsShowOnMap(dashboardWidgets, floatingPanels, mainScreenPanels)
        if (configs.isEmpty()) {
            if (!showOnMap) return null
            return SpeedCamAggregateConfig(
                radiusM = DEFAULT_MAPS_CAM_LOOKAHEAD_M,
                overageKmh = DEFAULT_SPEED_CAM_OVERAGE_KMH,
                showOnMap = true,
                radarHoldDistanceM = DEFAULT_MAPS_CAM_RADAR_HOLD_M,
            )
        }
        return SpeedCamAggregateConfig(
            radiusM = configs.maxOf { normalizeMapsCamLookaheadM(it.mapsCamLookaheadDistanceM) },
            overageKmh = configs.minOf { normalizeSpeedCamOverageKmh(it.speedCamOverageKmh) },
            showOnMap = showOnMap,
            radarHoldDistanceM = configs.maxOf { normalizeMapsCamRadarHoldM(it.mapsCamRadarHoldDistanceM) },
        )
    }

    private fun anyMapWantsShowOnMap(
        dashboardWidgets: List<FloatingDashboardWidgetConfig>,
        floatingPanels: List<FloatingDashboardConfig>,
        mainScreenPanels: List<MainScreenPanelConfig>,
    ): Boolean {
        if (dashboardWidgets.any { isRoadMatchMapWidgetDataKey(it.dataKey) && it.speedCamShowOnMap }) {
            return true
        }
        if (floatingPanels.any { panel ->
                panel.enabled && panel.widgetsConfig.any { w ->
                    isRoadMatchMapWidgetDataKey(w.dataKey) && w.speedCamShowOnMap
                }
            }
        ) {
            return true
        }
        return mainScreenPanels.any { panel ->
            panel.enabled && panel.widgetsConfig.any { w ->
                isRoadMatchMapWidgetDataKey(w.dataKey) && w.speedCamShowOnMap
            }
        }
    }
}
