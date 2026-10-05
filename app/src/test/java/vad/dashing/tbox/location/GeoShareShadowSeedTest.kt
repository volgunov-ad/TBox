package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.esp.LocationSource
import vad.dashing.tbox.location.roadmatch.RoadEdge
import vad.dashing.tbox.location.roadmatch.RoadGraph
import vad.dashing.tbox.location.roadmatch.RoadGraphStore
import vad.dashing.tbox.location.roadmatch.RoadMatchManualSeedRepository

class GeoShareShadowSeedTest {

    @Before
    fun clearPending() {
        RoadMatchManualSeedRepository.clear()
        RoadGraphStore.clear()
    }

    @Test
    fun shadowMode_advancedAndNoFixOnly() {
        assertTrue(
            GeoShareShadowSeed.isShadowSeedMode(
                MockPowerState.ALWAYS_ON,
                MockCanSpeedMode.CONSTANT,
            ),
        )
        assertTrue(
            GeoShareShadowSeed.isShadowSeedMode(
                MockPowerState.WHEN_NO_FIX,
                MockCanSpeedMode.NONE,
            ),
        )
        assertFalse(
            GeoShareShadowSeed.isShadowSeedMode(
                MockPowerState.OFF,
                MockCanSpeedMode.CONSTANT,
            ),
        )
        assertFalse(
            GeoShareShadowSeed.isShadowSeedMode(
                MockPowerState.ALWAYS_ON,
                MockCanSpeedMode.ALWAYS,
            ),
        )
        assertFalse(
            GeoShareShadowSeed.isShadowSeedMode(
                MockPowerState.ALWAYS_ON,
                MockCanSpeedMode.NONE,
            ),
        )
    }

    @Test
    fun apply_requestsSeedInAdvanced() {
        val outcome = GeoShareShadowSeed.apply(
            lat = 55.75,
            lon = 37.61,
            power = MockPowerState.ALWAYS_ON,
            storedMode = MockCanSpeedMode.CONSTANT,
            locationSource = LocationSource.TBOX,
            travelBearingDeg = 90f,
        )
        assertEquals(GeoShareShadowSeed.Outcome.APPLIED, outcome)
        val seed = RoadMatchManualSeedRepository.take()
        assertEquals(55.75, seed!!.lat, 1e-9)
        assertEquals(37.61, seed.lon, 1e-9)
        assertEquals(90f, seed.travelBearingDeg, 0.01f)
        assertNull(RoadMatchManualSeedRepository.peek())
    }

    @Test
    fun apply_snapsBearingToNearestOnewayEdge() {
        val edge = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 1_000.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
            oneway = 1,
        )
        RoadGraphStore.put(
            "share-seed",
            RoadGraph(
                regionId = "share-seed",
                graphVersion = 1,
                bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
                edges = listOf(edge),
            ),
        )
        val outcome = GeoShareShadowSeed.apply(
            lat = 55.75,
            lon = 37.61,
            power = MockPowerState.ALWAYS_ON,
            storedMode = MockCanSpeedMode.CONSTANT,
            locationSource = LocationSource.TBOX,
            travelBearingDeg = 250f,
        )
        assertEquals(GeoShareShadowSeed.Outcome.APPLIED, outcome)
        val seed = RoadMatchManualSeedRepository.take()!!
        assertEquals(90f, seed.travelBearingDeg, 1f)
    }

    @Test
    fun apply_keepsBearingWhenNoNearEdge() {
        val outcome = GeoShareShadowSeed.apply(
            lat = 55.75,
            lon = 37.61,
            power = MockPowerState.ALWAYS_ON,
            storedMode = MockCanSpeedMode.CONSTANT,
            locationSource = LocationSource.TBOX,
            travelBearingDeg = 33f,
        )
        assertEquals(GeoShareShadowSeed.Outcome.APPLIED, outcome)
        assertEquals(33f, RoadMatchManualSeedRepository.take()!!.travelBearingDeg, 0.01f)
    }

    @Test
    fun apply_rejectsWhenMockOff() {
        val outcome = GeoShareShadowSeed.apply(
            lat = 55.75,
            lon = 37.61,
            power = MockPowerState.OFF,
            storedMode = MockCanSpeedMode.CONSTANT,
            locationSource = LocationSource.TBOX,
            travelBearingDeg = 0f,
        )
        assertEquals(GeoShareShadowSeed.Outcome.MOCK_OFF, outcome)
        assertNull(RoadMatchManualSeedRepository.peek())
    }

    @Test
    fun apply_rejectsDirectAndAlwaysEnhance() {
        val direct = GeoShareShadowSeed.apply(
            lat = 55.75,
            lon = 37.61,
            power = MockPowerState.ALWAYS_ON,
            storedMode = MockCanSpeedMode.NONE,
            locationSource = LocationSource.TBOX,
            travelBearingDeg = 0f,
        )
        assertEquals(GeoShareShadowSeed.Outcome.WRONG_MODE, direct)
        val always = GeoShareShadowSeed.apply(
            lat = 55.75,
            lon = 37.61,
            power = MockPowerState.ALWAYS_ON,
            storedMode = MockCanSpeedMode.ALWAYS,
            locationSource = LocationSource.TBOX,
            travelBearingDeg = 0f,
        )
        assertEquals(GeoShareShadowSeed.Outcome.WRONG_MODE, always)
        assertNull(RoadMatchManualSeedRepository.peek())
    }

    @Test
    fun apply_rejectsAndroidSource() {
        val outcome = GeoShareShadowSeed.apply(
            lat = 55.75,
            lon = 37.61,
            power = MockPowerState.ALWAYS_ON,
            storedMode = MockCanSpeedMode.CONSTANT,
            locationSource = LocationSource.ANDROID,
            travelBearingDeg = 0f,
        )
        assertEquals(GeoShareShadowSeed.Outcome.ANDROID_SOURCE, outcome)
        assertNull(RoadMatchManualSeedRepository.peek())
    }
}
