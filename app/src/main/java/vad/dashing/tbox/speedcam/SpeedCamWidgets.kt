package vad.dashing.tbox.speedcam

import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainScreenPanelConfig

/** Nearest speed-camera / enforcement POI ahead of travel. */
const val SPEED_CAM_WIDGET_DATA_KEY = "speedCamWidget"

fun isSpeedCamWidgetDataKey(dataKey: String): Boolean =
    dataKey == SPEED_CAM_WIDGET_DATA_KEY

const val DEFAULT_SPEED_CAM_OVERAGE_KMH = 18
const val MIN_SPEED_CAM_OVERAGE_KMH = 0
const val MAX_SPEED_CAM_OVERAGE_KMH = 40

const val DEFAULT_SPEED_CAM_RADIUS_M = 500
const val MIN_SPEED_CAM_RADIUS_M = 300
const val MAX_SPEED_CAM_RADIUS_M = 1000

fun normalizeSpeedCamOverageKmh(raw: Int): Int =
    raw.coerceIn(MIN_SPEED_CAM_OVERAGE_KMH, MAX_SPEED_CAM_OVERAGE_KMH)

fun normalizeSpeedCamRadiusM(raw: Int): Int =
    raw.coerceIn(MIN_SPEED_CAM_RADIUS_M, MAX_SPEED_CAM_RADIUS_M)

/**
 * Aggregated demand from all SpeedCam tiles across panels.
 * [radiusM] is the max so map/search covers every tile; [overageKmh] is the min
 * (strictest) for the shared repository overLimit hint — tiles recompute locally.
 */
data class SpeedCamAggregateConfig(
    val radiusM: Int,
    val overageKmh: Int,
    val showOnMap: Boolean,
)

object SpeedCamWidgetPresence {
    fun isPresent(
        dashboardWidgets: List<FloatingDashboardWidgetConfig>,
        floatingPanels: List<FloatingDashboardConfig>,
        mainScreenPanels: List<MainScreenPanelConfig>,
    ): Boolean {
        if (dashboardWidgets.any { isSpeedCamWidgetDataKey(it.dataKey) }) return true
        if (floatingPanels.any { it.enabled && it.widgetsConfig.any { w -> isSpeedCamWidgetDataKey(w.dataKey) } }) {
            return true
        }
        if (mainScreenPanels.any { it.enabled && it.widgetsConfig.any { w -> isSpeedCamWidgetDataKey(w.dataKey) } }) {
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
        dashboardWidgets.filterTo(configs) { isSpeedCamWidgetDataKey(it.dataKey) }
        for (panel in floatingPanels) {
            if (!panel.enabled) continue
            panel.widgetsConfig.filterTo(configs) { isSpeedCamWidgetDataKey(it.dataKey) }
        }
        for (panel in mainScreenPanels) {
            if (!panel.enabled) continue
            panel.widgetsConfig.filterTo(configs) { isSpeedCamWidgetDataKey(it.dataKey) }
        }
        if (configs.isEmpty()) return null
        return SpeedCamAggregateConfig(
            radiusM = configs.maxOf { normalizeSpeedCamRadiusM(it.speedCamRadiusM) },
            overageKmh = configs.minOf { normalizeSpeedCamOverageKmh(it.speedCamOverageKmh) },
            showOnMap = configs.any { it.speedCamShowOnMap },
        )
    }
}
