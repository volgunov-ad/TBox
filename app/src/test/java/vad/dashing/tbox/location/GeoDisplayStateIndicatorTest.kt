package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDisplayStateIndicatorTest {

    @Test
    fun forceRetainKeepsBlueEvenWhenLiveUsable() {
        val state = GeoDisplayState(
            liveUsable = true,
            retaining = true,
            locateStatus = true,
            latitude = 55.0,
            longitude = 37.0,
            forceRetainIndicator = true,
        )
        assertEquals(LocIndicatorState.RETAINING, state.indicator)
        assertTrueTruth(state.isTruthful)
    }

    @Test
    fun withoutForceLiveUsableIsGreen() {
        val state = GeoDisplayState(
            liveUsable = true,
            retaining = true,
            locateStatus = true,
            latitude = 55.0,
            longitude = 37.0,
            forceRetainIndicator = false,
        )
        assertEquals(LocIndicatorState.LIVE, state.indicator)
    }

    private fun assertTrueTruth(v: Boolean) {
        org.junit.Assert.assertTrue(v)
    }
}
