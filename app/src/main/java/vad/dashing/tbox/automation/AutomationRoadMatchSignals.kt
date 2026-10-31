package vad.dashing.tbox.automation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import vad.dashing.tbox.location.roadmatch.RoadHighwayClass
import vad.dashing.tbox.location.roadmatch.RoadMatchAnchorRepository
import vad.dashing.tbox.location.roadmatch.RoadMatchAnchorState

/**
 * APP automation signals backed by [RoadMatchAnchorRepository] (same snapshot as OSM
 * speed-limit tiles). Unavailable when there is no matched edge / no posted value.
 * Does not consult SpeedCam or SLA CAN.
 */
object AutomationRoadMatchSignals {
    val INTEREST_IDS: Set<AutomationSignalId> = setOf(
        AutomationSignalId.ROAD_HIGHWAY_CLASS,
        AutomationSignalId.ROAD_SPEED_LIMIT_KMH,
        AutomationSignalId.ROAD_TUNNEL,
    )

    /** Common OSM highway classes present in `.tboxroads` packs (picker options). */
    val HIGHWAY_CLASS_OPTIONS: List<String> = listOf(
        "motorway",
        "motorway_link",
        "trunk",
        "trunk_link",
        "primary",
        "primary_link",
        "secondary",
        "secondary_link",
        "tertiary",
        "tertiary_link",
        "unclassified",
        "residential",
        "living_street",
        "service",
        "track",
    )

    fun isRoadMatchSignal(id: AutomationSignalId): Boolean = id in INTEREST_IDS

    fun highwayClassValue(anchor: RoadMatchAnchorState): AutomationSignalValue {
        if (anchor.edgeId == null) return AutomationSignalValue.Unavailable
        val cls = anchor.highwayClass?.takeIf { it.isNotBlank() }
            ?: return AutomationSignalValue.Unavailable
        return AutomationSignalValue.State(RoadHighwayClass.normalize(cls))
    }

    fun speedLimitKmhValue(anchor: RoadMatchAnchorState): AutomationSignalValue {
        if (anchor.edgeId == null) return AutomationSignalValue.Unavailable
        val limit = anchor.currentLimitKmh?.takeIf { it > 0 }
            ?: return AutomationSignalValue.Unavailable
        return AutomationSignalValue.Number(limit.toDouble())
    }

    fun tunnelValue(anchor: RoadMatchAnchorState): AutomationSignalValue {
        if (anchor.edgeId == null) return AutomationSignalValue.Unavailable
        return AutomationSignalValue.State(if (anchor.tunnel == true) "on" else "off")
    }

    fun flowFor(signal: AutomationSignalId): Flow<AutomationSignalValue>? = when (signal) {
        AutomationSignalId.ROAD_HIGHWAY_CLASS ->
            RoadMatchAnchorRepository.state
                .map(::highwayClassValue)
                .distinctUntilChanged()
        AutomationSignalId.ROAD_SPEED_LIMIT_KMH ->
            RoadMatchAnchorRepository.state
                .map(::speedLimitKmhValue)
                .distinctUntilChanged()
        AutomationSignalId.ROAD_TUNNEL ->
            RoadMatchAnchorRepository.state
                .map(::tunnelValue)
                .distinctUntilChanged()
        else -> null
    }

    fun highwayClassOptionLabel(raw: String): String? = when (RoadHighwayClass.normalize(raw)) {
        "motorway" -> "Автомагистраль"
        "motorway_link" -> "Съезд с автомагистрали"
        "trunk" -> "Магистраль"
        "trunk_link" -> "Съезд с магистрали"
        "primary" -> "Главная дорога"
        "primary_link" -> "Съезд с главной"
        "secondary" -> "Второстепенная"
        "secondary_link" -> "Съезд со второстепенной"
        "tertiary" -> "Третьестепенная"
        "tertiary_link" -> "Съезд с третьестепенной"
        "unclassified" -> "Без классификации"
        "residential" -> "Жилая зона"
        "living_street" -> "Жилая улица"
        "service" -> "Служебный проезд"
        "track" -> "Просёлок"
        else -> null
    }
}
