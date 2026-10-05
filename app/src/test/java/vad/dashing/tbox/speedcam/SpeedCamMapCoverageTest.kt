package vad.dashing.tbox.speedcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.MAPS_CAM_DISTANCE_STEP_M
import vad.dashing.tbox.MAX_MAPS_CAM_LOOKAHEAD_M
import vad.dashing.tbox.MIN_MAPS_CAM_LOOKAHEAD_M
import vad.dashing.tbox.MIN_MAPS_CAM_RADAR_HOLD_M
import vad.dashing.tbox.normalizeMapsCamLookaheadM
import vad.dashing.tbox.normalizeMapsCamRadarHoldM
import vad.dashing.tbox.snapMapsCamDistanceM

class SpeedCamMapCoverageTest {

    @Test
    fun oneWayBeamPointsAtApproachNotTravel() {
        // DIRECTION=90 (eastbound travel) → look/approach beam toward west (270°)
        val beams = SpeedCamMapCoverage.beamsFor(
            lat = 55.75,
            lon = 37.65,
            dirType = 1,
            directionDeg = 90,
            lengthM = 100.0,
            halfWidthM = 1.0,
        )
        assertEquals(1, beams.size)
        assertEquals(SpeedCamMapCoverage.BeamKind.PRIMARY, beams[0].kind)
        assertEquals(270f, SpeedCamMapCoverage.lookBearingDeg(90), 0.01f)
        // Tip of trapezoid is the midpoint of the far edge (points 1 and 2)
        val tipLat = (beams[0].points[1].lat + beams[0].points[2].lat) / 2.0
        val tipLon = (beams[0].points[1].lon + beams[0].points[2].lon) / 2.0
        // West of origin → lon decreases
        assertTrue(tipLon < 37.65)
        assertEquals(55.75, tipLat, 0.002)
    }

    @Test
    fun gaidarSwCameraBeamFacesMagnitApproach() {
        // cam 75303: DIRECTION=210 (SW travel). Approach from Magnit = NE ≈ 30°.
        assertEquals(30f, SpeedCamMapCoverage.lookBearingDeg(210), 0.01f)
        val beams = SpeedCamMapCoverage.beamsFor(
            lat = 56.2476633,
            lon = 43.4445308,
            dirType = 1,
            directionDeg = 210,
            lengthM = 110.0,
            halfWidthM = 1.0,
        )
        val tipLat = (beams[0].points[1].lat + beams[0].points[2].lat) / 2.0
        val tipLon = (beams[0].points[1].lon + beams[0].points[2].lon) / 2.0
        assertTrue("approach tip should be north of camera", tipLat > 56.2476633)
        assertTrue("approach tip should be east of camera", tipLon > 43.4445308)
    }

    @Test
    fun bothWaysProducesPrimaryAndOpposite() {
        val beams = SpeedCamMapCoverage.beamsFor(
            lat = 55.75,
            lon = 37.65,
            dirType = 2,
            directionDeg = 0,
        )
        assertEquals(2, beams.size)
        assertEquals(SpeedCamMapCoverage.BeamKind.PRIMARY, beams[0].kind)
        assertEquals(SpeedCamMapCoverage.BeamKind.OPPOSITE, beams[1].kind)
    }

    @Test
    fun allDirectionsProducesDisk() {
        val beams = SpeedCamMapCoverage.beamsFor(
            lat = 55.75,
            lon = 37.65,
            dirType = 0,
            directionDeg = 0,
        )
        assertEquals(1, beams.size)
        assertEquals(SpeedCamMapCoverage.BeamKind.ALL, beams[0].kind)
        assertTrue(beams[0].radiusM > 0.0)
    }
}

class MapsCamDistanceSnapTest {

    @Test
    fun snapRoundsToTenMeters() {
        assertEquals(300, snapMapsCamDistanceM(304, MIN_MAPS_CAM_LOOKAHEAD_M, MAX_MAPS_CAM_LOOKAHEAD_M))
        assertEquals(310, snapMapsCamDistanceM(305, MIN_MAPS_CAM_LOOKAHEAD_M, MAX_MAPS_CAM_LOOKAHEAD_M))
        assertEquals(MAPS_CAM_DISTANCE_STEP_M, MAPS_CAM_DISTANCE_STEP_M)
    }

    @Test
    fun normalizeLookaheadAndHoldSnap() {
        assertEquals(1000, normalizeMapsCamLookaheadM(1004))
        assertEquals(1010, normalizeMapsCamLookaheadM(1005))
        assertEquals(50, normalizeMapsCamRadarHoldM(54))
        assertEquals(MIN_MAPS_CAM_RADAR_HOLD_M, normalizeMapsCamRadarHoldM(1))
    }
}
