package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoDisplayStateIndicatorTest {

    @Test
    fun liveUsableIsGreenEvenWhenRetaining() {
        val state = GeoDisplayState(
            liveUsable = true,
            retaining = true,
            locateStatus = true,
            latitude = 55.0,
            longitude = 37.0,
        )
        assertEquals(LocIndicatorState.LIVE, state.indicator)
        assertTrue(state.isTruthful)
    }

    @Test
    fun retainingWithoutLiveUsableIsBlue() {
        val state = GeoDisplayState(
            liveUsable = false,
            retaining = true,
            locateStatus = true,
            latitude = 55.0,
            longitude = 37.0,
        )
        assertEquals(LocIndicatorState.RETAINING, state.indicator)
    }
}
