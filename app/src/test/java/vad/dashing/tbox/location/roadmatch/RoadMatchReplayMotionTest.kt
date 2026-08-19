package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RoadMatchReplayMotionTest {

    @Test
    fun parseModeAliases() {
        assertEquals(RoadMatchReplayMotion.Mode.DELTA, RoadMatchReplayMotion.parseMode(null))
        assertEquals(RoadMatchReplayMotion.Mode.STRIP, RoadMatchReplayMotion.parseMode("strip"))
        assertEquals(RoadMatchReplayMotion.Mode.DR, RoadMatchReplayMotion.parseMode("gyro"))
        assertEquals(RoadMatchReplayMotion.Mode.DR, RoadMatchReplayMotion.parseMode("dr"))
    }

    @Test
    fun resolveCalibPrefersOverrides() {
        val c = RoadMatchReplayMotion.resolveCalib(
            loggedYawScale = 1.2f,
            loggedYawSign = 1f,
            loggedSpeedScale = 1.006f,
            overrideYawScale = 1.05f,
            overrideYawSign = -1f,
            overrideSpeedScale = 1.0f,
        )
        assertEquals(1.05f, c.yawScale, 1e-4f)
        assertEquals(-1f, c.yawSign, 1e-4f)
        assertEquals(1.0f, c.speedScale, 1e-4f)
    }

    @Test
    fun drAppliesYawAndSpeedScale() {
        val from = RoadMatchPose(55.75, 37.60, 90f)
        val calib = RoadMatchReplayMotion.Calib(yawScale = 1.1f, yawSign = 1f, speedScale = 1.0f)
        val next = RoadMatchReplayMotion.step(
            mode = RoadMatchReplayMotion.Mode.DR,
            from = from,
            dDistM = 10.0,
            dYawDebDeg = 10f,
            calib = calib,
            loggedYawDeltaDeg = 99f,
            loggedBearingDeltaDeg = 0f,
            fallbackPathM = 10.0,
        )
        assertEquals(101f, next.bearingDeg, 0.05f)
        val moved = RoadGraph.haversineM(from.lat, from.lon, next.lat, next.lon)
        assertEquals(10.0, moved, 0.3)
    }

    @Test
    fun stripRemovesLoggedMatchYaw() {
        val from = RoadMatchPose(55.75, 37.60, 90f)
        val calib = RoadMatchReplayMotion.Calib(1f, 1f, 1f)
        val next = RoadMatchReplayMotion.step(
            mode = RoadMatchReplayMotion.Mode.STRIP,
            from = from,
            dDistM = 5.0,
            dYawDebDeg = 50f,
            calib = calib,
            loggedYawDeltaDeg = 14f,
            loggedBearingDeltaDeg = 4f,
            fallbackPathM = 5.0,
        )
        // 90 + (14-4) = 100; gyro aggregate ignored in strip.
        assertEquals(100f, next.bearingDeg, 0.05f)
    }

    @Test
    fun lowerYawScaleTurnsLess() {
        val from = RoadMatchPose(55.75, 37.60, 0f)
        val hot = RoadMatchReplayMotion.step(
            mode = RoadMatchReplayMotion.Mode.DR,
            from = from,
            dDistM = 20.0,
            dYawDebDeg = 20f,
            calib = RoadMatchReplayMotion.Calib(1.2f, 1f, 1f),
            loggedYawDeltaDeg = 0f,
            loggedBearingDeltaDeg = 0f,
            fallbackPathM = 20.0,
        )
        val cool = RoadMatchReplayMotion.step(
            mode = RoadMatchReplayMotion.Mode.DR,
            from = from,
            dDistM = 20.0,
            dYawDebDeg = 20f,
            calib = RoadMatchReplayMotion.Calib(1.0f, 1f, 1f),
            loggedYawDeltaDeg = 0f,
            loggedBearingDeltaDeg = 0f,
            fallbackPathM = 20.0,
        )
        assertTrue(abs(hot.bearingDeg - cool.bearingDeg) > 3f)
        assertEquals(24f, hot.bearingDeg, 0.05f)
        assertEquals(20f, cool.bearingDeg, 0.05f)
    }
}
