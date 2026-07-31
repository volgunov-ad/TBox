package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.esp.LocationSource

class MockLocationJobTest {

    @Test
    fun doesNotPushWhenMockDisabled() {
        assertFalse(MockLocationJob.shouldPushMock(false, LocationSource.TBOX))
        assertFalse(MockLocationJob.shouldPushMock(false, LocationSource.ESP32))
    }

    @Test
    fun doesNotPushWhenAndroidSource() {
        assertFalse(MockLocationJob.shouldPushMock(true, LocationSource.ANDROID))
    }

    @Test
    fun pushesWhenMockEnabledAndNotAndroid() {
        assertTrue(MockLocationJob.shouldPushMock(true, LocationSource.TBOX))
        assertTrue(MockLocationJob.shouldPushMock(true, LocationSource.ESP32))
        assertTrue(MockLocationJob.shouldPushMock(true, LocationSource.USB))
    }

    @Test
    fun hasValidCoordinates_rejectsZeroZero() {
        assertFalse(MockLocationJob.hasValidCoordinates(LocValues()))
        assertTrue(
            MockLocationJob.hasValidCoordinates(
                LocValues(latitude = 55.0, longitude = 37.0, locateStatus = true),
            ),
        )
    }

    @Test
    fun fixRetentionIsTwoMinutes() {
        assertTrue(MockLocationJob.FIX_RETENTION_MS == 120_000L)
    }

    @Test
    fun extrapolateMovesNorth() {
        val (lat, lon) = MockLocationJob.extrapolateLatLon(
            lat = 55.0,
            lon = 37.0,
            bearingDeg = 0f,
            distanceM = 111.32,
        )
        assertTrue(lat > 55.0)
        assertEquals(37.0, lon, 1e-5)
        assertEquals(55.001, lat, 1e-4)
    }

    @Test
    fun extrapolateZeroDistanceKeepsPoint() {
        val (lat, lon) = MockLocationJob.extrapolateLatLon(55.75, 37.62, 90f, 0.0)
        assertEquals(55.75, lat, 0.0)
        assertEquals(37.62, lon, 0.0)
    }

    @Test
    fun resolveBearingPrefersCurrentNonZero() {
        assertEquals(
            84f,
            MockLocationJob.resolveBearingForExtrapolation(84f, 12f),
        )
    }

    @Test
    fun resolveBearingFallsBackToLastKnownWhenCurrentZero() {
        assertEquals(
            120f,
            MockLocationJob.resolveBearingForExtrapolation(0f, 120f),
        )
    }

    @Test
    fun resolveBearingNullWhenNoUsableHeading() {
        assertEquals(null, MockLocationJob.resolveBearingForExtrapolation(0f, null))
        assertEquals(null, MockLocationJob.resolveBearingForExtrapolation(0f, 0f))
    }
}
