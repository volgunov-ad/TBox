package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

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
        AutomationSignalId.ROAD_SETTLEMENT,
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

    fun settlementValue(anchor: RoadMatchAnchorState): AutomationSignalValue {
        if (anchor.edgeId == null) return AutomationSignalValue.Unavailable
        return AutomationSignalValue.State(if (anchor.settlement == true) "on" else "off")
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
        AutomationSignalId.ROAD_SETTLEMENT ->
            RoadMatchAnchorRepository.state
                .map(::settlementValue)
                .distinctUntilChanged()
        else -> null
    }

    fun highwayClassOptionLabel(raw: String): String? = when (RoadHighwayClass.normalize(raw)) {
        "motorway" -> ruEn("Автомагистраль", "Motorway")
        "motorway_link" -> ruEn("Съезд с автомагистрали", "Motorway link")
        "trunk" -> ruEn("Магистраль", "Trunk road")
        "trunk_link" -> ruEn("Съезд с магистрали", "Trunk link")
        "primary" -> ruEn("Главная дорога", "Primary road")
        "primary_link" -> ruEn("Съезд с главной", "Primary link")
        "secondary" -> ruEn("Второстепенная", "Secondary")
        "secondary_link" -> ruEn("Съезд со второстепенной", "Secondary link")
        "tertiary" -> ruEn("Третьестепенная", "Tertiary")
        "tertiary_link" -> ruEn("Съезд с третьестепенной", "Tertiary link")
        "unclassified" -> ruEn("Без классификации", "Unclassified")
        "residential" -> ruEn("Жилая зона", "Residential")
        "living_street" -> ruEn("Жилая улица", "Living street")
        "service" -> ruEn("Служебный проезд", "Service road")
        "track" -> ruEn("Просёлок", "Track")
        else -> null
    }
}
