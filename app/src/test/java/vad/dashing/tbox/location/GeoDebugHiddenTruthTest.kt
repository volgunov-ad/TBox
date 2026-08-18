package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoDebugHiddenTruthTest {

    @Test
    fun parseRmcAcceptsActiveGnRmc() {
        val fix = GeoDebugHiddenTruth.parseRmc(
            "nmea|\$GNRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A",
        )
        requireNotNull(fix)
        assertEquals(48.1173, fix.lat, 1e-4)
        assertEquals(11.516666, fix.lon, 1e-4)
        assertEquals(84.4f, fix.courseDeg!!, 1e-3f)
        assertEquals("nmea", fix.src)
        assertEquals(0L, fix.ageMs)
    }

    @Test
    fun parseRmcRejectsVoidAndZeroCoords() {
        assertNull(GeoDebugHiddenTruth.parseRmc("\$GNRMC,030152.83,V,,,,,,,101225,,,N*4A"))
        assertNull(
            GeoDebugHiddenTruth.parseRmc("\$GPRMC,123519,A,0000.000,N,00000.000,E,0.0,0.0,230394,,,A*00"),
        )
    }

    @Test
    fun firstValidRmcSkipsVoidThenTakesActive() {
        val fix = GeoDebugHiddenTruth.firstValidRmc(
            listOf(
                "\$GNRMC,1,V,,,,,,,101225,,,N*4A",
                "\$GNRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,,,A*6A",
            ),
        )
        requireNotNull(fix)
        assertEquals(48.1173, fix.lat, 1e-4)
    }

    @Test
    fun selectPrefersNmeaThenLocThenLastKnownThenCache() {
        val nmea = GeoDebugHiddenTruth.Fix(55.0, 37.0, 10f, "nmea")
        val loc = GeoDebugHiddenTruth.Fix(55.1, 37.1, 20f, "tbox", ageMs = 500L)
        val last = GeoDebugHiddenTruth.Fix(55.2, 37.2, 30f, "android", ageMs = 8_000L)
        val cached = GeoDebugHiddenTruth.Fix(55.3, 37.3, 40f, "tbox")

        assertEquals(nmea.lat, GeoDebugHiddenTruth.select(nmea, loc, last, cached, 10_000L, 1_000L)!!.lat, 0.0)
        assertEquals(loc.lat, GeoDebugHiddenTruth.select(null, loc, last, cached, 10_000L, 1_000L)!!.lat, 0.0)
        assertEquals(last.lat, GeoDebugHiddenTruth.select(null, null, last, cached, 10_000L, 1_000L)!!.lat, 0.0)
        val aged = GeoDebugHiddenTruth.select(null, null, null, cached, 10_000L, 1_000L)!!
        assertEquals(55.3, aged.lat, 0.0)
        assertEquals(9_000L, aged.ageMs)
        assertNull(GeoDebugHiddenTruth.select(null, null, null, null, 10_000L, null))
    }

    @Test
    fun fromPublishedRejectsZeroIsland() {
        assertNull(
            GeoDebugHiddenTruth.fromPublished(
                lat = 0.0,
                lon = 0.0,
                courseDeg = 90f,
                src = "tbox",
                accM = 4f,
                ageMs = 0L,
            ),
        )
    }

    @Test
    fun replayPosePrefersPreMatchOverMock() {
        val pose = GeoDebugHiddenTruth.replayPose(
            preMatchLat = 56.5,
            preMatchLon = 38.4,
            preMatchBearing = 12f,
            mockLat = 56.51,
            mockLon = 38.41,
            mockBearing = 90f,
        )
        requireNotNull(pose)
        assertEquals(56.5, pose.first, 0.0)
        assertEquals(38.4, pose.second, 0.0)
        assertEquals(12f, pose.third, 0f)
    }

    @Test
    fun replayPoseFallsBackToMockWhenPreMatchMissing() {
        val pose = GeoDebugHiddenTruth.replayPose(
            preMatchLat = null,
            preMatchLon = null,
            preMatchBearing = null,
            mockLat = 56.51,
            mockLon = 38.41,
            mockBearing = 90f,
        )
        requireNotNull(pose)
        assertEquals(56.51, pose.first, 0.0)
        assertEquals(90f, pose.third, 0f)
    }

    @Test
    fun replayTruthPrefersTruthLineOverNmea() {
        val nmea = GeoDebugHiddenTruth.Fix(55.0, 37.0, 10f, "nmea")
        val truth = GeoDebugHiddenTruth.replayTruth(56.0, 38.0, 80f, nmea)
        requireNotNull(truth)
        assertEquals(56.0, truth.lat, 0.0)
        assertEquals(80f, truth.courseDeg!!, 0f)
        val fallback = GeoDebugHiddenTruth.replayTruth(null, null, null, nmea)
        assertEquals(55.0, fallback!!.lat, 0.0)
    }
}
