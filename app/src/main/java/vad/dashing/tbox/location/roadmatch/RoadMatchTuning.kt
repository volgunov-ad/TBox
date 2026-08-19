package vad.dashing.tbox.location.roadmatch

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.round
import vad.dashing.tbox.location.roadmatch.RoadMatchTuningGroup.COMMON
import vad.dashing.tbox.location.roadmatch.RoadMatchTuningGroup.FREE_TURNS
import vad.dashing.tbox.location.roadmatch.RoadMatchTuningGroup.ORDINARY
import vad.dashing.tbox.location.roadmatch.RoadMatchTuningGroup.RAILS

enum class RoadMatchTuningGroup {
    COMMON,
    ORDINARY,
    RAILS,
    FREE_TURNS,
}

/**
 * User-adjustable road matcher values. Only deviations from production defaults are persisted,
 * so "Reset" always follows the current constants shipped by the app.
 */
enum class RoadMatchTuningKey(
    val group: RoadMatchTuningGroup,
    val storageName: String,
    val defaultValue: Double,
    val minValue: Double,
    val maxValue: Double,
    val step: Double,
    val unit: String = "",
    val integer: Boolean = false,
) {
    MATCH_CADENCE_MS(COMMON, "matchCadenceMs", 500.0, 200.0, 2_000.0, 50.0, "ms", true),
    PATH_TRIGGER_M(COMMON, "pathTriggerM", 12.0, 4.0, 30.0, 1.0, "m"),
    TIME_TRIGGER_MS(COMMON, "timeTriggerMs", 2_000.0, 500.0, 5_000.0, 100.0, "ms", true),
    TURN_TRIGGER_DEG(COMMON, "turnTriggerDeg", 18.0, 8.0, 40.0, 1.0, "°"),
    MIN_SPEED_KMH(COMMON, "minSpeedKmh", 1.8, 0.0, 10.0, 0.5, "km/h"),
    CANDIDATE_RADIUS_M(COMMON, "candidateRadiusM", 35.0, 15.0, 60.0, 1.0, "m"),
    HEADING_TOLERANCE_DEG(COMMON, "headingToleranceDeg", 65.0, 40.0, 90.0, 1.0, "°"),
    CROSS_BLEND(COMMON, "crossBlend", 0.40, 0.10, 0.80, 0.05),
    MAX_CROSS_STEP_M(COMMON, "maxCrossStepM", 2.5, 0.5, 8.0, 0.5, "m"),
    MAX_BEARING_STEP_DEG(COMMON, "maxBearingStepDeg", 6.0, 2.0, 15.0, 1.0, "°"),
    MAX_BEARING_CATCHUP_DEG(COMMON, "maxBearingCatchupDeg", 14.0, 6.0, 30.0, 1.0, "°"),
    BEARING_INHIBIT_DEG(COMMON, "bearingInhibitDeg", 28.0, 15.0, 60.0, 1.0, "°"),
    HOLD_PREVIOUS_RADIUS_M(COMMON, "holdPreviousRadiusM", 24.0, 10.0, 40.0, 1.0, "m"),
    SWITCH_CONFIRM_COUNT(COMMON, "switchConfirmCount", 3.0, 1.0, 6.0, 1.0, "", true),
    BEAM_WIDTH(COMMON, "beamWidth", 5.0, 3.0, 8.0, 1.0, "", true),
    MATCH_LAG_MIN_M(COMMON, "matchLagMinM", 10.0, 0.0, 30.0, 1.0, "m"),
    MATCH_LAG_MAX_M(COMMON, "matchLagMaxM", 30.0, 10.0, 50.0, 1.0, "m"),
    MATCH_LAG_SECONDS(COMMON, "matchLagSeconds", 1.0, 0.0, 3.0, 0.1, "s"),
    LOOK_AHEAD_MIN_M(COMMON, "lookAheadMinM", 10.0, 3.0, 25.0, 1.0, "m"),
    LOOK_AHEAD_MAX_M(COMMON, "lookAheadMaxM", 20.0, 10.0, 50.0, 1.0, "m"),
    LOOK_AHEAD_SECONDS(COMMON, "lookAheadSeconds", 1.5, 0.5, 4.0, 0.1, "s"),
    GNSS_MAX_ACCURACY_M(COMMON, "gnssMaxAccuracyM", 12.0, 3.0, 30.0, 1.0, "m"),
    GNSS_MAX_SHADOW_GAP_M(COMMON, "gnssMaxShadowGapM", 20.0, 5.0, 60.0, 1.0, "m"),
    GNSS_CLASS_PENALTY_RELAX(COMMON, "gnssClassPenaltyRelax", 0.85, 0.0, 1.0, 0.05),

    LEASH_BREAK_XT_M(ORDINARY, "leashBreakXtM", 18.0, 8.0, 35.0, 1.0, "m"),
    LEASH_BREAK_YARD_XT_M(ORDINARY, "leashBreakYardXtM", 15.0, 8.0, 30.0, 1.0, "m"),
    LEASH_BREAK_PATH_M(ORDINARY, "leashBreakPathM", 8.0, 3.0, 20.0, 1.0, "m"),
    JUNCTION_RADIUS_M(ORDINARY, "junctionRadiusM", 100.0, 40.0, 150.0, 5.0, "m"),
    JUNCTION_MIN_ROADS(ORDINARY, "junctionMinRoads", 3.0, 2.0, 5.0, 1.0, "", true),
    PROMOTE_POS_M(ORDINARY, "promotePosM", 15.0, 5.0, 30.0, 1.0, "m"),
    PROMOTE_POS_HEADING_M(ORDINARY, "promotePosHeadingM", 8.0, 3.0, 20.0, 1.0, "m"),
    PROMOTE_HEADING_DEG(ORDINARY, "promoteHeadingDeg", 30.0, 15.0, 60.0, 1.0, "°"),
    MAX_ALONG_STEP_M(ORDINARY, "maxAlongStepM", 2.0, 0.0, 6.0, 0.5, "m"),
    PAST_END_RELEASE_M(ORDINARY, "pastEndReleaseM", 8.0, 3.0, 20.0, 1.0, "m"),

    RAILS_HARD_SNAP_XT_M(RAILS, "railsHardSnapXtM", 10.0, 3.0, 20.0, 1.0, "m"),
    RAILS_SOFT_XT_M(RAILS, "railsSoftXtM", 25.0, 10.0, 40.0, 1.0, "m"),
    RAILS_SOFT_BLEND(RAILS, "railsSoftBlend", 0.45, 0.20, 0.80, 0.05),
    RAILS_SOFT_MAX_STEP_M(RAILS, "railsSoftMaxStepM", 5.0, 1.0, 10.0, 0.5, "m"),
    RAILS_BREAK_XT_M(RAILS, "railsBreakXtM", 40.0, 15.0, 60.0, 1.0, "m"),
    RAILS_BREAK_YARD_XT_M(RAILS, "railsBreakYardXtM", 22.0, 10.0, 35.0, 1.0, "m"),
    RAILS_RELOCK_RADIUS_M(RAILS, "railsRelockRadiusM", 80.0, 30.0, 120.0, 5.0, "m"),
    RAILS_RELOCK_HEADING_DEG(RAILS, "railsRelockHeadingDeg", 20.0, 10.0, 40.0, 1.0, "°"),
    RAILS_MIN_ADVANCE_M(RAILS, "railsMinAdvanceM", 0.4, 0.1, 2.0, 0.1, "m"),
    RAILS_ALONG_LEASH_XT_M(RAILS, "railsAlongLeashXtM", 18.0, 8.0, 30.0, 1.0, "m"),
    RAILS_ALONG_LEASH_DEAD_M(RAILS, "railsAlongLeashDeadM", 6.0, 2.0, 12.0, 1.0, "m"),
    RAILS_ALONG_LEASH_GAIN(RAILS, "railsAlongLeashGain", 0.5, 0.2, 1.0, 0.05),
    RAILS_ALONG_LEASH_MAX_PULL_M(RAILS, "railsAlongLeashMaxPullM", 8.0, 2.0, 15.0, 1.0, "m"),
    RAILS_NAV_PATH_FACTOR(RAILS, "railsNavPathFactor", 1.25, 1.0, 2.0, 0.05),
    RAILS_NAV_PATH_SLACK_M(RAILS, "railsNavPathSlackM", 5.0, 0.0, 15.0, 1.0, "m"),
    RAILS_TURN_HINT_BIAS_DEG(RAILS, "railsTurnHintBiasDeg", 35.0, 15.0, 60.0, 1.0, "°"),
    RAILS_HIGHWAY_INTENT_BIAS_DEG(RAILS, "railsHighwayIntentBiasDeg", 55.0, 30.0, 80.0, 1.0, "°"),

    FREE_UNBIND_BEFORE_M(FREE_TURNS, "freeUnbindBeforeM", 35.0, 15.0, 60.0, 1.0, "m"),
    FREE_REBIND_AFTER_M(FREE_TURNS, "freeRebindAfterM", 10.0, 5.0, 30.0, 1.0, "m"),
    FREE_MIN_INCIDENT_LINES(FREE_TURNS, "freeMinIncidentLines", 3.0, 2.0, 5.0, 1.0, "", true),
    FREE_BEARING_CATCHUP_DEG(FREE_TURNS, "freeBearingCatchupDeg", 26.0, 14.0, 40.0, 1.0, "°"),
    FREE_THROTTLE_BEARING_DEG(FREE_TURNS, "freeThrottleBearingDeg", 18.0, 8.0, 30.0, 1.0, "°"),
    FREE_THROTTLE_MAX_RESIDUAL_DEG(FREE_TURNS, "freeThrottleMaxResidualDeg", 60.0, 30.0, 120.0, 5.0, "°");

    fun normalize(value: Double): Double {
        if (!value.isFinite()) return defaultValue
        val clamped = value.coerceIn(minValue, maxValue)
        val stepped = minValue + round((clamped - minValue) / step) * step
        return stepped.coerceIn(minValue, maxValue)
    }

    companion object {
        private val byStorageName = entries.associateBy { it.storageName }
        fun fromStorageName(name: String): RoadMatchTuningKey? = byStorageName[name]
    }
}

data class RoadMatchTuning(
    private val overrides: Map<RoadMatchTuningKey, Double> = emptyMap(),
) {
    operator fun get(key: RoadMatchTuningKey): Double = overrides[key] ?: key.defaultValue
    fun float(key: RoadMatchTuningKey): Float = get(key).toFloat()
    fun int(key: RoadMatchTuningKey): Int = get(key).toInt()
    fun long(key: RoadMatchTuningKey): Long = get(key).toLong()

    fun with(key: RoadMatchTuningKey, value: Double): RoadMatchTuning {
        val normalized = key.normalize(value)
        val changed = overrides.toMutableMap()
        if (abs(normalized - key.defaultValue) < 1e-9) changed.remove(key)
        else changed[key] = normalized
        return RoadMatchTuning(changed)
    }

    fun reset(group: RoadMatchTuningGroup? = null): RoadMatchTuning =
        if (group == null) DEFAULT
        else RoadMatchTuning(overrides.filterKeys { it.group != group })

    fun isDefault(group: RoadMatchTuningGroup? = null): Boolean =
        if (group == null) overrides.isEmpty() else overrides.keys.none { it.group == group }

    fun toJson(): String {
        val root = JSONObject().put("version", FORMAT_VERSION)
        overrides.toSortedMap(compareBy { it.storageName }).forEach { (key, value) ->
            root.put(key.storageName, value)
        }
        return root.toString()
    }

    companion object {
        const val FORMAT_VERSION = 1
        val DEFAULT = RoadMatchTuning()

        fun fromJson(raw: String?): RoadMatchTuning {
            if (raw.isNullOrBlank()) return DEFAULT
            return runCatching {
                val root = JSONObject(raw)
                var result = DEFAULT
                val names = root.keys()
                while (names.hasNext()) {
                    val name = names.next()
                    val key = RoadMatchTuningKey.fromStorageName(name) ?: continue
                    result = result.with(key, root.optDouble(name, key.defaultValue))
                }
                result
            }.getOrDefault(DEFAULT)
        }
    }
}
