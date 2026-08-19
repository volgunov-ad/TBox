package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class RoadMatchFreeTurnsMathTest {

    @Test
    fun remainingAlong_forwardAndReverse() {
        assertEquals(25.0, RoadMatchFreeTurnsMath.remainingAlongM(75.0, 100.0, false), 1e-9)
        assertEquals(75.0, RoadMatchFreeTurnsMath.remainingAlongM(75.0, 100.0, true), 1e-9)
        assertEquals(0.0, RoadMatchFreeTurnsMath.remainingAlongM(100.0, 100.0, false), 1e-9)
    }

    @Test
    fun shouldRelease_onlyInside30m() {
        assertFalse(RoadMatchFreeTurnsMath.shouldRelease(null))
        assertFalse(RoadMatchFreeTurnsMath.shouldRelease(30.01))
        assertTrue(RoadMatchFreeTurnsMath.shouldRelease(30.0))
        assertTrue(RoadMatchFreeTurnsMath.shouldRelease(5.0))
    }

    @Test
    fun shouldRebind_afterRemainingPlus10m() {
        assertFalse(RoadMatchFreeTurnsMath.shouldRebind(34.0, 25.0))
        assertTrue(RoadMatchFreeTurnsMath.shouldRebind(35.0, 25.0))
        assertTrue(RoadMatchFreeTurnsMath.shouldRebind(10.0, 0.0))
        assertFalse(RoadMatchFreeTurnsMath.shouldRebind(9.0, 0.0))
    }

    @Test
    fun crossHasFourIncidentLines_tJunctionHasThree() {
        val cross = fourWayGraph()
        val west = cross.edgeById[1L]!!
        assertEquals(
            4,
            RoadMapMatcher.incidentLineCountAtTravelEnd(
                graphs = listOf(cross),
                regionId = cross.regionId,
                edge = west,
                travelAgainstCoords = false,
            ),
        )
        val tee = tJunctionGraph()
        val teeWest = tee.edgeById[1L]!!
        assertEquals(
            3,
            RoadMapMatcher.incidentLineCountAtTravelEnd(
                graphs = listOf(tee),
                regionId = tee.regionId,
                edge = teeWest,
                travelAgainstCoords = false,
            ),
        )
    }

    @Test
    fun remainingToComplex_unbindsAtCrossNotTJunction() {
        val cross = fourWayGraph()
        val west = cross.edgeById[1L]!!
        val rem = RoadMapMatcher.remainingToComplexJunctionM(
            graphs = listOf(cross),
            regionId = cross.regionId,
            edge = west,
            alongTrackM = 70.0,
            travelAgainstCoords = false,
            allowAgainstOneway = false,
        )
        assertEquals(30.0, rem!!, 0.6)
        assertTrue(RoadMatchFreeTurnsMath.shouldRelease(rem))

        val tee = tJunctionGraph()
        val teeWest = tee.edgeById[1L]!!
        val teeRem = RoadMapMatcher.remainingToComplexJunctionM(
            graphs = listOf(tee),
            regionId = tee.regionId,
            edge = teeWest,
            alongTrackM = 80.0,
            travelAgainstCoords = false,
            allowAgainstOneway = false,
        )
        assertNull(teeRem)
        assertFalse(RoadMatchFreeTurnsMath.shouldRelease(teeRem))
    }

    companion object {
        const val CENTER_LAT = 55.75
        const val CENTER_LON = 37.61

        fun fourWayGraph(): RoadGraph = graphOf(
            regionId = "free-turns-cross",
            edges = listOf(
                eastWest(id = 1L, fromNode = 0, toNode = 1, westOfCenterM = 100.0, eastOfCenterM = 0.0),
                eastWest(id = 2L, fromNode = 1, toNode = 2, westOfCenterM = 0.0, eastOfCenterM = 100.0),
                northSouth(id = 3L, fromNode = 3, toNode = 1, southOfCenterM = 100.0, northOfCenterM = 0.0),
                northSouth(id = 4L, fromNode = 1, toNode = 4, southOfCenterM = 0.0, northOfCenterM = 100.0),
            ),
        )

        fun tJunctionGraph(): RoadGraph = graphOf(
            regionId = "free-turns-tee",
            edges = listOf(
                eastWest(id = 1L, fromNode = 0, toNode = 1, westOfCenterM = 100.0, eastOfCenterM = 0.0),
                eastWest(id = 2L, fromNode = 1, toNode = 2, westOfCenterM = 0.0, eastOfCenterM = 100.0),
                northSouth(id = 3L, fromNode = 3, toNode = 1, southOfCenterM = 100.0, northOfCenterM = 0.0),
            ),
        )

        private fun graphOf(regionId: String, edges: List<RoadEdge>): RoadGraph {
            return RoadGraph(
                regionId = regionId,
                graphVersion = 1,
                bbox = doubleArrayOf(CENTER_LON - 0.01, CENTER_LAT - 0.01, CENTER_LON + 0.01, CENTER_LAT + 0.01),
                edges = edges,
            )
        }

        private fun eastWest(
            id: Long,
            fromNode: Int,
            toNode: Int,
            westOfCenterM: Double,
            eastOfCenterM: Double,
        ): RoadEdge {
            val lon0 = CENTER_LON - dLon(westOfCenterM)
            val lon1 = CENTER_LON + dLon(eastOfCenterM)
            val length = westOfCenterM + eastOfCenterM
            return RoadEdge(
                id = id,
                highwayClass = "primary",
                lengthM = length,
                fromNode = fromNode,
                toNode = toNode,
                coords = doubleArrayOf(lon0, CENTER_LAT, lon1, CENTER_LAT),
            )
        }

        private fun northSouth(
            id: Long,
            fromNode: Int,
            toNode: Int,
            southOfCenterM: Double,
            northOfCenterM: Double,
        ): RoadEdge {
            val lat0 = CENTER_LAT - dLat(southOfCenterM)
            val lat1 = CENTER_LAT + dLat(northOfCenterM)
            val length = southOfCenterM + northOfCenterM
            return RoadEdge(
                id = id,
                highwayClass = "primary",
                lengthM = length,
                fromNode = fromNode,
                toNode = toNode,
                coords = doubleArrayOf(CENTER_LON, lat0, CENTER_LON, lat1),
            )
        }

        private fun dLat(m: Double) = m / 111_320.0
        private fun dLon(m: Double) = m / (111_320.0 * cos(Math.toRadians(CENTER_LAT)))
    }
}
