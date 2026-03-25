package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class TripDurationFormatTest {

    @Test
    fun rounding_nearestMinute() {
        assertEquals(0, tripDurationRoundedMinutes(29_999L))
        assertEquals(1, tripDurationRoundedMinutes(30_000L))
        assertEquals(35, tripDurationRoundedMinutes(35 * 60_000L))
        assertEquals(94, tripDurationRoundedMinutes(94 * 60_000L + 20_000L))
    }
}
