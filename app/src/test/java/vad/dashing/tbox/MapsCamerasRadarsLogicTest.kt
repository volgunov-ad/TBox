package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsCamerasRadarsLogicTest {

    @Test
    fun pickAheadPrefersNearer() {
        val pick = MapsCamerasRadarsLogic.pickAhead(
            osm = MapsCamerasRadarsLogic.AheadCandidate(60, 200.0),
            camera = MapsCamerasRadarsLogic.AheadCandidate(40, 400.0),
        )
        assertEquals(60, pick.limitKmh)
        assertEquals(200.0, pick.distanceM!!, 0.01)
    }

    @Test
    fun pickAheadWhenClosePrefersStricter() {
        val pick = MapsCamerasRadarsLogic.pickAhead(
            osm = MapsCamerasRadarsLogic.AheadCandidate(90, 300.0),
            camera = MapsCamerasRadarsLogic.AheadCandidate(60, 340.0),
        )
        assertEquals(60, pick.limitKmh)
        assertEquals(340.0, pick.distanceM!!, 0.01)
    }

    @Test
    fun pickAheadOnlyCamera() {
        val pick = MapsCamerasRadarsLogic.pickAhead(
            osm = null,
            camera = MapsCamerasRadarsLogic.AheadCandidate(50, 120.0),
        )
        assertEquals(50, pick.limitKmh)
    }

    @Test
    fun resolveCurrentPrefersOsm() {
        val kmh = MapsCamerasRadarsLogic.resolveCurrentLimitKmh(
            osmCurrentKmh = 60,
            lastRadar = LastRadarLimit(55.0, 37.0, 40),
            vehicleLat = 55.0,
            vehicleLon = 37.0,
            holdDistanceM = 500,
        )
        assertEquals(60, kmh)
    }

    @Test
    fun resolveCurrentFallsBackToRadarWithinHold() {
        val kmh = MapsCamerasRadarsLogic.resolveCurrentLimitKmh(
            osmCurrentKmh = null,
            lastRadar = LastRadarLimit(55.0, 37.0, 40),
            vehicleLat = 55.0,
            vehicleLon = 37.0,
            holdDistanceM = 500,
        )
        assertEquals(40, kmh)
    }

    @Test
    fun resolveCurrentClearsRadarBeyondHold() {
        // ~0.01 deg lat ≈ 1.1 km
        val kmh = MapsCamerasRadarsLogic.resolveCurrentLimitKmh(
            osmCurrentKmh = null,
            lastRadar = LastRadarLimit(55.0, 37.0, 40),
            vehicleLat = 55.01,
            vehicleLon = 37.0,
            holdDistanceM = 500,
        )
        assertNull(kmh)
    }

    @Test
    fun updateLastRadarCapturesNearbyCamera() {
        val next = MapsCamerasRadarsLogic.updateLastRadar(
            previous = null,
            alertLat = 55.0,
            alertLon = 37.0,
            alertSpeedKmh = 60,
            alertDistanceM = 40.0,
            vehicleLat = 55.001,
            vehicleLon = 37.0,
            holdDistanceM = 500,
        )
        assertEquals(60, next!!.speedKmh)
    }

    @Test
    fun updateLastRadarClearsWhenFar() {
        val prev = LastRadarLimit(55.0, 37.0, 60)
        val next = MapsCamerasRadarsLogic.updateLastRadar(
            previous = prev,
            alertLat = null,
            alertLon = null,
            alertSpeedKmh = null,
            alertDistanceM = null,
            vehicleLat = 55.02,
            vehicleLon = 37.0,
            holdDistanceM = 500,
        )
        assertNull(next)
    }
}
