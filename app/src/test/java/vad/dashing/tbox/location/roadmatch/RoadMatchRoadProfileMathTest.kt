package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadMatchRoadProfileMathTest {

    @Test
    fun motorwayAndTrunkAreHighway() {
        assertEquals(
            RoadMatchRoadProfile.HIGHWAY,
            RoadMatchRoadProfileMath.classify("motorway", null),
        )
        assertEquals(
            RoadMatchRoadProfile.HIGHWAY,
            RoadMatchRoadProfileMath.classify("trunk_link", null),
        )
    }

    @Test
    fun highMaxspeedMakesHighway() {
        assertEquals(
            RoadMatchRoadProfile.HIGHWAY,
            RoadMatchRoadProfileMath.classify("primary", 90),
        )
        assertEquals(
            RoadMatchRoadProfile.CITY,
            RoadMatchRoadProfileMath.classify("primary", 60),
        )
    }

    @Test
    fun residentialIsCity() {
        assertEquals(
            RoadMatchRoadProfile.CITY,
            RoadMatchRoadProfileMath.classify("residential", 80),
        )
    }

    @Test
    fun highwayIntentForkBiasIsStrongerThanCity() {
        fun cand(id: Long, azimuth: Float, score: Double) =
            RoadMapMatcher.Candidate(
                edge = RoadEdge(id, "motorway_link", 80.0, id.toInt(), id.toInt() + 1, doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
                regionId = "r",
                crossTrackM = 0.0,
                alongTrackM = 10.0,
                projLat = 0.0,
                projLon = 0.0,
                edgeAzimuthDeg = azimuth,
                score = score,
                connectedFromPrevious = true,
            )
        // Shallow 15° exit — below city 25° threshold, above highway-intent 12°.
        val ranked = listOf(cand(1L, 90f, 2.0), cand(2L, 105f, 6.0))
        val noIntent = RoadMapMatcher.applyTurnSignalForkBias(
            ranked = ranked,
            travelBearingDeg = 90f,
            hint = RoadMapMatcher.TurnHint.Right,
            previousEdgeId = 1L,
            previousRegionId = "r",
            turnIntent = false,
            roadProfile = RoadMatchRoadProfile.HIGHWAY,
        )
        assertEquals(ranked.map { it.score }, noIntent.map { it.score })

        val withIntent = RoadMapMatcher.applyTurnSignalForkBias(
            ranked = ranked,
            travelBearingDeg = 90f,
            hint = RoadMapMatcher.TurnHint.Right,
            previousEdgeId = 1L,
            previousRegionId = "r",
            turnIntent = true,
            roadProfile = RoadMatchRoadProfile.HIGHWAY,
        )
        val exit = withIntent.first { it.edge.id == 2L }
        assertEquals(6.0 + RoadMapMatcher.TURN_SIGNAL_HIGHWAY_INTENT_TOWARD_BONUS, exit.score, 1e-6)
        assertTrue(
            RoadMapMatcher.isTurnSignalToward(
                90f, 105f, RoadMapMatcher.TurnHint.Right,
                RoadMapMatcher.TURN_SIGNAL_HIGHWAY_INTENT_TOWARD_MIN_DEG,
            ),
        )
        assertFalse(
            RoadMapMatcher.isTurnSignalToward(
                90f, 105f, RoadMapMatcher.TurnHint.Right,
                RoadMapMatcher.TURN_SIGNAL_TOWARD_MIN_DEG,
            ),
        )
    }
}
