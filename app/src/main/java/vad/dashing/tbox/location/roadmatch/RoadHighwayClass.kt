package vad.dashing.tbox.location.roadmatch

/**
 * OSM highway class costs for Phase E map-match scoring.
 * Footways/paths are filtered out at pack build time; remaining low-priority
 * classes (yards, living streets) are penalized rather than hard-excluded.
 */
object RoadHighwayClass {
    fun normalize(raw: String): String = raw.trim().lowercase()

    /** Additive score penalty (metres-equivalent). Lower is better. */
    fun scorePenalty(highwayClass: String): Double {
        val c = normalize(highwayClass)
        return when {
            c.startsWith("motorway") || c.startsWith("trunk") -> 0.0
            c.startsWith("primary") -> 0.0
            c.startsWith("secondary") -> 0.35
            c.startsWith("tertiary") -> 0.9
            c == "unclassified" -> 1.6
            c == "residential" || c == "living_street" -> 3.2
            c == "service" || c == "track" -> 12.0
            else -> 2.5
        }
    }

    /**
     * Extra penalty when dropping from a major road onto a yard-like class.
     */
    fun transitionPenalty(previousClass: String?, nextClass: String): Double {
        if (previousClass.isNullOrBlank()) return 0.0
        val prev = normalize(previousClass)
        val next = normalize(nextClass)
        val prevMajor = prev.startsWith("motorway") || prev.startsWith("trunk") ||
            prev.startsWith("primary") || prev.startsWith("secondary")
        val nextYard = next == "residential" || next == "living_street" ||
            next == "service" || next == "track"
        return if (prevMajor && nextYard) 4.0 else 0.0
    }

    fun isYardLike(highwayClass: String): Boolean {
        val c = normalize(highwayClass)
        return c == "residential" || c == "living_street" || c == "service" || c == "track"
    }
}
