package vad.dashing.tbox.location.roadmatch

/**
 * How road matching constrains the DR shadow when the Geoposition toggle is on.
 *
 * [ORDINARY] — current softCorrect / leash / free behaviour (default).
 * [RAILS] — pose stays on the graph corridor; gyro/steer pick forks;
 *   free particle can break off (yard, large gap, dead-end).
 */
enum class RoadMatchMode {
    ORDINARY,
    RAILS;

    companion object {
        fun fromStorage(raw: String?): RoadMatchMode {
            return when (raw?.trim()?.uppercase()) {
                "RAILS", "RAIL" -> RAILS
                else -> ORDINARY
            }
        }
    }
}
