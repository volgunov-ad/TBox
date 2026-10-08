package vad.dashing.mqtt.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationThrottleTest {
    @Test
    fun sendsAtMostEveryThirtySecondsAndFiftyMeters() {
        val throttle = LocationThrottle()
        val home = GeoPoint(55.75, 37.62)
        assertTrue(throttle.shouldSend(home, 0, force = false))
        throttle.markSent(home, 0)
        val nearby = GeoPoint(55.7501, 37.6201)
        assertFalse(throttle.shouldSend(nearby, 10_000, force = false))
        assertFalse(throttle.shouldSend(nearby, 31_000, force = false))
        val moved = GeoPoint(55.752, 37.62)
        assertTrue(distanceMeters(home, moved) >= 50.0)
        assertTrue(throttle.shouldSend(moved, 31_000, force = false))
        assertTrue(throttle.shouldSend(nearby, 5_000, force = true))
    }

    @Test
    fun configuredIntervalReplacesTheDefaultThirtySeconds() {
        val throttle = LocationThrottle()
        val home = GeoPoint(55.75, 37.62)
        throttle.markSent(home, 0)
        val moved = GeoPoint(55.752, 37.62)
        assertFalse(throttle.shouldSend(moved, 4_000, force = false, minIntervalMs = 5_000))
        assertTrue(throttle.shouldSend(moved, 5_000, force = false, minIntervalMs = 5_000))
    }
}
