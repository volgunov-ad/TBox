package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues

class GeoDisplaySourcePassthroughTest {

    private fun point(
        speed: Float = 60f,
        altitude: Double = 100.0,
        satsVis: Int = 12,
        satsUsed: Int = 10,
    ) = LocValues(
        locateStatus = true,
        latitude = 55.0,
        longitude = 37.0,
        altitude = altitude,
        speed = speed,
        trueDirection = 90f,
        visibleSatellites = satsVis,
        usingSatellites = satsUsed,
    )

    @Test
    fun usableUpdatesLastAndState() {
        val live = point()
        val r = GeoDisplaySourcePassthrough.next(
            live = live,
            liveUsable = true,
            junkFilterOn = true,
            lastUsable = null,
        )
        assertTrue(r.state.liveUsable)
        assertEquals(60f, r.state.speedKmh, 0f)
        assertEquals(live, r.lastUsable)
    }

    @Test
    fun junkKeepsLastUsableSpeedAndBearing() {
        val good = point(speed = 50f)
        val junk = point(speed = 80f, altitude = 12_000.0, satsVis = 18, satsUsed = 8)
        val r = GeoDisplaySourcePassthrough.next(
            live = junk,
            liveUsable = false,
            junkFilterOn = true,
            lastUsable = good,
        )
        assertFalse(r.state.liveUsable)
        assertEquals(50f, r.state.speedKmh, 0f)
        assertEquals(90f, r.state.bearingDeg)
        assertEquals(18, r.state.visibleSatellites)
        assertEquals(8, r.state.usingSatellites)
        assertEquals(good, r.lastUsable)
    }

    @Test
    fun noJunkFilterShowsLiveEvenIfUnusable() {
        val live = point(speed = 12f)
        val r = GeoDisplaySourcePassthrough.next(
            live = live,
            liveUsable = false,
            junkFilterOn = false,
            lastUsable = point(speed = 50f),
        )
        assertEquals(12f, r.state.speedKmh, 0f)
    }
}
