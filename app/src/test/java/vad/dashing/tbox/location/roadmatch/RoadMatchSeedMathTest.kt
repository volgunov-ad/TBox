package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RoadMatchSeedMathTest {

    @Test
    fun bearingFromCanvasDelta_cardinals() {
        assertEquals(0f, RoadMatchSeedMath.bearingFromCanvasDelta(0f, -10f), 0.1f)
        assertEquals(90f, RoadMatchSeedMath.bearingFromCanvasDelta(10f, 0f), 0.1f)
        assertEquals(180f, RoadMatchSeedMath.bearingFromCanvasDelta(0f, 10f), 0.1f)
        assertEquals(270f, RoadMatchSeedMath.bearingFromCanvasDelta(-10f, 0f), 0.1f)
    }

    @Test
    fun headingRingTickPointsNorthThenEast() {
        val north = RoadMatchSeedMath.headingRingTickOffset(0f, 40f)
        assertEquals(0f, north.first, 0.1f)
        assertEquals(-40f, north.second, 0.1f)
        val east = RoadMatchSeedMath.headingRingTickOffset(90f, 40f)
        assertEquals(40f, east.first, 0.1f)
        assertEquals(0f, east.second, 0.1f)
    }

    @Test
    fun headingRingRejectsCenterAndFar() {
        assertFalse(RoadMatchSeedMath.isOnHeadingRing(0f, 0f, 20f, 40f))
        assertTrue(RoadMatchSeedMath.isOnHeadingRing(30f, 0f, 20f, 40f))
        assertFalse(RoadMatchSeedMath.isOnHeadingRing(80f, 0f, 20f, 40f))
    }

    @Test
    fun pinchZoomInShrinksSpan() {
        val next = RoadMatchSeedMath.applyPinchZoom(200.0, zoom = 2f)
        assertEquals(100.0, next, 0.01)
        assertEquals(
            RoadMatchSeedMath.SET_MIN_HALF_SPAN_M,
            RoadMatchSeedMath.applyPinchZoom(40.0, zoom = 4f),
            0.01,
        )
        assertEquals(
            RoadMatchSeedMath.SET_MAX_HALF_SPAN_M,
            RoadMatchSeedMath.applyPinchZoom(200.0, zoom = 0.1f),
            0.01,
        )
    }

    @Test
    fun panRightMovesCenterWest() {
        val (eastM, northM) = RoadMatchSeedMath.panToEastNorthM(
            panXpx = 50f,
            panYpx = 0f,
            widthPx = 200f,
            heightPx = 200f,
            halfWidthM = 100.0,
            halfHeightM = 100.0,
        )
        assertTrue(eastM < 0.0)
        assertEquals(0.0, northM, 1e-9)
        val moved = RoadMatchSeedMath.shiftCenter(55.75, 37.61, eastM, northM)
        assertTrue(moved.lon < 37.61)
        assertEquals(55.75, moved.lat, 1e-8)
    }

    @Test
    fun panDownMovesCenterNorth() {
        val (eastM, northM) = RoadMatchSeedMath.panToEastNorthM(
            panXpx = 0f,
            panYpx = 40f,
            widthPx = 200f,
            heightPx = 200f,
            halfWidthM = 100.0,
            halfHeightM = 100.0,
        )
        assertEquals(0.0, eastM, 1e-9)
        assertTrue(northM > 0.0)
        val moved = RoadMatchSeedMath.shiftCenter(55.75, 37.61, eastM, northM)
        assertTrue(moved.lat > 55.75)
    }

    @Test
    fun manualSeedRejectsInvalidAndWrapsBearing() {
        assertNull(RoadMatchManualSeed.create(0.0, 0.0, 10f))
        assertNull(RoadMatchManualSeed.create(95.0, 37.0, 10f))
        assertNull(RoadMatchManualSeed.create(55.0, 37.0, Float.NaN))
        val seed = RoadMatchManualSeed.create(55.75, 37.61, -90f)
        assertNotNull(seed)
        assertEquals(270f, seed!!.travelBearingDeg, 0.01f)
    }

    @Test
    fun manualSeedRepositoryTakeClearsPending() {
        RoadMatchManualSeedRepository.clear()
        val seed = RoadMatchManualSeed.create(55.75, 37.61, 45f)!!
        RoadMatchManualSeedRepository.request(seed)
        assertEquals(seed, RoadMatchManualSeedRepository.peek())
        assertEquals(seed, RoadMatchManualSeedRepository.take())
        assertNull(RoadMatchManualSeedRepository.take())
        RoadMatchManualSeedRepository.request(seed)
        RoadMatchManualSeedRepository.clear()
        assertNull(RoadMatchManualSeedRepository.peek())
    }

    @Test
    fun distanceAroundMoscowIsSane() {
        val d = RoadMatchSeedMath.distanceM(55.75, 37.61, 55.7536, 37.61)
        assertTrue(d in 350.0..450.0)
        assertTrue(abs(d - 400.0) < 30.0)
    }
}
