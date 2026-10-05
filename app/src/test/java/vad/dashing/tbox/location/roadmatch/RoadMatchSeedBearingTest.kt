package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadMatchSeedBearingTest {

    private fun eastWestGraph(oneway: Int = 0): RoadGraph {
        val edge = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 1_000.0,
            fromNode = 0,
            toNode = 1,
            // eastbound along lat=55.75 from lon 37.60 → 37.62
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
            oneway = oneway,
        )
        return RoadGraph(
            regionId = "seed-brg",
            graphVersion = 1,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            edges = listOf(edge),
        )
    }

    @Test
    fun alignTwoWayPicksMinimalTurn() {
        assertEquals(
            90f,
            RoadMatchSeedBearing.alignToEdgeAzimuth(10f, 90f, oneway = 0),
            0.1f,
        )
        assertEquals(
            270f,
            RoadMatchSeedBearing.alignToEdgeAzimuth(200f, 90f, oneway = 0),
            0.1f,
        )
    }

    @Test
    fun alignOnewayForcesLegalDirection() {
        // Eastbound only — even if current heading is west.
        assertEquals(
            90f,
            RoadMatchSeedBearing.alignToEdgeAzimuth(270f, 90f, oneway = 1),
            0.1f,
        )
        // Westbound only (against coords).
        assertEquals(
            270f,
            RoadMatchSeedBearing.alignToEdgeAzimuth(90f, 90f, oneway = -1),
            0.1f,
        )
    }

    @Test
    fun snapNearTwoWayEdgeMinimalTurn() {
        val graphs = listOf(eastWestGraph(oneway = 0))
        // ~0 m north of the line at lon 37.61
        val east = RoadMatchSeedBearing.snapTravelBearingDeg(
            lat = 55.75,
            lon = 37.61,
            currentBearingDeg = 20f,
            graphs = graphs,
        )
        assertEquals(90f, east!!, 1f)

        val west = RoadMatchSeedBearing.snapTravelBearingDeg(
            lat = 55.75,
            lon = 37.61,
            currentBearingDeg = 200f,
            graphs = graphs,
        )
        assertEquals(270f, west!!, 1f)
    }

    @Test
    fun snapNearOnewayIgnoresAgainstFlow() {
        val graphs = listOf(eastWestGraph(oneway = 1))
        val snapped = RoadMatchSeedBearing.snapTravelBearingDeg(
            lat = 55.75,
            lon = 37.61,
            currentBearingDeg = 250f,
            graphs = graphs,
        )
        assertEquals(90f, snapped!!, 1f)
    }

    @Test
    fun snapReturnsNullWhenFartherThan30m() {
        val graphs = listOf(eastWestGraph())
        // ~55 m north of the east-west line
        val far = RoadMatchSeedBearing.snapTravelBearingDeg(
            lat = 55.7505,
            lon = 37.61,
            currentBearingDeg = 90f,
            graphs = graphs,
            maxDistanceM = RoadMatchSeedBearing.MAX_SNAP_DISTANCE_M,
        )
        assertNull(far)
        val kept = RoadMatchSeedBearing.snapOrKeep(
            lat = 55.7505,
            lon = 37.61,
            currentBearingDeg = 42f,
            graphs = graphs,
        )
        assertEquals(42f, kept, 0.01f)
    }

    @Test
    fun snapReturnsNullWithoutGraphs() {
        assertNull(
            RoadMatchSeedBearing.snapTravelBearingDeg(
                lat = 55.75,
                lon = 37.61,
                currentBearingDeg = 90f,
                graphs = emptyList(),
            ),
        )
    }

    @Test
    fun snapWithin30mDoesAlign() {
        val graphs = listOf(eastWestGraph())
        // ~11 m north
        val snapped = RoadMatchSeedBearing.snapTravelBearingDeg(
            lat = 55.7501,
            lon = 37.61,
            currentBearingDeg = 0f,
            graphs = graphs,
        )
        assertEquals(90f, snapped!!, 1f)
        val proj = RoadMapMatcher.projectOntoEdge(55.7501, 37.61, graphs[0].edges[0])!!
        assertTrue(proj.crossTrackM < RoadMatchSeedBearing.MAX_SNAP_DISTANCE_M)
    }
}
