package vad.dashing.tbox.speedcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeedCamRepositoryTest {
    @Before
    fun clear() {
        SpeedCamRepository.clear()
    }

    @Test
    fun mapMarkersWithoutBearingStillPublished() {
        val index = SpeedCamIndex(
            listOf(
                SpeedCamPoint(1, 37.0, 55.0, typeCode = 1, speedKmh = 60, dirType = 0, directionDeg = 0),
            ),
        )
        SpeedCamRepository.updateFromPose(
            index = index,
            installedMeta = SpeedCamPackManager.Snapshot(installed = true, pointCount = 1),
            lat = 55.0,
            lon = 37.0,
            bearingDeg = null,
            vehicleSpeedKmh = 50f,
            radiusM = 500,
            overageKmh = 18,
            showMapMarkers = true,
        )
        val state = SpeedCamRepository.state.value
        assertTrue(state.installed)
        assertNull(state.alert)
        assertEquals(1, state.nearbyForMap.size)
    }

    @Test
    fun alertRequiresBearing() {
        val index = SpeedCamIndex(
            listOf(
                SpeedCamPoint(1, 37.0, 55.002, typeCode = 1, speedKmh = 60, dirType = 0, directionDeg = 0),
            ),
        )
        SpeedCamRepository.updateFromPose(
            index = index,
            installedMeta = SpeedCamPackManager.Snapshot(installed = true, pointCount = 1),
            lat = 55.0,
            lon = 37.0,
            bearingDeg = 0f,
            vehicleSpeedKmh = 50f,
            radiusM = 500,
            overageKmh = 18,
            showMapMarkers = false,
        )
        assertNotNull(SpeedCamRepository.state.value.alert)
    }
}
