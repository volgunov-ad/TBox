package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues

class MockJunkFixFilterTest {

    private fun good(
        speed: Float = 60f,
        altitude: Double = 100.0,
        hdop: Float? = 1.2f,
        hrms: Float? = null,
    ) = LocValues(
        locateStatus = true,
        latitude = 55.0,
        longitude = 37.0,
        altitude = altitude,
        speed = speed,
        hdop = hdop,
        hrms = hrms,
    )

    @Test
    fun acceptsNormalFix() {
        assertTrue(MockJunkFixFilter.isAcceptable(good(), carSpeedKmh = 62f))
    }

    @Test
    fun rejectsAltitudeTooHigh() {
        val r = MockJunkFixFilter.evaluate(good(altitude = 12_000.0), carSpeedKmh = 60f)
        assertFalse(r.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.ALTITUDE, r.reason)
    }

    @Test
    fun rejectsAbsurdGnssSpeed() {
        val r = MockJunkFixFilter.evaluate(good(speed = 400f), carSpeedKmh = 60f)
        assertFalse(r.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.GNSS_SPEED, r.reason)
    }

    @Test
    fun rejectsPoorAccuracyFromHrms() {
        val r = MockJunkFixFilter.evaluate(good(hrms = 150f, hdop = null), carSpeedKmh = 60f)
        assertFalse(r.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.ACCURACY, r.reason)
    }

    @Test
    fun doesNotRejectOnDefaultAccuracyWithoutDop() {
        assertTrue(
            MockJunkFixFilter.isAcceptable(
                good(hdop = null, hrms = null),
                carSpeedKmh = 60f,
            ),
        )
    }

    @Test
    fun rejectsRelativeSpeedMismatch() {
        // 60 vs 80 ? 25% > 10%
        val r = MockJunkFixFilter.evaluate(good(speed = 80f), carSpeedKmh = 60f)
        assertFalse(r.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.SPEED_MISMATCH, r.reason)
    }

    @Test
    fun acceptsWithinRelativeTolerance() {
        assertTrue(MockJunkFixFilter.isAcceptable(good(speed = 64f), carSpeedKmh = 60f))
    }

    @Test
    fun lowSpeedUsesAbsoluteTolerance() {
        assertTrue(MockJunkFixFilter.isAcceptable(good(speed = 8f), carSpeedKmh = 5f))
        assertFalse(MockJunkFixFilter.isAcceptable(good(speed = 20f), carSpeedKmh = 5f))
    }

    @Test
    fun nullCarSpeedSkipsMismatchCheck() {
        assertTrue(MockJunkFixFilter.isAcceptable(good(speed = 200f), carSpeedKmh = null))
    }
}
