package vad.dashing.tbox.location.roadmatch

/**
 * How road matching constrains the DR shadow when the Geoposition toggle is on.
 *
 * [ORDINARY] — current softCorrect / leash / free behaviour (default).
 * [RAILS] — pose stays on the graph corridor; an Ordinary navigator
 *   picks forks; free particle can break off (yard, large gap+heading).
 *   Dead-end with a small along-gap holds the last rail.
 * [FREE_TURNS] — Ordinary parameters, stronger heading pull toward the selected
 *   edge, and a full unbind 30 m before a 3+ line junction (any fork) until
 *   10 m after it.
 */
enum class RoadMatchMode {
    ORDINARY,
    RAILS,
    FREE_TURNS;

    companion object {
        fun fromStorage(raw: String?): RoadMatchMode {
            return when (raw?.trim()?.uppercase()) {
                "RAILS", "RAIL" -> RAILS
                "FREE_TURNS", "FREETURNS", "FREE" -> FREE_TURNS
                else -> ORDINARY
            }
        }
    }
}
