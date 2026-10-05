package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Test

class RoadMatchBasemapOpacityTest {
    @Test
    fun normalizeSnapsToNearestStep() {
        assertEquals(0, RoadMatchBasemapOpacity.normalize(-5))
        assertEquals(15, RoadMatchBasemapOpacity.normalize(14))
        assertEquals(75, RoadMatchBasemapOpacity.normalize(80))
    }

    @Test
    fun viewAlphaMatchesTransparency() {
        assertEquals(1f, RoadMatchBasemapOpacity.viewAlpha(0), 1e-6f)
        assertEquals(0.85f, RoadMatchBasemapOpacity.viewAlpha(15), 1e-6f)
        assertEquals(0.25f, RoadMatchBasemapOpacity.viewAlpha(75), 1e-6f)
    }
}
