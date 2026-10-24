package vad.dashing.tbox.location

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.esp.LocationSource
import vad.dashing.tbox.mbcan.LateralAccelerationDomain
import vad.dashing.tbox.phoneble.PhoneBleCodec
import vad.dashing.tbox.phoneble.PhoneSensorMath
import vad.dashing.tbox.vehicle.WheelCounters
import kotlin.math.cos
import kotlin.math.sin

class PhoneAndWheelHeadingTest {

    @Before
    fun setUp() {
        WheelHeadingCalibrationStore.reset()
        WheelHeadingIntegrator.reset()
        PhoneGyroCalibrationStore.reset()
        PhoneCompassCalibrationStore.reset()
        PhoneTelemetryRepository.resetForTest()
    }

    @After
    fun tearDown() {
        WheelHeadingCalibrationStore.reset()
        WheelHeadingIntegrator.reset()
        PhoneGyroCalibrationStore.reset()
        PhoneCompassCalibrationStore.reset()
        PhoneTelemetryRepository.resetForTest()
    }

    @Test
    fun phoneSensorMath_projectsYawOntoArbitraryGravityOrientation() {
        val g = PhoneSensorMath.STANDARD_GRAVITY_MPS2
        // Phone tilted 37° in holder around X axis; rotating 10 °/s around vertical
        val tiltRad = Math.toRadians(37.0)
        val gVec = PhoneSensorMath.Vec3(
            x = 0f,
            y = (g * sin(tiltRad)).toFloat(),
            z = (g * cos(tiltRad)).toFloat(),
        )
        val yawRadPerSec = Math.toRadians(10.0).toFloat()
        val gyroVec = PhoneSensorMath.Vec3(
            x = 0f,
            y = (yawRadPerSec * sin(tiltRad)).toFloat(),
            z = (yawRadPerSec * cos(tiltRad)).toFloat(),
        )
        val projected = PhoneSensorMath.projectYawRateDegPerSec(gyroVec, gVec)
        assertNotNull(projected)
        assertEquals(10.0f, projected!!, 0.05f)
    }

    @Test
    fun phoneSensorMath_tiltCompensatedMag_andRejectionGates() {
        val gVec = PhoneSensorMath.Vec3(0f, 0f, PhoneSensorMath.STANDARD_GRAVITY_MPS2)
        // Magnetic field pointing East (+X = 40 uT, Y = 0) → Device +Y points West (270°)
        val magEast = PhoneSensorMath.Vec3(40f, 0f, -20f)
        val res = PhoneSensorMath.computeTiltCompensatedMag(magEast, gVec)
        assertNotNull(res)
        assertEquals(270f, res!!.azimuthDeg, 0.5f)
        assertTrue(PhoneSensorMath.isMagSampleUsable(accuracy = 3, fieldStrengthUt = res.fieldStrengthUt, mountStable = true))
        assertFalse(PhoneSensorMath.isMagSampleUsable(accuracy = 1, fieldStrengthUt = res.fieldStrengthUt, mountStable = true))
        assertFalse(PhoneSensorMath.isMagSampleUsable(accuracy = 3, fieldStrengthUt = 120f, mountStable = true))
        assertFalse(PhoneSensorMath.isMagSampleUsable(accuracy = 3, fieldStrengthUt = res.fieldStrengthUt, mountStable = false))
    }

    @Test
    fun wheelHeadingIntegrator_leftAndRightTurns_andReverseSignFlip() {
        val k = 0.025f
        WheelHeadingIntegrator.onWheelSample(
            counters = WheelCounters(100, 100, 100, 100, 1000L),
            kMetersPerPulse = k,
            reverse = false,
            steerDeg = 90f,
            speedKmh = 30f,
            nowElapsedMs = 1000L,
        )
        // Left turn forward: right rear wheel (RHR) travels more than left rear wheel (LHR)
        // → nav bearing decreases (negative delta)
        WheelHeadingIntegrator.onWheelSample(
            counters = WheelCounters(120, 124, 120, 124, 1500L),
            kMetersPerPulse = k,
            reverse = false,
            steerDeg = 90f,
            speedKmh = 30f,
            nowElapsedMs = 1500L,
        )
        val leftDelta = WheelHeadingIntegrator.consumeDeltaDeg()
        assertTrue("Expected negative nav bearing delta on left turn, got $leftDelta", leftDelta < -1.0f)

        // Same counter difference in reverse → opposite nav bearing delta
        WheelHeadingIntegrator.onWheelSample(
            counters = WheelCounters(140, 148, 140, 148, 2000L),
            kMetersPerPulse = k,
            reverse = true,
            steerDeg = 90f,
            speedKmh = 10f,
            nowElapsedMs = 2000L,
        )
        val revDelta = WheelHeadingIntegrator.consumeDeltaDeg()
        assertTrue("Expected positive nav bearing delta in reverse, got $revDelta", revDelta > 1.0f)
    }

    @Test
    fun wheelHeadingIntegrator_rejectsFrontAxleSlip() {
        val k = 0.025f
        WheelHeadingIntegrator.onWheelSample(
            counters = WheelCounters(100, 100, 100, 100, 1000L),
            kMetersPerPulse = k,
            reverse = false,
            steerDeg = 0f,
            speedKmh = 25f,
            nowElapsedMs = 1000L,
        )
        // Front left wheel spins wildly (dLhf=40, dRhf=20) while rear is straight (dLhr=20, dRhr=20)
        WheelHeadingIntegrator.onWheelSample(
            counters = WheelCounters(140, 120, 120, 120, 1500L),
            kMetersPerPulse = k,
            reverse = false,
            steerDeg = 0f,
            speedKmh = 25f,
            nowElapsedMs = 1500L,
        )
        assertTrue(WheelHeadingIntegrator.peekDebugSnapshot().slipRejected)
        assertEquals(0f, WheelHeadingIntegrator.consumeDeltaDeg(), 1e-4f)
    }

    @Test
    fun wheelHeadingIntegrator_discardThroughDoesNotKeepSilentWheelsFresh() {
        WheelHeadingIntegrator.onWheelSample(
            counters = WheelCounters(100, 100, 100, 100, 1000L),
            kMetersPerPulse = 0.025f,
            reverse = false,
            steerDeg = 0f,
            speedKmh = 30f,
            nowElapsedMs = 1000L,
        )
        var now = 1000L
        repeat(10) {
            now += 500L
            WheelHeadingIntegrator.discardThrough(now)
        }
        assertFalse(WheelHeadingIntegrator.isFresh(now))
        assertEquals(1000L, WheelHeadingIntegrator.lastSampleElapsedMs())
    }

    @Test
    fun wheelHeadingIntegrator_rebasesAfterSilentGapInsteadOfJumping() {
        val k = 0.025f
        WheelHeadingIntegrator.onWheelSample(
            counters = WheelCounters(100, 100, 100, 100, 1000L),
            kMetersPerPulse = k,
            reverse = false,
            steerDeg = 90f,
            speedKmh = 30f,
            nowElapsedMs = 1000L,
        )
        // Collection stopped for a minute; counters drifted with a large L/R mismatch.
        WheelHeadingIntegrator.onWheelSample(
            counters = WheelCounters(1100, 1400, 1100, 1400, 61_000L),
            kMetersPerPulse = k,
            reverse = false,
            steerDeg = 90f,
            speedKmh = 30f,
            nowElapsedMs = 61_000L,
        )
        assertEquals(0f, WheelHeadingIntegrator.consumeDeltaDeg(), 1e-4f)
        assertTrue(WheelHeadingIntegrator.isFresh(61_000L))

        WheelHeadingIntegrator.onWheelSample(
            counters = WheelCounters(1120, 1424, 1120, 1424, 61_500L),
            kMetersPerPulse = k,
            reverse = false,
            steerDeg = 90f,
            speedKmh = 30f,
            nowElapsedMs = 61_500L,
        )
        assertTrue(WheelHeadingIntegrator.consumeDeltaDeg() < -1.0f)
    }

    @Test
    fun hybridWheelsSteer_zeroWheelTickDoesNotOverweightSteer() {
        val steer = 2f
        val atFullSpeed = WheelHeadingIntegrator.hybridWheelsSteerDelta(
            wheelsDeltaDeg = 0f,
            steerDeltaDeg = steer,
            speedKmh = 60f,
            slipRejected = false,
            yasTrustWeight = 1f,
        )
        assertEquals(0f, atFullSpeed, 1e-4f)
        val halfTrust = WheelHeadingIntegrator.hybridWheelsSteerDelta(
            wheelsDeltaDeg = 0f,
            steerDeltaDeg = steer,
            speedKmh = 60f,
            slipRejected = false,
            yasTrustWeight = 0.5f,
        )
        assertEquals(1f, halfTrust, 1e-4f)
        val lowSpeed = WheelHeadingIntegrator.hybridWheelsSteerDelta(
            wheelsDeltaDeg = 0f,
            steerDeltaDeg = steer,
            speedKmh = 3f,
            slipRejected = false,
            yasTrustWeight = 1f,
        )
        assertEquals(steer, lowSpeed, 1e-4f)
    }

    @Test
    fun lateralAccelerationDomain_decodesMbCanAndVhal() {
        assertEquals(1.25f, LateralAccelerationDomain.decodeMbCanFloat(1.25f)!!, 1e-4f)
        assertEquals(0.0f, LateralAccelerationDomain.decodeVhalRaw(2000)!!, 1e-3f)
        assertEquals(0.5f, LateralAccelerationDomain.decodeVhalRaw(2500)!!, 1e-3f)
        assertNull(LateralAccelerationDomain.decodeVhalRaw(0xFFFF))
    }

    @Test
    fun phoneTelemetryRepository_filtersSecondPhoneAndResetsMountOffsetOnMove() {
        val loc1 = PhoneBleCodec.PhoneLocation(
            gen = 1,
            latitude = 55.75,
            longitude = 37.61,
            horizontalAccuracyM = 4f,
            altitudeM = 120f,
            speedKmh = 40f,
            bearingDeg = 100f,
            usedSatellites = 10,
            visibleSatellites = 14,
            fixAgeMs = 50,
        )
        PhoneTelemetryRepository.onPhoneLocation(
            idHex = "11223344",
            name = "DriverPhone",
            loc = loc1,
            activeLocationSource = LocationSource.PHONE,
            nowElapsedMs = 1000L,
            nowEpochMs = 1_700_000_000_000L,
        )
        assertEquals("11223344", PhoneTelemetryRepository.uiState.value.activePhoneIdHex)

        // Second passenger phone is ignored while DriverPhone is active
        val loc2 = loc1.copy(latitude = 59.93, longitude = 30.33)
        PhoneTelemetryRepository.onPhoneLocation(
            idHex = "aabbccdd",
            name = "PassengerPhone",
            loc = loc2,
            activeLocationSource = LocationSource.PHONE,
            nowElapsedMs = 1200L,
            nowEpochMs = 1_700_000_000_200L,
        )
        assertEquals(55.75, PhoneTelemetryRepository.uiState.value.lastLocation!!.latitude, 1e-4)

        // Feed valid MAG sample + straight forward GNSS to seed session mountYawOffset
        PhoneTelemetryRepository.onPhoneMag(
            idHex = "11223344",
            name = "DriverPhone",
            sample = PhoneBleCodec.PhoneMagSample(
                magneticAzimuthDeg = 80f,
                fieldStrengthUt = 45f,
                accuracy = 3,
                pitchDeg = 0,
                rollDeg = 0,
                monoLowMs = 1500,
                mountMoved = false,
            ),
            steerDeg = 0f,
            wheelDeltaDeg = 0f,
            nowElapsedMs = 1500L,
        )
        // Reverse motion must NOT calibrate mount offset
        PhoneTelemetryRepository.onGnssReference(
            gnssCourseDeg = 100f,
            lat = 55.75,
            lon = 37.61,
            altM = 120f,
            speedKmh = 25f,
            steerDeg = 0f,
            reverse = true,
            gnssTruthful = true,
            nowElapsedMs = 1600L,
            nowEpochMs = 1_700_000_000_600L,
        )
        assertNull(PhoneTelemetryRepository.uiState.value.mountYawOffsetDeg)

        // Forward motion seeds mount offset
        PhoneTelemetryRepository.onGnssReference(
            gnssCourseDeg = 100f,
            lat = 55.75,
            lon = 37.61,
            altM = 120f,
            speedKmh = 36f,
            steerDeg = 0f,
            reverse = false,
            gnssTruthful = true,
            nowElapsedMs = 1700L,
            nowEpochMs = 1_700_000_000_700L,
        )
        assertNotNull(PhoneTelemetryRepository.uiState.value.mountYawOffsetDeg)

        // Moving phone in holder immediately resets mountYawOffset
        PhoneTelemetryRepository.onPhoneYaw(
            idHex = "11223344",
            name = "DriverPhone",
            sample = PhoneBleCodec.PhoneYawSample(
                dtMs = 150,
                dPsiDeg = 5f,
                monoLowMs = 1900,
                mountStable = false,
                mountMoved = true,
                gravDeltaMg = 250,
            ),
            nowElapsedMs = 1900L,
        )
        assertNull(PhoneTelemetryRepository.uiState.value.mountYawOffsetDeg)
        assertFalse(PhoneTelemetryRepository.isCompassUsable(1900L))
    }
}
