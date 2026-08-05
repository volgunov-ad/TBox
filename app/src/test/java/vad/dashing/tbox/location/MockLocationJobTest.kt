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
    fun fixRetentionIsTenMinutes() {
        assertTrue(MockLocationJob.FIX_RETENTION_MS == 600_000L)
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
    fun shouldApplyReverse_requiresSettingAndEnhanceMode() {
        assertFalse(MockLocationJob.shouldApplyReverse(MockCanSpeedMode.NONE, true))
        assertFalse(MockLocationJob.shouldApplyReverse(MockCanSpeedMode.ALWAYS, false))
        assertFalse(MockLocationJob.shouldApplyReverse(MockCanSpeedMode.WHEN_FIX_LOST, false))
        assertFalse(MockLocationJob.shouldApplyReverse(MockCanSpeedMode.CONSTANT, false))
    }

    @Test
    fun resolveBearingNullWhenNoUsableHeading() {
        assertEquals(null, MockLocationJob.resolveBearingForExtrapolation(0f, null))
        assertEquals(null, MockLocationJob.resolveBearingForExtrapolation(0f, 0f))
    }

    @Test
    fun integrateYawLeftDecreasesNavBearing() {
        // Facing east (90°); left yaw (+) → toward north → bearing decreases.
        val next = MockLocationJob.integrateYawIntoBearing(
            bearingDeg = 90f,
            yawRateDegPerSec = 10f,
            dtSec = 0.2,
        )
        assertEquals(88f, next, 1e-3f)
    }

    @Test
    fun integrateYawRightIncreasesNavBearing() {
        val next = MockLocationJob.integrateYawIntoBearing(
            bearingDeg = 90f,
            yawRateDegPerSec = -10f,
            dtSec = 0.2,
        )
        assertEquals(92f, next, 1e-3f)
    }

    @Test
    fun integrateYawWraps() {
        val next = MockLocationJob.integrateYawIntoBearing(
            bearingDeg = 5f,
            yawRateDegPerSec = 20f,
            dtSec = 0.5, // capped to 0.25 → −5°
        )
        assertEquals(0f, next, 1e-3f)
        val wrapped = MockLocationJob.integrateYawIntoBearing(
            bearingDeg = 2f,
            yawRateDegPerSec = 20f,
            dtSec = 0.25,
        )
        assertEquals(357f, wrapped, 1e-3f)
    }

    @Test
    fun integrateYawCapsDt() {
        // 10 °/s for 1 s would be −10°, but dt capped at 0.25 → −2.5°.
        val next = MockLocationJob.integrateYawIntoBearing(
            bearingDeg = 100f,
            yawRateDegPerSec = 10f,
            dtSec = 1.0,
        )
        assertEquals(97.5f, next, 1e-3f)
    }

    @Test
    fun integrateYawRejectsAbsurdRate() {
        val next = MockLocationJob.integrateYawIntoBearing(
            bearingDeg = 100f,
            yawRateDegPerSec = 200f,
            dtSec = 0.2,
        )
        assertEquals(100f, next, 0f)
    }

    @Test
    fun shouldAcceptGnssCourseRequiresMotion() {
        assertTrue(MockLocationJob.shouldAcceptGnssCourse(10f, 90f))
        assertFalse(MockLocationJob.shouldAcceptGnssCourse(0.5f, 90f))
        assertFalse(MockLocationJob.shouldAcceptGnssCourse(10f, 0f))
    }

    @Test
    fun shouldAcceptGnssCourseCanFirstIgnoresPhantomGnssSpeed() {
        // CAN says stopped → reject even if GNSS reports noisy speed + spinning COG.
        assertFalse(
            MockLocationJob.shouldAcceptGnssCourse(
                canKmh = 0f,
                gnssSpeedKmh = 5f,
                courseDeg = 123f,
            ),
        )
        assertFalse(
            MockLocationJob.shouldAcceptGnssCourse(
                canKmh = 1.0f,
                gnssSpeedKmh = 40f,
                courseDeg = 90f,
            ),
        )
        // No CAN: GNSS below hold threshold.
        assertFalse(
            MockLocationJob.shouldAcceptGnssCourse(
                canKmh = null,
                gnssSpeedKmh = 0.5f,
                courseDeg = 90f,
            ),
        )
        // No CAN: GNSS moving + non-zero course.
        assertTrue(
            MockLocationJob.shouldAcceptGnssCourse(
                canKmh = null,
                gnssSpeedKmh = 10f,
                courseDeg = 90f,
            ),
        )
        // CAN moving wins.
        assertTrue(
            MockLocationJob.shouldAcceptGnssCourse(
                canKmh = 30f,
                gnssSpeedKmh = 0f,
                courseDeg = 45f,
            ),
        )
    }

    @Test
    fun applyYawDeadbandZerosNoise() {
        assertEquals(null, MockLocationJob.applyYawDeadband(0.3f))
        assertEquals(null, MockLocationJob.applyYawDeadband(0.49f))
        assertEquals(0.5f, MockLocationJob.applyYawDeadband(0.5f)!!, 1e-3f)
        assertEquals(2f, MockLocationJob.applyYawDeadband(2f))
    }

    @Test
    fun formatSatellitesCollapsesWhenEqual() {
        assertEquals("12", MockLocationJob.formatSatellites(12, 12))
        assertEquals("18/12", MockLocationJob.formatSatellites(18, 12))
    }

    @Test
    fun isJunkLiveRequiresDetectionAndFailingSanity() {
        val junk = LocValues(
            locateStatus = true,
            latitude = 55.0,
            longitude = 37.0,
            altitude = 12_000.0,
            speed = 60f,
        )
        assertFalse(MockLocationJob.isJunkLive(junk, junkFilterOn = false, liveUsable = false))
        assertTrue(MockLocationJob.isJunkLive(junk, junkFilterOn = true, liveUsable = false))
        assertFalse(
            MockLocationJob.isJunkLive(
                LocValues(locateStatus = false, latitude = 55.0, longitude = 37.0),
                junkFilterOn = true,
                liveUsable = false,
            ),
        )
    }

    @Test
    fun constantModeIsEnhancementAndIsolatedFromAlwaysAliases() {
        assertTrue(MockCanSpeedMode.CONSTANT.enhancesMock)
        assertTrue(MockCanSpeedMode.CONSTANT.isConstantCalc)
        assertFalse(MockCanSpeedMode.ALWAYS.isConstantCalc)
        assertFalse(MockCanSpeedMode.WHEN_FIX_LOST.isConstantCalc)
        assertEquals(MockCanSpeedMode.CONSTANT, MockCanSpeedMode.fromStorage("CONSTANT"))
        assertEquals(MockCanSpeedMode.CONSTANT, MockCanSpeedMode.fromStorage("CONTINUOUS"))
        assertEquals(MockCanSpeedMode.ALWAYS, MockCanSpeedMode.fromStorage("ALWAYS"))
    }

    @Test
    fun coldStartAllowsConstantMode() {
        assertTrue(MockLastGoodFix.canUseForColdStart(MockCanSpeedMode.CONSTANT))
        assertTrue(MockLastGoodFix.canUseForColdStart(MockCanSpeedMode.ALWAYS))
        assertFalse(MockLastGoodFix.canUseForColdStart(MockCanSpeedMode.NONE))
    }

    @Test
    fun courseHoldMinMatchesHalfMeterPerSec() {
        assertEquals(1.8f, MockLocationJob.COURSE_HOLD_MIN_KMH, 1e-3f)
        assertEquals(0.5f, MockLocationJob.YAW_DEADBAND_DEG_PER_SEC, 1e-3f)
    }
}
