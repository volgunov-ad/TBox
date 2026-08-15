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
            ),
        )
        assertFalse(
            RoadMatchLeashMath.shouldStretch(
                leavingSameEdge = true,
                sensorsOppose = false,
                crossTrackM = 2.0,
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
        assertFalse(RoadMatchLeashMath.shouldBreakLeash(13.0, 3.0, xtGrowing = false))
        assertTrue(RoadMatchLeashMath.shouldBreakLeash(13.0, 8.0, xtGrowing = false))
        assertTrue(RoadMatchLeashMath.shouldBreakLeash(13.0, 4.0, xtGrowing = true))
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
}
