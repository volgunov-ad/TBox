package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.trip.tripDurationHms

class TripDurationFormatTest {

    @Test
    fun hms_truncatesToWholeSeconds() {
        assertEquals(Triple(0, 0, 0), tripDurationHms(0L))
        assertEquals(Triple(0, 0, 0), tripDurationHms(999L))
        assertEquals(Triple(0, 0, 1), tripDurationHms(1000L))
        assertEquals(Triple(1, 2, 5), tripDurationHms(3_725_000L))
        assertEquals(Triple(2, 0, 0), tripDurationHms(7_200_000L))
    }
}
