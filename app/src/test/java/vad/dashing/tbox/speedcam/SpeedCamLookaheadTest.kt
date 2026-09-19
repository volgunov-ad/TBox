package vad.dashing.tbox.speedcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedCamLookaheadTest {
    private fun point(
        id: Int,
        lat: Double,
        lon: Double,
        speed: Int = 60,
        dirType: Int = 0,
        direction: Int = 0,
        type: Int = 1,
    ) = SpeedCamPoint(
        id = id,
        lon = lon,
        lat = lat,
        typeCode = type,
        speedKmh = speed,
        dirType = dirType,
        directionDeg = direction,
    )

    @Test
    fun picksNearestAheadIgnoringBehind() {
        // ~111 m per 0.001° lat
        val originLat = 55.0
        val originLon = 37.0
        val ahead = point(1, originLat + 0.002, originLon) // ~222 m north
        val behind = point(2, originLat - 0.001, originLon) // ~111 m south
        val index = SpeedCamIndex(listOf(ahead, behind))
        val alert = SpeedCamLookahead.findNearest(
            index = index,
            lat = originLat,
            lon = originLon,
            bearingDeg = 0f,
            radiusM = 500,
            vehicleSpeedKmh = 50f,
            overageKmh = 18,
        )
        assertNotNull(alert)
        assertEquals(1, alert!!.point.id)
        assertTrue(alert.distanceM in 200.0..250.0)
    }

    @Test
    fun dirTypeOneFiltersWrongHeading() {
        val originLat = 55.0
        val originLon = 37.0
        val cam = point(
            id = 3,
            lat = originLat + 0.002,
            lon = originLon,
            dirType = 1,
            direction = 0, // north
        )
        val index = SpeedCamIndex(listOf(cam))
        val same = SpeedCamLookahead.findNearest(
            index, originLat, originLon, bearingDeg = 10f,
            radiusM = 500, vehicleSpeedKmh = 80f, overageKmh = 18,
        )
        assertNotNull(same)
        val opposite = SpeedCamLookahead.findNearest(
            index, originLat, originLon, bearingDeg = 180f,
            radiusM = 500, vehicleSpeedKmh = 80f, overageKmh = 18,
        )
        // Traveling south: camera is behind travel cone → null
        assertNull(opposite)
    }

    @Test
    fun overLimitUsesOverageAllowance() {
        val originLat = 55.0
        val originLon = 37.0
        val cam = point(1, originLat + 0.001, originLon, speed = 60)
        val index = SpeedCamIndex(listOf(cam))
        val under = SpeedCamLookahead.findNearest(
            index, originLat, originLon, 0f, 500, vehicleSpeedKmh = 70f, overageKmh = 18,
        )
        assertNotNull(under)
        assertEquals(false, under!!.overLimit)
        val over = SpeedCamLookahead.findNearest(
            index, originLat, originLon, 0f, 500, vehicleSpeedKmh = 80f, overageKmh = 18,
        )
        assertNotNull(over)
        assertEquals(true, over!!.overLimit)
    }

    @Test
    fun relativeDirectionSameVsOncoming() {
        assertEquals(
            SpeedCamRelativeDirection.SAME,
            SpeedCamLookahead.relativeDirection(10f, 20),
        )
        assertEquals(
            SpeedCamRelativeDirection.ONCOMING,
            SpeedCamLookahead.relativeDirection(0f, 180),
        )
    }

    @Test
    fun formatDistance() {
        assertEquals("250 м", SpeedCamLookahead.formatDistanceM(250.4))
        assertEquals("1.2 км", SpeedCamLookahead.formatDistanceM(1200.0))
        assertEquals("250 m", SpeedCamLookahead.formatDistanceMEn(250.4))
        assertEquals("1.2 km", SpeedCamLookahead.formatDistanceMEn(1200.0))
    }
}
