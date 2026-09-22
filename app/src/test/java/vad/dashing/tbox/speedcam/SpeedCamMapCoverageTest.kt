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
    fun oneWayProducesPrimaryTrapezoid() {
        val beams = SpeedCamMapCoverage.beamsFor(
            lat = 55.75,
            lon = 37.65,
            dirType = 1,
            directionDeg = 90,
        )
        assertEquals(1, beams.size)
        assertEquals(SpeedCamMapCoverage.BeamKind.PRIMARY, beams[0].kind)
        assertEquals(4, beams[0].points.size)
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
