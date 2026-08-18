package vad.dashing.tbox.location.roadmatch

/**
 * Matcher profile: city (tight turns, yards) vs highway (gentle parallel exits).
 *
 * Detected from the sticky / candidate edge — not CAN speed — so braking before
 * an exit does not flicker the profile.
 */
enum class RoadMatchRoadProfile {
    CITY,
    HIGHWAY,
}

object RoadMatchRoadProfileMath {
    /** OSM maxspeed (km/h) treated as highway corridor. */
    const val HIGHWAY_MIN_MAXSPEED_KMH = 80

    /** Ticks of consistent classify before switching (0.5 s cycle → ~1.5–2.5 s). */
    const val HYSTERESIS_TICKS = 3

    fun classify(highwayClass: String?, maxspeedKmh: Int?): RoadMatchRoadProfile {
        if (highwayClass.isNullOrBlank()) return RoadMatchRoadProfile.CITY
        val c = RoadHighwayClass.normalize(highwayClass)
        // Slip roads inherit the corridor: motorway_link on a motorway approach.
        if (c.startsWith("motorway") || c.startsWith("trunk")) {
            return RoadMatchRoadProfile.HIGHWAY
        }
        if (RoadHighwayClass.isCourtyardLike(c)) return RoadMatchRoadProfile.CITY
        if (maxspeedKmh != null && maxspeedKmh >= HIGHWAY_MIN_MAXSPEED_KMH) {
            return RoadMatchRoadProfile.HIGHWAY
        }
        return RoadMatchRoadProfile.CITY
    }

    /**
     * Effective limit for classify: directed maxspeed if present, else plain.
     */
    fun effectiveMaxspeedKmh(edge: RoadEdge?, travelAgainstCoords: Boolean?): Int? {
        if (edge == null) return null
        return edge.speedLimitKmh(travelAgainstCoords == true)
    }
}
