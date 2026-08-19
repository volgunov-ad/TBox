package vad.dashing.tbox.location.roadmatch

import vad.dashing.tbox.location.ConstantDrMath

/**
 * When live GNSS is trustworthy, road-match ranking should lean more on
 * cross-track distance (nearest road) and less on OSM highway-class bias —
 * e.g. a frontage/doubler next to a motorway.
 *
 * Trust is 0 while retaining / without a usable live fix, so tunnel DR keeps
 * the existing class preference.
 */
object RoadMatchGnssTrust {
    /** Live horizontal accuracy above this → no class-penalty relaxation. */
    const val MAX_ACCURACY_M = 12f

    /**
     * If shadow and GNSS disagree by more than this, do not relax class
     * penalties (avoid ranking on a jumped fix).
     */
    const val MAX_SHADOW_GAP_M = 20.0

    /** Fraction of class/transition penalty removed at trust = 1. */
    const val CLASS_PENALTY_RELAX = 0.85

    /**
     * @param liveGnss true when a live GNSS point is driving the matcher input
     * @param accuracyM live horizontal accuracy (m); null / invalid → 0
     * @param shadowGnssGapM optional distance shadow↔GNSS; when set and large → 0
     */
    fun fromLive(
        liveGnss: Boolean,
        accuracyM: Float?,
        shadowGnssGapM: Double? = null,
        maxAccuracyM: Float = MAX_ACCURACY_M,
        maxShadowGapM: Double = MAX_SHADOW_GAP_M,
    ): Float {
        if (!liveGnss) return 0f
        val acc = accuracyM?.takeIf { it.isFinite() && it > 0f } ?: return 0f
        if (acc > maxAccuracyM) return 0f
        if (shadowGnssGapM != null &&
            shadowGnssGapM.isFinite() &&
            shadowGnssGapM > maxShadowGapM
        ) {
            return 0f
        }
        return ConstantDrMath.confidenceFromAccuracyM(acc).coerceIn(0f, 1f)
    }

    /** Multiplier for [RoadHighwayClass.scorePenalty] / [RoadHighwayClass.transitionPenalty]. */
    fun classPenaltyScale(
        gnssPositionTrust: Float,
        relax: Double = CLASS_PENALTY_RELAX,
    ): Double {
        val t = gnssPositionTrust.coerceIn(0f, 1f).toDouble()
        return 1.0 - relax.coerceIn(0.0, 1.0) * t
    }
}
