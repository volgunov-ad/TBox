package vad.dashing.tbox.location

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GeoShareIntentParserTest {

    @Test
    fun sendTextPlain_yandexUrl() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Ярославль\nhttps://yandex.ru/maps/?ll=39.824286%2C57.650525&z=16",
            )
        }
        val p = GeoShareIntentParser.parse(intent)
        assertNotNull(p)
        assertEquals(57.650525, p!!.lat, 1e-5)
        assertEquals(39.824286, p.lon, 1e-5)
    }

    @Test
    fun sendTextPlain_twoGisUrl() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://2gis.ru/yaroslavl/geo/39.824286,57.650525")
        }
        val p = GeoShareIntentParser.parse(intent)
        assertNotNull(p)
        assertEquals(57.650525, p!!.lat, 1e-5)
        assertEquals(39.824286, p.lon, 1e-5)
    }

    @Test
    fun viewGeoUri() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:57.650525,39.824286"))
        val p = GeoShareIntentParser.parse(intent)
        assertNotNull(p)
        assertEquals(57.650525, p!!.lat, 1e-5)
        assertEquals(39.824286, p.lon, 1e-5)
    }

    @Test
    fun viewGeoUriWithQuery() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=57.650525,39.824286"))
        val p = GeoShareIntentParser.parse(intent)
        assertNotNull(p)
        assertEquals(57.650525, p!!.lat, 1e-5)
        assertEquals(39.824286, p.lon, 1e-5)
    }

    @Test
    fun clipDataUri() {
        val uri = Uri.parse("https://2gis.ru/geo/39.824286,57.650525")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            clipData = ClipData.newRawUri("map", uri)
        }
        val p = GeoShareIntentParser.parse(intent)
        assertNotNull(p)
        assertEquals(57.650525, p!!.lat, 1e-5)
        assertEquals(39.824286, p.lon, 1e-5)
    }

    @Test
    fun rejectsNonCoordinateShare() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "hello from notes")
        }
        assertNull(GeoShareIntentParser.parse(intent))
        assertNull(GeoShareIntentParser.parse(null))
    }
}
