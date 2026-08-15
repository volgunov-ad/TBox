package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadMatchCanvasProjectionTest {

    @Test
    fun followHalfSpan_isMinAtLowSpeedAndMaxOnHighway() {
        assertEquals(
            RoadMatchCanvasProjection.MIN_HALF_SPAN_M,
            RoadMatchCanvasProjection.followHalfSpanM(0.0),
            0.01,
        )
        assertEquals(
            RoadMatchCanvasProjection.MIN_HALF_SPAN_M,
            RoadMatchCanvasProjection.followHalfSpanM(15.0),
            0.01,
        )
        assertEquals(
            RoadMatchCanvasProjection.MAX_HALF_SPAN_M,
            RoadMatchCanvasProjection.followHalfSpanM(120.0),
            0.01,
        )
        assertEquals(
            RoadMatchCanvasProjection.MAX_HALF_SPAN_M,
            RoadMatchCanvasProjection.followHalfSpanM(180.0),
            0.01,
        )
        val mid = RoadMatchCanvasProjection.followHalfSpanM(67.5)
        assertEquals(175.0, mid, 0.5)
    }

    @Test
    fun lerpHeading_takesShortestPathAcrossNorth() {
        val stepped = RoadMatchCanvasProjection.lerpHeadingDeg(350f, 10f, 0.5f)
        assertEquals(0f, stepped, 0.2f)
        assertEquals(10f, RoadMatchCanvasProjection.shortestHeadingDeltaDeg(350f, 0f), 0.01f)
        assertEquals(-10f, RoadMatchCanvasProjection.shortestHeadingDeltaDeg(10f, 0f), 0.01f)
    }

    @Test
    fun headingUp_putsShadowBelowCenterAndAheadAbove() {
        val shadow = OverlayPoseMarker(55.75, 37.61, 90f, visible = true)
        val state = RoadMatchOverlayState(active = true, shadow = shadow)
        val half = 100.0
        val vp = RoadMatchCanvasProjection.viewport(
            state = state,
            aspectRatio = 1f,
            halfHeightM = half,
            headingDeg = 90f,
            aheadFraction = RoadMatchCanvasProjection.HEADING_UP_AHEAD_FRACTION,
        )!!
        val shadowPt = vp.project(shadow.lat, shadow.lon)
        assertEquals(0.5f, shadowPt.x, 0.02f)
        val expectedY = 0.5f + RoadMatchCanvasProjection.HEADING_UP_AHEAD_FRACTION / 2f
        assertEquals(expectedY, shadowPt.y, 0.02f)
        assertTrue(shadowPt.y > 0.5f)

        val ahead = RoadMatchSeedMath.shiftCenter(
            lat = shadow.lat,
            lon = shadow.lon,
            eastM = 40.0,
            northM = 0.0,
        )
        val aheadPt = vp.project(ahead.lat, ahead.lon)
        assertTrue(aheadPt.y < shadowPt.y)
        assertEquals(0.5f, aheadPt.x, 0.04f)
    }

    @Test
    fun headingEast_mapsEastToScreenUp() {
        val shadow = OverlayPoseMarker(55.75, 37.61, 90f, visible = true)
        val state = RoadMatchOverlayState(active = true, shadow = shadow)
        val vp = RoadMatchCanvasProjection.viewport(
            state = state,
            aspectRatio = 1f,
            halfHeightM = 100.0,
            headingDeg = 90f,
            aheadFraction = 0f,
        )!!
        val east = RoadMatchSeedMath.shiftCenter(shadow.lat, shadow.lon, eastM = 30.0, northM = 0.0)
        val eastPt = vp.project(east.lat, east.lon)
        val north = RoadMatchSeedMath.shiftCenter(shadow.lat, shadow.lon, eastM = 0.0, northM = 30.0)
        val northPt = vp.project(north.lat, north.lon)
        assertTrue(eastPt.y < 0.5f)
        assertEquals(0.5f, eastPt.x, 0.03f)
        assertTrue(northPt.x < 0.5f)
        assertEquals(0.5f, northPt.y, 0.03f)
    }

    @Test
    fun followBlendT_isZeroAtDtZeroAndApproachesOne() {
        assertEquals(0f, RoadMatchCanvasProjection.followBlendT(0.0, 0.4), 0.0f)
        val step = RoadMatchCanvasProjection.followBlendT(0.016, 0.4)
        assertTrue(step in 0.02f..0.08f)
        val longStep = RoadMatchCanvasProjection.followBlendT(2.0, 0.4)
        assertTrue(longStep > 0.95f)
    }
}
