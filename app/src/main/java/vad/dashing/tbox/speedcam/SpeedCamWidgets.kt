package vad.dashing.tbox.speedcam

import vad.dashing.tbox.DEFAULT_MAPS_CAM_LOOKAHEAD_M
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.DEFAULT_MAPS_CAM_RADAR_HOLD_M
import vad.dashing.tbox.isOsmSpeedLimitWidgetDataKey
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
 * Aggregated demand from unified maps/cameras/radars tiles (`osmSpeedLimitWidget`).
 * [radiusM] is the max lookahead so search covers every tile; [overageKmh] is the min
 * (strictest); [radarHoldDistanceM] is the max hold for current-limit fallback.
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
        return false
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
        if (configs.isEmpty()) return null
        return SpeedCamAggregateConfig(
            radiusM = configs.maxOf { normalizeMapsCamLookaheadM(it.mapsCamLookaheadDistanceM) },
            overageKmh = configs.minOf { normalizeSpeedCamOverageKmh(it.speedCamOverageKmh) },
            showOnMap = configs.any { it.speedCamShowOnMap },
            radarHoldDistanceM = configs.maxOf { normalizeMapsCamRadarHoldM(it.mapsCamRadarHoldDistanceM) },
        )
    }
}
