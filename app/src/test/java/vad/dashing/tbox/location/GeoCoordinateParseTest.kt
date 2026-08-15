package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeoCoordinateParseTest {

    private fun assertPoint(raw: String, lat: Double, lon: Double, eps: Double = 1e-5) {
        val p = GeoCoordinateParse.parse(raw)
        assertNotNull(raw, p)
        assertEquals(raw, lat, p!!.lat, eps)
        assertEquals(raw, lon, p.lon, eps)
    }

    @Test
    fun yandexCardDecimalVariants() {
        assertPoint("57.650525, 39.824286", 57.650525, 39.824286)
        assertPoint("57.650525,39.824286", 57.650525, 39.824286)
        assertPoint("57.650525 39.824286", 57.650525, 39.824286)
        assertPoint("57.650525; 39.824286", 57.650525, 39.824286)
        assertPoint("57.650525;39.824286", 57.650525, 39.824286)
        assertPoint("57.650525 / 39.824286", 57.650525, 39.824286)
        assertPoint("57.650525\t39.824286", 57.650525, 39.824286)
        assertPoint("57,650525, 39,824286", 57.650525, 39.824286)
        assertPoint("Ярославль\n57.650525, 39.824286", 57.650525, 39.824286)
    }

    @Test
    fun hemisphereLetters() {
        assertPoint("57.650525N, 39.824286E", 57.650525, 39.824286)
        assertPoint("N57.650525 E39.824286", 57.650525, 39.824286)
        assertPoint("N 57.650525, E 39.824286", 57.650525, 39.824286)
        assertPoint("57.650525 S, 39.824286 W", -57.650525, -39.824286)
        assertPoint("57.650525 с.ш. 39.824286 в.д.", 57.650525, 39.824286)
        assertPoint("57.650525 с. ш., 39.824286 в. д.", 57.650525, 39.824286)
        assertPoint("E39.824286 N57.650525", 57.650525, 39.824286)
    }

    @Test
    fun dms() {
        assertPoint("""57°39'01.89"N 39°49'27.43"E""", 57.650525, 39.824286, eps = 2e-4)
        assertPoint("57°39′01.89″N, 39°49′27.43″E", 57.650525, 39.824286, eps = 2e-4)
        assertPoint("57 39 1.89 N, 39 49 27.43 E", 57.650525, 39.824286, eps = 2e-4)
    }

    @Test
    fun urls() {
        assertPoint(
            "https://yandex.ru/maps/?ll=39.824286%2C57.650525&z=16",
            57.650525,
            39.824286,
        )
        assertPoint(
            "https://yandex.ru/maps/?ll=39.824286,57.650525",
            57.650525,
            39.824286,
        )
        assertPoint(
            "https://yandex.ru/maps/?pt=39.824286,57.650525",
            57.650525,
            39.824286,
        )
        assertPoint(
            "https://www.google.com/maps/@57.650525,39.824286,17z",
            57.650525,
            39.824286,
        )
        assertPoint(
            "https://www.google.com/maps?q=57.650525,39.824286",
            57.650525,
            39.824286,
        )
        assertPoint("geo:57.650525,39.824286", 57.650525, 39.824286)
        assertPoint(
            "https://www.openstreetmap.org/#map=18/57.650525/39.824286",
            57.650525,
            39.824286,
        )
        assertPoint(
            "https://2gis.ru/yaroslavl/geo/39.824286,57.650525",
            57.650525,
            39.824286,
        )
        assertPoint(
            "https://maps.apple.com/?ll=57.650525,39.824286",
            57.650525,
            39.824286,
        )
    }

    @Test
    fun labeled() {
        assertPoint("lat=57.650525 lon=39.824286", 57.650525, 39.824286)
        assertPoint("широта: 57.650525, долгота: 39.824286", 57.650525, 39.824286)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(GeoCoordinateParse.parse(null))
        assertNull(GeoCoordinateParse.parse(""))
        assertNull(GeoCoordinateParse.parse("hello"))
        assertNull(GeoCoordinateParse.parse("57.650525"))
        assertNull(GeoCoordinateParse.parse("95.0, 200.0"))
        assertNull(GeoCoordinateParse.parse("0, 0"))
        assertNull(GeoCoordinateParse.parse("57.650525, 200.0"))
    }

    @Test
    fun swapsWhenFirstLooksLikeLongitude() {
        assertPoint("139.6917, 35.6895", 35.6895, 139.6917)
    }
}
