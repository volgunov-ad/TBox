package vad.dashing.tbox.obd

import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.isObdMetricWidgetDataKey

/**
 * Collect Mode 01 / ATRV PID ids currently shown on a dashboard surface.
 */
fun collectObdPidIdsFromConfigs(configs: List<FloatingDashboardWidgetConfig>): Set<String> =
    configs.asSequence()
        .filter { isObdMetricWidgetDataKey(it.dataKey) }
        .map { ObdPid.normalizeId(it.obdPidId) }
        .toSet()

fun publishObdInterest(sourceId: String, configs: List<FloatingDashboardWidgetConfig>) {
    ObdInterestAggregator.setSourcePids(sourceId, collectObdPidIdsFromConfigs(configs))
}

fun clearObdInterest(sourceId: String) {
    ObdInterestAggregator.clearSource(sourceId)
}
