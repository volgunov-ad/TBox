package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.LocValues

class MockJunkFixFilterTest {

    @Before
    fun resetDebouncer() {
        JunkSpeedMismatchDebouncer.reset()
    }

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
        assertTrue(MockJunkFixFilter.isAcceptable(good(), carSpeedKmh = 62f, nowElapsedMs = 0L))
    }

    @Test
    fun rejectsAltitudeTooHighImmediately() {
        val r = MockJunkFixFilter.evaluate(good(altitude = 12_000.0), carSpeedKmh = 60f, 0L)
        assertFalse(r.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.ALTITUDE, r.reason)
    }

    @Test
    fun rejectsAbsurdGnssSpeedImmediately() {
        val r = MockJunkFixFilter.evaluate(good(speed = 400f), carSpeedKmh = 60f, 0L)
        assertFalse(r.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.GNSS_SPEED, r.reason)
    }

    @Test
    fun rejectsPoorAccuracyFromHrmsImmediately() {
        val r = MockJunkFixFilter.evaluate(good(hrms = 150f, hdop = null), carSpeedKmh = 60f, 0L)
        assertFalse(r.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.ACCURACY, r.reason)
    }

    @Test
    fun doesNotRejectOnDefaultAccuracyWithoutDop() {
        assertTrue(
            MockJunkFixFilter.isAcceptable(
                good(hdop = null, hrms = null),
                carSpeedKmh = 60f,
                nowElapsedMs = 0L,
            ),
        )
    }

    @Test
    fun speedMismatchNotJunkUntilDebounce() {
        val r0 = MockJunkFixFilter.evaluate(good(speed = 80f), carSpeedKmh = 60f, 0L)
        assertTrue(r0.accepted)
        val r4 = MockJunkFixFilter.evaluate(good(speed = 80f), carSpeedKmh = 60f, 4_000L)
        assertTrue(r4.accepted)
        val r5 = MockJunkFixFilter.evaluate(good(speed = 80f), carSpeedKmh = 60f, 5_000L)
        assertFalse(r5.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.SPEED_MISMATCH, r5.reason)
    }

    @Test
    fun speedMatchClearsJunkAfterOkDebounce() {
        MockJunkFixFilter.evaluate(good(speed = 80f), carSpeedKmh = 60f, 0L)
        MockJunkFixFilter.evaluate(good(speed = 80f), carSpeedKmh = 60f, 5_000L)
        assertTrue(JunkSpeedMismatchDebouncer.isLatched())
        assertFalse(MockJunkFixFilter.isAcceptable(good(speed = 62f), 60f, 5_000L))
        assertTrue(JunkSpeedMismatchDebouncer.isLatched())
        assertTrue(MockJunkFixFilter.isAcceptable(good(speed = 62f), 60f, 7_000L))
        assertFalse(JunkSpeedMismatchDebouncer.isLatched())
    }

    @Test
    fun rawRelativeMismatchHelper() {
        assertTrue(MockJunkFixFilter.isSpeedMismatch(80f, 60f))
        assertFalse(MockJunkFixFilter.isSpeedMismatch(64f, 60f))
    }

    @Test
    fun lowSpeedUsesAbsoluteTolerance() {
        assertFalse(MockJunkFixFilter.isSpeedMismatch(8f, 5f))
        assertTrue(MockJunkFixFilter.isSpeedMismatch(20f, 5f))
    }

    @Test
    fun nullCarSpeedSkipsMismatchCheck() {
        assertTrue(MockJunkFixFilter.isAcceptable(good(speed = 200f), carSpeedKmh = null, 0L))
    }
}
