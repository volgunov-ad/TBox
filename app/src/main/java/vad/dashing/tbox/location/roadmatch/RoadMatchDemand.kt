package vad.dashing.tbox.location.roadmatch

import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.isOsmSpeedLimitWidgetDataKey
import vad.dashing.tbox.location.MockCanSpeedMode
import vad.dashing.tbox.location.MockPowerState

/**
 * Who wants the shared [RoadMatchRuntime]: pose correction (Geoposition toggle) and/or
 * the OSM speed-limit widget. Pose is only nudged when [correctPose] is true.
 * [mode] selects Ordinary softCorrect vs Rails; default Ordinary keeps legacy behaviour.
 */
data class RoadMatchDemand(
    val matchNeeded: Boolean,
    val correctPose: Boolean,
    val mode: RoadMatchMode = RoadMatchMode.ORDINARY,
) {
    companion object {
        val NONE = RoadMatchDemand(
            matchNeeded = false,
            correctPose = false,
            mode = RoadMatchMode.ORDINARY,
        )

        fun resolve(
            toggleOn: Boolean,
            power: MockPowerState,
            canMode: MockCanSpeedMode,
            widgetPresent: Boolean,
            mode: RoadMatchMode = RoadMatchMode.ORDINARY,
        ): RoadMatchDemand {
            val correctPose = toggleOn && RoadMatchAvailability.isToggleEnabled(power, canMode)
            return RoadMatchDemand(
                matchNeeded = correctPose || widgetPresent,
                correctPose = correctPose,
                mode = mode,
            )
        }
    }
}

object RoadMatchWidgetPresence {
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
}
