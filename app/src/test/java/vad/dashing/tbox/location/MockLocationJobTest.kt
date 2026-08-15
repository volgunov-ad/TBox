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
        assertTrue(
            MockLocationJob.shouldPushMock(MockPowerState.WHEN_NO_FIX, LocationSource.TBOX),
        )
        assertFalse(
            MockLocationJob.shouldPushMock(MockPowerState.OFF, LocationSource.TBOX),
        )
    }

    @Test
    fun injectCadenceIsIndependentOfInnerDrTick() {
        assertEquals(500L, MockLocationJob.INNER_CALC_MS)
        assertTrue(MockLocationJob.isInjectDue(0L, lastInjectElapsedMs = 0L, periodMs = 1_000L))
        assertTrue(MockLocationJob.isInjectDue(100L, lastInjectElapsedMs = 0L, periodMs = 5_000L))
        assertFalse(MockLocationJob.isInjectDue(1_999L, lastInjectElapsedMs = 1_000L, periodMs = 1_000L))
        assertTrue(MockLocationJob.isInjectDue(2_000L, lastInjectElapsedMs = 1_000L, periodMs = 1_000L))
        assertFalse(MockLocationJob.isInjectDue(1_400L, lastInjectElapsedMs = 1_000L, periodMs = 500L))
        assertTrue(MockLocationJob.isInjectDue(1_500L, lastInjectElapsedMs = 1_000L, periodMs = 500L))
        // Period below the inner tick still injects at most once per inner tick.
        assertFalse(MockLocationJob.isInjectDue(1_400L, lastInjectElapsedMs = 1_000L, periodMs = 200L))
        assertTrue(MockLocationJob.isInjectDue(1_500L, lastInjectElapsedMs = 1_000L, periodMs = 200L))
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
        // Held true north (0°) is valid — only null means “no heading”.
        assertEquals(
            0f,
            MockLocationJob.resolveBearingForExtrapolation(0f, 0f),
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
    fun whenNoFix_effectiveConstantEnablesReverseGate() {
        // Stored Direct/NONE under WHEN_NO_FIX must not suppress reverse for steer/DR:
        // signedSteerSpeedKmh uses effectiveCanSpeedMode, not the persisted mode.
        val effective = MockPowerState.WHEN_NO_FIX.effectiveCanSpeedMode(MockCanSpeedMode.NONE)
        assertEquals(MockCanSpeedMode.CONSTANT, effective)
        assertTrue(effective.enhancesMock)
        assertFalse(MockCanSpeedMode.NONE.enhancesMock)
    }

    @Test
    fun resolveBearingNullWhenNoUsableHeading() {
        assertEquals(null, MockLocationJob.resolveBearingForExtrapolation(0f, null))
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
    fun integrateYawCapsPerSampleDtNotMockPeriod() {
        // Helper still clamps a single dt to MAX_SAMPLE_DT (0.25 s) — for one step only.
        // Full turns use YawIntegrator across many samples (see YawIntegratorTest).
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
    fun headingCannotChangeWithoutTravel() {
        assertEquals(
            100f,
            MockLocationJob.constrainHeadingToTravel(
                bearingBeforeDeg = 100f,
                proposedBearingDeg = 170f,
                distanceM = 0.0,
            ),
            0f,
        )
        assertEquals(
            100f,
            MockLocationJob.constrainHeadingToTravel(
                bearingBeforeDeg = 100f,
                proposedBearingDeg = 30f,
                distanceM = -1.0,
            ),
            0f,
        )
    }

    @Test
    fun headingChangeIsLimitedByPhysicalTurnForTravel() {
        val distanceM = 0.10
        val maxDelta = Math.toDegrees(
            distanceM / SteerHeadingIntegrator.DEFAULT_WHEELBASE_M *
                kotlin.math.tan(Math.toRadians(SteerHeadingIntegrator.MAX_ROAD_WHEEL_DEG.toDouble())),
        ).toFloat()
        assertEquals(
            MockLocationJob.wrapBearingDeg(350f + maxDelta),
            MockLocationJob.constrainHeadingToTravel(
                bearingBeforeDeg = 350f,
                proposedBearingDeg = 90f,
                distanceM = distanceM,
            ),
            1e-3f,
        )
        assertEquals(
            MockLocationJob.wrapBearingDeg(10f - maxDelta),
            MockLocationJob.constrainHeadingToTravel(
                bearingBeforeDeg = 10f,
                proposedBearingDeg = 270f,
                distanceM = distanceM,
            ),
            1e-3f,
        )
        // A normal small heading change with enough travelled distance passes unchanged.
        assertEquals(
            92f,
            MockLocationJob.constrainHeadingToTravel(
                bearingBeforeDeg = 90f,
                proposedBearingDeg = 92f,
                distanceM = 5.0,
            ),
            1e-3f,
        )
    }

    @Test
    fun gnssCannotRotateExistingHeadingWithoutTravel() {
        assertEquals(0f, MockLocationJob.gnssCourseScaleForTravel(20f, 0.0), 0f)
        assertEquals(0f, MockLocationJob.gnssCourseScaleForTravel(20f, Double.NaN), 0f)
        assertEquals(1f, MockLocationJob.gnssCourseScaleForTravel(20f, 1.0), 0f)
        assertEquals(0.3f, MockLocationJob.gnssCourseScaleForTravel(1f, 0.2), 0f)
        // Hard-resync / far recovery may catch up course without travel this tick.
        assertEquals(
            1f,
            MockLocationJob.gnssCourseScaleForTravel(20f, 0.0, allowWithoutTravel = true),
            0f,
        )
        assertEquals(
            0f,
            MockLocationJob.gnssCourseScaleForTravel(0.1f, 0.0, allowWithoutTravel = true),
            0f,
        )
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
    fun formatSatellitesAlwaysShowsVisibleOverUsing() {
        assertEquals("12/12", MockLocationJob.formatSatellites(12, 12))
        assertEquals("18/12", MockLocationJob.formatSatellites(18, 12))
        assertEquals("0/0", MockLocationJob.formatSatellites(0, 0))
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
    fun constantAcceptsLiveGnssFollowsJunkToggle() {
        assertTrue(
            MockLocationJob.constantAcceptsLiveGnss(
                junkFilterOn = true,
                gnssTruthful = true,
                gnssFixPresent = true,
            ),
        )
        assertFalse(
            MockLocationJob.constantAcceptsLiveGnss(
                junkFilterOn = true,
                gnssTruthful = false,
                gnssFixPresent = true,
            ),
        )
        assertTrue(
            MockLocationJob.constantAcceptsLiveGnss(
                junkFilterOn = false,
                gnssTruthful = false,
                gnssFixPresent = true,
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
    fun matcherIgnoresHeldHeadingWhileLiveGnssHasNoCourse() {
        assertFalse(
            MockLocationJob.shouldFeedHeadingToMatcher(
                gnssPresent = true,
                gnssCourseDeg = 0f,
            ),
        )
        assertTrue(
            MockLocationJob.shouldFeedHeadingToMatcher(
                gnssPresent = true,
                gnssCourseDeg = 174f,
            ),
        )
        assertTrue(
            MockLocationJob.shouldFeedHeadingToMatcher(
                gnssPresent = false,
                gnssCourseDeg = 0f,
            ),
        )
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
        assertEquals(
            SteerHeadingIntegrator.MIN_SPEED_MPS * 3.6f,
            MockLocationJob.CRAWL_DR_MIN_KMH,
            1e-3f,
        )
    }

    @Test
    fun classifyDrMotionKeepsGnssHoldButAllowsCrawlWithMetres() {
        assertEquals(
            DrMotionGate.STEP,
            MockLocationJob.classifyDrMotion(20f, 0.0, 0.5),
        )
        assertEquals(
            DrMotionGate.DISCARD,
            MockLocationJob.classifyDrMotion(0f, 0.0, 0.5),
        )
        assertEquals(
            DrMotionGate.DISCARD,
            MockLocationJob.classifyDrMotion(0.5f, 0.0, 0.5),
        )
        assertEquals(
            DrMotionGate.STEP,
            MockLocationJob.classifyDrMotion(0.5f, 1.2, 0.5),
        )
        assertEquals(
            DrMotionGate.HOLD_CRAWL,
            MockLocationJob.classifyDrMotion(1.1f, 0.15, 0.5),
        )
        assertEquals(
            DrMotionGate.STEP,
            MockLocationJob.classifyDrMotion(
                1.1f,
                MockLocationJob.CRAWL_DR_MIN_DISTANCE_M,
                0.5,
            ),
        )
        assertEquals(
            DrMotionGate.DISCARD,
            MockLocationJob.classifyDrMotion(1.1f, 0.5, 0.0),
        )
        assertTrue(
            MockLocationJob.shouldAcceptGnssCourse(10f, 90f),
        )
        assertFalse(
            MockLocationJob.shouldAcceptGnssCourse(1.1f, 90f),
        )
    }

    @Test
    fun averageBearingDegHalfTurn() {
        assertEquals(45f, MockLocationJob.averageBearingDeg(0f, 90f), 1e-3f)
        assertEquals(0f, MockLocationJob.averageBearingDeg(350f, 10f), 1e-3f)
        assertEquals(180f, MockLocationJob.averageBearingDeg(90f, 270f), 1e-3f)
    }

    @Test
    fun averageBearingDegUnchangedWhenEqual() {
        assertEquals(123f, MockLocationJob.averageBearingDeg(123f, 123f), 1e-3f)
    }

    @Test
    fun hybridUsesSteerWhenGyroSilent() {
        assertEquals(4f, MockLocationJob.hybridGyroSteerDelta(0f, 4f), 0f)
        assertEquals(-3f, MockLocationJob.hybridGyroSteerDelta(0f, -3f), 0f)
        assertEquals(2f, MockLocationJob.hybridGyroSteerDelta(2f, 0f), 0f)
        assertEquals(0f, MockLocationJob.hybridGyroSteerDelta(0f, 0f), 0f)
    }

    @Test
    fun hybridUsesSteerWhenGyroQuietAndSteerClear() {
        assertEquals(
            2.5f,
            MockLocationJob.hybridGyroSteerDelta(0.10f, 2.5f),
            0f,
        )
    }

    @Test
    fun hybridKeepsGyroOnOppositeSign() {
        assertEquals(8f, MockLocationJob.hybridGyroSteerDelta(8f, -12f), 0f)
        assertEquals(-8f, MockLocationJob.hybridGyroSteerDelta(-8f, 12f), 0f)
    }

    @Test
    fun hybridKeepsGyroWhenSteerDoesNotLead() {
        assertEquals(10f, MockLocationJob.hybridGyroSteerDelta(10f, 10.4f), 0f)
        assertEquals(10f, MockLocationJob.hybridGyroSteerDelta(10f, 8f), 0f)
    }

    @Test
    fun hybridPullsTowardLeadingSteerWithoutReplacing() {
        // 124442-style 1 s tick: gyro ~9.5°, calibrated steer ~14°.
        val g = 9.5f
        val s = 14f
        val blended = MockLocationJob.hybridGyroSteerDelta(g, s)
        val extra = ((s - g) * MockLocationJob.HYBRID_STEER_CATCHUP_BLEND)
            .coerceAtMost(MockLocationJob.HYBRID_STEER_CATCHUP_MAX_DEG)
        assertEquals(g + extra, blended, 1e-3f)
        assertTrue(blended > g)
        assertTrue(blended < s)
        assertEquals(
            -g - extra,
            MockLocationJob.hybridGyroSteerDelta(-g, -s),
            1e-3f,
        )
    }

    @Test
    fun hybridSteerCatchupDoesNotPassSteerOrSum() {
        val g = 6f
        val s = 20f
        val blended = MockLocationJob.hybridGyroSteerDelta(g, s)
        assertTrue(blended <= s)
        assertTrue(blended < g + s)
        assertEquals(
            g + MockLocationJob.HYBRID_STEER_CATCHUP_MAX_DEG,
            blended,
            1e-3f,
        )
    }
}
