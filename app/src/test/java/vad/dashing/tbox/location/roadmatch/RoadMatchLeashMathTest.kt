package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadMatchLeashMathTest {

    @Test
    fun stretchWhenLeavingOrSensorsOppose() {
        assertTrue(
            RoadMatchLeashMath.shouldStretch(
                leavingSameEdge = true,
                sensorsOppose = false,
                crossTrackM = 5.0,
                dueTurn = true,
            ),
        )
        assertFalse(
            RoadMatchLeashMath.shouldStretch(
                leavingSameEdge = true,
                sensorsOppose = false,
                crossTrackM = 5.0,
                dueTurn = false,
            ),
        )
        assertFalse(
            RoadMatchLeashMath.shouldStretch(
                leavingSameEdge = true,
                sensorsOppose = false,
                crossTrackM = 2.0,
                dueTurn = true,
            ),
        )
        assertTrue(
            RoadMatchLeashMath.shouldStretch(
                leavingSameEdge = false,
                sensorsOppose = true,
                drYawAbs = 12f,
            ),
        )
        assertFalse(
            RoadMatchLeashMath.shouldStretch(
                leavingSameEdge = false,
                sensorsOppose = true,
                drYawAbs = 2f,
            ),
        )
        assertFalse(RoadMatchLeashMath.shouldStretch(leavingSameEdge = false, sensorsOppose = false))
    }

    @Test
    fun breakNeedsBothDistanceAndPath() {
        assertFalse(RoadMatchLeashMath.shouldBreakLeash(11.0, 20.0, xtGrowing = true))
        assertFalse(RoadMatchLeashMath.shouldBreakLeash(17.0, 20.0, xtGrowing = true))
        assertFalse(RoadMatchLeashMath.shouldBreakLeash(19.0, 3.0, xtGrowing = false))
        assertTrue(RoadMatchLeashMath.shouldBreakLeash(19.0, 8.0, xtGrowing = false))
        assertTrue(RoadMatchLeashMath.shouldBreakLeash(19.0, 4.0, xtGrowing = true))
        // Tight curve (145417): 14 m xt is still on the street, not a courtyard leave.
        assertFalse(RoadMatchLeashMath.shouldBreakLeash(14.0, 8.0, xtGrowing = true))
        // Circulating (`151302`): xt to the old chord is not a courtyard leave.
        assertFalse(
            RoadMatchLeashMath.shouldBreakLeash(27.0, 12.0, xtGrowing = true, turning = true),
        )
        assertTrue(
            RoadMatchLeashMath.shouldBreakLeash(27.0, 12.0, xtGrowing = true, turning = false),
        )
        // Yard: slightly tighter xt when not turning; mid-turn still holds (`151302`).
        assertTrue(
            RoadMatchLeashMath.shouldBreakLeash(
                16.0, 8.0, xtGrowing = true, turning = false, courtyardLike = true,
            ),
        )
        assertFalse(
            RoadMatchLeashMath.shouldBreakLeash(
                16.0, 8.0, xtGrowing = true, turning = true, courtyardLike = true,
            ),
        )
    }

    @Test
    fun headingClustersWrapAndSeparate() {
        assertEquals(0, RoadMatchLeashMath.headingClusters(emptyList()))
        assertEquals(1, RoadMatchLeashMath.headingClusters(listOf(10f, 15f, 20f)))
        assertEquals(3, RoadMatchLeashMath.headingClusters(listOf(0f, 90f, 180f)))
        assertEquals(1, RoadMatchLeashMath.headingClusters(listOf(350f, 10f, 5f)))
    }

    @Test
    fun complexJunctionNeedsThreeWays() {
        assertFalse(RoadMatchLeashMath.isComplexJunction(outgoing = 2, nearbyClusters = 2))
        assertTrue(RoadMatchLeashMath.isComplexJunction(outgoing = 3, nearbyClusters = 1))
        assertTrue(RoadMatchLeashMath.isComplexJunction(outgoing = 1, nearbyClusters = 3))
    }

    @Test
    fun promoteRequiresPositionNotHeadingAlone() {
        assertFalse(RoadMatchLeashMath.shouldPromoteFree(5.0, 40f))
        assertTrue(RoadMatchLeashMath.shouldPromoteFree(16.0, 5f))
        assertTrue(RoadMatchLeashMath.shouldPromoteFree(9.0, 32f))
        assertFalse(RoadMatchLeashMath.shouldPromoteFree(9.0, 20f))
    }

    @Test
    fun settleRejectsTurnAndStretch() {
        assertTrue(
            RoadMatchLeashMath.maneuverSettled(
                drYawAbs = 1f,
                residualDeg = 5f,
                dueTurn = false,
                stretching = false,
            ),
        )
        assertFalse(
            RoadMatchLeashMath.maneuverSettled(
                drYawAbs = 1f,
                residualDeg = 5f,
                dueTurn = true,
                stretching = false,
            ),
        )
        assertFalse(
            RoadMatchLeashMath.maneuverSettled(
                drYawAbs = 1f,
                residualDeg = 5f,
                dueTurn = false,
                stretching = true,
            ),
        )
    }

    @Test
    fun freeParticleKeepsInstrumentHeading() {
        val free = RoadMatchPose(55.75, 37.60, 0f)
        val from = RoadMatchPose(55.75, 37.61, 90f)
        val dest = RoadMatchLeashMath.destination(from.lat, from.lon, 90f, 10.0)
        val to = RoadMatchPose(dest.first, dest.second, 95f)
        val next = RoadMatchLeashMath.stepFreePose(free, from, to)
        assertEquals(5f, next.bearingDeg, 0.2f)
        val moved = RoadGraph.haversineM(free.lat, free.lon, next.lat, next.lon)
        assertEquals(10.0, moved, 0.3)
    }

    @Test
    fun headingPullAllowedWhenCloseEvenOnHold() {
        // Field 073412 07:55: HOLD_EDGE residual ~12° must pull again.
        assertFalse(
            RoadMatchLeashMath.shouldInhibitHeadingPull(
                residualDeg = 12f,
                holding = true,
                leavingSameEdge = false,
                dueTurn = false,
                switched = false,
                sameEdgeLink = false,
                sensorsOpposeEdge = false,
                turnHintActive = false,
            ),
        )
        // Yard exit 60–90° / large residual: still inhibit.
        assertTrue(
            RoadMatchLeashMath.shouldInhibitHeadingPull(
                residualDeg = 60f,
                holding = true,
                leavingSameEdge = false,
                dueTurn = true,
                switched = false,
                sameEdgeLink = false,
                sensorsOpposeEdge = false,
                turnHintActive = false,
            ),
        )
        // Leaving the road: inhibit even if residual is in the "close" band.
        assertTrue(
            RoadMatchLeashMath.shouldInhibitHeadingPull(
                residualDeg = 14f,
                holding = false,
                leavingSameEdge = true,
                dueTurn = false,
                switched = false,
                sameEdgeLink = false,
                sensorsOpposeEdge = false,
                turnHintActive = false,
            ),
        )
        // Stalk hint on sticky edge: keep inhibit (do not fight the turn).
        assertTrue(
            RoadMatchLeashMath.shouldInhibitHeadingPull(
                residualDeg = 5f,
                holding = true,
                leavingSameEdge = false,
                dueTurn = false,
                switched = false,
                sameEdgeLink = false,
                sensorsOpposeEdge = false,
                turnHintActive = true,
            ),
        )
    }

    @Test
    fun regrabByHeadingAfterLostSticky() {
        assertTrue(
            RoadMatchLeashMath.shouldRegrabByHeading(
                residualToBestDeg = 12f,
                crossTrackM = 24.0,
                switchRejected = false,
            ),
        )
        assertFalse(
            RoadMatchLeashMath.shouldRegrabByHeading(
                residualToBestDeg = 12f,
                crossTrackM = 40.0,
                switchRejected = false,
            ),
        )
        assertFalse(
            RoadMatchLeashMath.shouldRegrabByHeading(
                residualToBestDeg = 25f,
                crossTrackM = 20.0,
                switchRejected = false,
            ),
        )
        assertFalse(
            RoadMatchLeashMath.shouldRegrabByHeading(
                residualToBestDeg = 12f,
                crossTrackM = 20.0,
                switchRejected = true,
            ),
        )
    }
}
