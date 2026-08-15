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
        MockJunkFixFilter.resetSession()
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
        val r2 = MockJunkFixFilter.evaluate(good(speed = 80f), carSpeedKmh = 60f, 2_000L)
        assertTrue(r2.accepted)
        val r3 = MockJunkFixFilter.evaluate(good(speed = 80f), carSpeedKmh = 60f, 3_000L)
        assertFalse(r3.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.SPEED_MISMATCH, r3.reason)
    }

    @Test
    fun speedMatchClearsJunkAfterOkDebounce() {
        MockJunkFixFilter.evaluate(good(speed = 80f), carSpeedKmh = 60f, 0L)
        MockJunkFixFilter.evaluate(good(speed = 80f), carSpeedKmh = 60f, 3_000L)
        assertTrue(JunkSpeedMismatchDebouncer.isLatched())
        assertFalse(MockJunkFixFilter.isAcceptable(good(speed = 62f), 60f, 3_000L))
        assertTrue(JunkSpeedMismatchDebouncer.isLatched())
        assertTrue(MockJunkFixFilter.isAcceptable(good(speed = 62f), 60f, 5_000L))
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

    @Test
    fun rejectsCoordJumpVsCanImmediately() {
        assertTrue(MockJunkFixFilter.isAcceptable(good(), carSpeedKmh = 67f, nowElapsedMs = 1_000L))
        val jumped = LocValues(
            locateStatus = true,
            latitude = 55.008,
            longitude = 37.0,
            altitude = 100.0,
            speed = 62f,
            hdop = 1.2f,
        )
        val r = MockJunkFixFilter.evaluate(jumped, carSpeedKmh = 67f, nowElapsedMs = 2_000L)
        assertFalse(r.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.COORD_JUMP, r.reason)
    }

    @Test
    fun acceptsNormalStepAtHighwaySpeed() {
        assertTrue(MockJunkFixFilter.isAcceptable(good(), carSpeedKmh = 67f, nowElapsedMs = 1_000L))
        val next = LocValues(
            locateStatus = true,
            latitude = 55.00015,
            longitude = 37.0,
            altitude = 100.0,
            speed = 67f,
            hdop = 1.2f,
        )
        assertTrue(MockJunkFixFilter.isAcceptable(next, carSpeedKmh = 67f, nowElapsedMs = 2_000L))
    }

    @Test
    fun rejectsAltitudeJumpImmediately() {
        assertTrue(MockJunkFixFilter.isAcceptable(good(altitude = 173.0), carSpeedKmh = 67f, nowElapsedMs = 1_000L))
        val r = MockJunkFixFilter.evaluate(
            good(altitude = 1516.0),
            carSpeedKmh = 67f,
            nowElapsedMs = 2_000L,
        )
        assertFalse(r.accepted)
        assertEquals(MockJunkFixFilter.RejectReason.ALTITUDE_JUMP, r.reason)
    }

    @Test
    fun coordJumpHelperUsesCanBudgetNotGnssSpeed() {
        val loc = LocValues(
            locateStatus = true,
            latitude = 55.004,
            longitude = 37.0,
            altitude = 100.0,
            speed = 194f,
            hdop = 1.2f,
        )
        assertTrue(
            MockJunkFixFilter.isCoordJump(
                loc = loc,
                prevLat = 55.0,
                prevLon = 37.0,
                prevElapsedMs = 0L,
                nowElapsedMs = 1_000L,
                carSpeedKmh = 67f,
            ),
        )
        assertFalse(
            MockJunkFixFilter.isCoordJump(
                loc = loc.copy(latitude = 55.00015),
                prevLat = 55.0,
                prevLon = 37.0,
                prevElapsedMs = 0L,
                nowElapsedMs = 1_000L,
                carSpeedKmh = 67f,
            ),
        )
    }

    @Test
    fun jumpCheckSkippedWhenGapOverTwoSeconds() {
        assertTrue(MockJunkFixFilter.isAcceptable(good(), carSpeedKmh = 67f, nowElapsedMs = 1_000L))
        val far = LocValues(
            locateStatus = true,
            latitude = 55.02,
            longitude = 37.0,
            altitude = 100.0,
            speed = 67f,
            hdop = 1.2f,
        )
        assertTrue(MockJunkFixFilter.isAcceptable(far, carSpeedKmh = 67f, nowElapsedMs = 4_000L))
    }
}
