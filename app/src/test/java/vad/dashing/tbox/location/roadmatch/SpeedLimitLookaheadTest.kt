package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SpeedLimitLookaheadTest {

    @Test
    fun missingAnchorFieldsYieldEmpty() {
        val graph = graph(east(1, 0.0, 200.0, 0, 1, 60))
        assertEquals(
            SpeedLimitLookahead.Result.EMPTY,
            SpeedLimitLookahead.compute(listOf(graph), "test", null, 10.0, false),
        )
        assertEquals(
            SpeedLimitLookahead.Result.EMPTY,
            SpeedLimitLookahead.compute(listOf(graph), "test", 1L, null, false),
        )
        assertEquals(
            SpeedLimitLookahead.Result.EMPTY,
            SpeedLimitLookahead.compute(listOf(graph), "test", 1L, 10.0, null),
        )
    }

    @Test
    fun straightChangeWithinHorizon() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            east(2, 200.0, 300.0, 1, 2, 40),
        )
        val result = look(graph, alongM = 20.0)
        assertEquals(60, result.currentKmh)
        assertEquals(40, result.nextKmh)
        assertFalse(result.nextHidden)
        assertEquals(180.0, result.nextDistanceM!!, 8.0)
    }

    @Test
    fun changeBeyondHorizonIsIgnored() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            east(2, 200.0, 600.0, 1, 2, 60),
            east(3, 600.0, 700.0, 2, 3, 40),
        )
        val result = look(graph, alongM = 20.0)
        assertEquals(60, result.currentKmh)
        assertNull(result.nextKmh)
        assertFalse(result.nextHidden)
    }

    @Test
    fun remainingOnCurrentBeyondHorizonHasNoNext() {
        val graph = graph(
            east(1, 0.0, 800.0, 0, 1, 60),
            east(2, 800.0, 900.0, 1, 2, 40),
        )
        val result = look(graph, alongM = 20.0)
        assertEquals(60, result.currentKmh)
        assertNull(result.nextKmh)
    }

    @Test
    fun sideRoadBeyondStraightConeIsIgnored() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            east(2, 200.0, 350.0, 1, 2, 40),
            north(3, 200.0, 0.0, 150.0, 1, 3, 80),
        )
        val result = look(graph, alongM = 20.0)
        assertEquals(40, result.nextKmh)
        assertFalse(result.nextHidden)
        assertEquals(180.0, result.nextDistanceM!!, 8.0)
    }

    @Test
    fun ySplitDisagreeHidesNext() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            heading(2, 200.0, 0.0, 60.0, 150.0, 1, 2, 40),
            heading(3, 200.0, 0.0, 120.0, 150.0, 1, 3, 80),
        )
        val result = look(graph, alongM = 20.0)
        assertEquals(60, result.currentKmh)
        assertNull(result.nextKmh)
        assertTrue(result.nextHidden)
    }

    @Test
    fun ySplitSameNextUsesCloserDistance() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            heading(2, 200.0, 0.0, 60.0, 150.0, 1, 2, 40),
            heading(3, 200.0, 0.0, 120.0, 80.0, 1, 4, 60),
            heading(
                id = 4,
                fromEastM = 200.0 + kotlin.math.sin(Math.toRadians(120.0)) * 80.0,
                fromNorthM = kotlin.math.cos(Math.toRadians(120.0)) * 80.0,
                headingDeg = 120.0,
                lengthM = 100.0,
                fromNode = 4,
                toNode = 5,
                maxspeed = 40,
            ),
        )
        val result = look(graph, alongM = 20.0)
        assertEquals(40, result.nextKmh)
        assertFalse(result.nextHidden)
        assertEquals(180.0, result.nextDistanceM!!, 8.0)
    }

    @Test
    fun ySplitNullVersusNumberHidesNext() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            heading(2, 200.0, 0.0, 60.0, 150.0, 1, 2, 40),
            heading(3, 200.0, 0.0, 120.0, 150.0, 1, 3, null),
        )
        val result = look(graph, alongM = 20.0)
        assertNull(result.nextKmh)
        assertTrue(result.nextHidden)
    }

    @Test
    fun numericThenUnknownStopsWithoutSkipping() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            east(2, 200.0, 300.0, 1, 2, null),
            east(3, 300.0, 400.0, 2, 3, 40),
        )
        val result = look(graph, alongM = 20.0)
        assertEquals(60, result.currentKmh)
        assertNull(result.nextKmh)
        assertFalse(result.nextHidden)
    }

    @Test
    fun unknownCurrentThenNumericIsNext() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, null),
            east(2, 200.0, 300.0, 1, 2, 60),
        )
        val result = look(graph, alongM = 20.0)
        assertNull(result.currentKmh)
        assertEquals(60, result.nextKmh)
        assertFalse(result.nextHidden)
        assertEquals(180.0, result.nextDistanceM!!, 8.0)
    }

    @Test
    fun sameLimitThenChangeContinuesWalking() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            east(2, 200.0, 280.0, 1, 2, 60),
            east(3, 280.0, 360.0, 2, 3, 40),
        )
        val result = look(graph, alongM = 20.0)
        assertEquals(40, result.nextKmh)
        assertEquals(260.0, result.nextDistanceM!!, 8.0)
    }

    @Test
    fun tJunctionBothBeyondConeHasNoNext() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            north(2, 200.0, 0.0, 150.0, 1, 2, 40),
            north(3, 200.0, 0.0, -150.0, 1, 3, 80),
        )
        val result = look(graph, alongM = 20.0)
        assertEquals(60, result.currentKmh)
        assertNull(result.nextKmh)
        assertFalse(result.nextHidden)
    }

    @Test
    fun againstOnewayOutgoingIsSkipped() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            east(2, 200.0, 350.0, 1, 2, 40, oneway = -1),
        )
        val blocked = look(graph, alongM = 20.0, allowAgainstOneway = false)
        assertNull(blocked.nextKmh)
        assertFalse(blocked.nextHidden)

        val reverse = look(graph, alongM = 20.0, allowAgainstOneway = true)
        assertEquals(40, reverse.nextKmh)
    }

    @Test
    fun directedLimitUsesTravelDirection() {
        val edge = east(
            id = 1,
            fromEastM = 0.0,
            toEastM = 200.0,
            fromNode = 0,
            toNode = 1,
            maxspeed = 90,
            maxspeedForward = 60,
            maxspeedBackward = 40,
        )
        val graph = graph(edge)
        val along = look(graph, alongM = 20.0, against = false)
        assertEquals(60, along.currentKmh)
        val reverse = look(graph, alongM = 180.0, against = true)
        assertEquals(40, reverse.currentKmh)
    }

    @Test
    fun unknownThenUnknownThenNumericWalksTheGap() {
        val graph = graph(
            east(1, 0.0, 150.0, 0, 1, null),
            east(2, 150.0, 250.0, 1, 2, null),
            east(3, 250.0, 350.0, 2, 3, 60),
        )
        val result = look(graph, alongM = 20.0)
        assertNull(result.currentKmh)
        assertEquals(60, result.nextKmh)
        assertEquals(230.0, result.nextDistanceM!!, 8.0)
    }

    @Test
    fun trackerCountsDownWithoutSecondWalk() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            east(2, 200.0, 300.0, 1, 2, 40),
        )
        val tracker = SpeedLimitLookahead.Tracker()
        val first = tracker.update(
            graphs = listOf(graph),
            regionId = "test",
            edgeId = 1L,
            alongTrackM = 20.0,
            travelAgainstCoords = false,
            nowElapsedMs = 1_000L,
        )
        assertEquals(1, tracker.walkCount)
        assertEquals(40, first.nextKmh)
        val second = tracker.update(
            graphs = listOf(graph),
            regionId = "test",
            edgeId = 1L,
            alongTrackM = 40.0,
            travelAgainstCoords = false,
            nowElapsedMs = 1_400L,
        )
        assertEquals(1, tracker.walkCount)
        assertEquals(40, second.nextKmh)
        assertEquals(first.nextDistanceM!! - 20.0, second.nextDistanceM!!, 0.5)
    }

    @Test
    fun trackerRewalksAfterRefreshDistance() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            east(2, 200.0, 300.0, 1, 2, 40),
        )
        val tracker = SpeedLimitLookahead.Tracker()
        tracker.update(
            graphs = listOf(graph),
            regionId = "test",
            edgeId = 1L,
            alongTrackM = 20.0,
            travelAgainstCoords = false,
            nowElapsedMs = 1_000L,
        )
        tracker.update(
            graphs = listOf(graph),
            regionId = "test",
            edgeId = 1L,
            alongTrackM = 20.0 + SpeedLimitLookahead.REFRESH_M + 1.0,
            travelAgainstCoords = false,
            nowElapsedMs = 1_200L,
        )
        assertEquals(2, tracker.walkCount)
    }

    @Test
    fun trackerRewalksAfterRefreshTime() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            east(2, 200.0, 300.0, 1, 2, 40),
        )
        val tracker = SpeedLimitLookahead.Tracker()
        tracker.update(
            graphs = listOf(graph),
            regionId = "test",
            edgeId = 1L,
            alongTrackM = 20.0,
            travelAgainstCoords = false,
            nowElapsedMs = 1_000L,
        )
        tracker.update(
            graphs = listOf(graph),
            regionId = "test",
            edgeId = 1L,
            alongTrackM = 22.0,
            travelAgainstCoords = false,
            nowElapsedMs = 1_000L + SpeedLimitLookahead.REFRESH_MS,
        )
        assertEquals(2, tracker.walkCount)
    }

    @Test
    fun trackerRewalksWhenHorizonOpens() {
        val graph = graph(
            east(1, 0.0, 800.0, 0, 1, 60),
            east(2, 800.0, 900.0, 1, 2, 40),
        )
        val tracker = SpeedLimitLookahead.Tracker()
        val far = tracker.update(
            graphs = listOf(graph),
            regionId = "test",
            edgeId = 1L,
            alongTrackM = 20.0,
            travelAgainstCoords = false,
            nowElapsedMs = 1_000L,
        )
        assertEquals(0, tracker.walkCount)
        assertNull(far.nextKmh)
        val near = tracker.update(
            graphs = listOf(graph),
            regionId = "test",
            edgeId = 1L,
            alongTrackM = 350.0,
            travelAgainstCoords = false,
            nowElapsedMs = 2_000L,
        )
        assertEquals(1, tracker.walkCount)
        assertEquals(40, near.nextKmh)
    }

    @Test
    fun trackerRewalksOnEdgeChange() {
        val graph = graph(
            east(1, 0.0, 200.0, 0, 1, 60),
            east(2, 200.0, 400.0, 1, 2, 40),
            east(3, 400.0, 500.0, 2, 3, 80),
        )
        val tracker = SpeedLimitLookahead.Tracker()
        tracker.update(
            graphs = listOf(graph),
            regionId = "test",
            edgeId = 1L,
            alongTrackM = 20.0,
            travelAgainstCoords = false,
            nowElapsedMs = 1_000L,
        )
        val next = tracker.update(
            graphs = listOf(graph),
            regionId = "test",
            edgeId = 2L,
            alongTrackM = 10.0,
            travelAgainstCoords = false,
            nowElapsedMs = 1_200L,
        )
        assertEquals(2, tracker.walkCount)
        assertEquals(40, next.currentKmh)
        assertEquals(80, next.nextKmh)
    }

    private fun look(
        graph: RoadGraph,
        alongM: Double,
        against: Boolean = false,
        allowAgainstOneway: Boolean = false,
        edgeId: Long = 1L,
    ): SpeedLimitLookahead.Result = SpeedLimitLookahead.compute(
        graphs = listOf(graph),
        regionId = graph.regionId,
        edgeId = edgeId,
        alongTrackM = alongM,
        travelAgainstCoords = against,
        allowAgainstOneway = allowAgainstOneway,
    )

    private fun graph(vararg edges: RoadEdge): RoadGraph = RoadGraph(
        regionId = "test",
        graphVersion = 1,
        bbox = doubleArrayOf(37.58, 55.74, 37.66, 55.77),
        edges = edges.toList(),
    )

    private fun east(
        id: Long,
        fromEastM: Double,
        toEastM: Double,
        fromNode: Int,
        toNode: Int,
        maxspeed: Int?,
        oneway: Int = 0,
        maxspeedForward: Int? = null,
        maxspeedBackward: Int? = null,
    ): RoadEdge = RoadEdge(
        id = id,
        highwayClass = "primary",
        lengthM = kotlin.math.abs(toEastM - fromEastM),
        fromNode = fromNode,
        toNode = toNode,
        coords = doubleArrayOf(lon(fromEastM), LAT, lon(toEastM), LAT),
        oneway = oneway,
        maxspeed = maxspeed,
        maxspeedForward = maxspeedForward,
        maxspeedBackward = maxspeedBackward,
    )

    private fun north(
        id: Long,
        eastM: Double,
        fromNorthM: Double,
        toNorthM: Double,
        fromNode: Int,
        toNode: Int,
        maxspeed: Int?,
    ): RoadEdge = RoadEdge(
        id = id,
        highwayClass = "residential",
        lengthM = kotlin.math.abs(toNorthM - fromNorthM),
        fromNode = fromNode,
        toNode = toNode,
        coords = doubleArrayOf(lon(eastM), lat(fromNorthM), lon(eastM), lat(toNorthM)),
        maxspeed = maxspeed,
    )

    private fun heading(
        id: Long,
        fromEastM: Double,
        fromNorthM: Double,
        headingDeg: Double,
        lengthM: Double,
        fromNode: Int,
        toNode: Int,
        maxspeed: Int?,
    ): RoadEdge = headingFrom(
        id, fromEastM, fromNorthM, headingDeg, lengthM, fromNode, toNode, maxspeed,
    )

    private fun headingFrom(
        id: Long,
        fromEastM: Double,
        fromNorthM: Double,
        headingDeg: Double,
        lengthM: Double,
        fromNode: Int,
        toNode: Int,
        maxspeed: Int?,
    ): RoadEdge {
        val rad = Math.toRadians(headingDeg)
        val toEast = fromEastM + kotlin.math.sin(rad) * lengthM
        val toNorth = fromNorthM + kotlin.math.cos(rad) * lengthM
        return RoadEdge(
            id = id,
            highwayClass = "primary",
            lengthM = lengthM,
            fromNode = fromNode,
            toNode = toNode,
            coords = doubleArrayOf(lon(fromEastM), lat(fromNorthM), lon(toEast), lat(toNorth)),
            maxspeed = maxspeed,
        )
    }

    companion object {
        private const val LAT = 55.75
        private val M_PER_DEG_LAT = 111_320.0
        private val M_PER_DEG_LON = 111_320.0 * cos(Math.toRadians(LAT))

        private fun lon(eastM: Double): Double = 37.60 + eastM / M_PER_DEG_LON
        private fun lat(northM: Double): Double = LAT + northM / M_PER_DEG_LAT
    }
}
