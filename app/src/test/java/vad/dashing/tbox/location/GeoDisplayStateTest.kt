package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues

class GeoDisplayStateTest {

    @Test
    fun truthIsGnssTrustNotBlendGate() {
        val state = GeoDisplayState(
            liveUsable = false,
            retaining = true,
            locateStatus = true,
            latitude = 55.0,
            longitude = 37.0,
            gnssTruthful = true,
        )
        assertTrue(state.isTruthful)
        assertEquals(LocIndicatorState.RETAINING, state.indicator)
    }

    @Test
    fun greenArrowDoesNotImplyTruthWhenGnssUntrusted() {
        val state = GeoDisplayState(
            liveUsable = true,
            retaining = false,
            locateStatus = true,
            latitude = 55.0,
            longitude = 37.0,
            gnssTruthful = false,
        )
        assertFalse(state.isTruthful)
        assertEquals(LocIndicatorState.LIVE, state.indicator)
    }

    @Test
    fun redArrowWhenNoFixNoCoordsNoRetention() {
        val state = GeoDisplayState(
            liveUsable = false,
            retaining = false,
            locateStatus = false,
            latitude = 0.0,
            longitude = 0.0,
            gnssTruthful = false,
        )
        assertEquals(LocIndicatorState.NONE, state.indicator)
        assertFalse(state.isTruthful)
    }

    @Test
    fun constantShadowKeepsBlueWithoutGnss() {
        val state = GeoDisplayState(
            liveUsable = false,
            retaining = true,
            locateStatus = true,
            latitude = 55.0,
            longitude = 37.0,
            gnssTruthful = false,
            mockActive = true,
        )
        assertEquals(LocIndicatorState.RETAINING, state.indicator)
    }

    @Test
    fun fromLiveDefaultsTruthToLiveUsable() {
        val loc = LocValues(locateStatus = true, latitude = 1.0, longitude = 2.0)
        assertTrue(GeoDisplayState.fromLive(loc, liveUsable = true).isTruthful)
        assertFalse(GeoDisplayState.fromLive(loc, liveUsable = false).isTruthful)
    }
}
