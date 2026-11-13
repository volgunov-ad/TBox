package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRequestWidgetUrlDispatchTest {

    @Test
    fun httpScheme_goesThroughHttpClient() {
        assertFalse(httpRequestWidgetUrlNeedsIntentDispatch("http://example.com/api"))
        assertFalse(httpRequestWidgetUrlNeedsIntentDispatch("https://example.com/api?pt=37.62,55.75"))
    }

    @Test
    fun schemeCaseInsensitive_goesThroughHttpClient() {
        assertFalse(httpRequestWidgetUrlNeedsIntentDispatch("HTTP://EXAMPLE.COM/api"))
        assertFalse(httpRequestWidgetUrlNeedsIntentDispatch("Https://Example.com/"))
    }

    @Test
    fun deeplinkSchemes_goThroughIntentDispatch() {
        assertTrue(
            httpRequestWidgetUrlNeedsIntentDispatch(
                "yandexmaps://maps.yandex.ru/?pt=37.620070,55.753630",
            ),
        )
        assertTrue(
            httpRequestWidgetUrlNeedsIntentDispatch(
                "yandexnavi://build_route_on_map?lat_to=55.753630&lon_to=37.620070",
            ),
        )
        assertTrue(httpRequestWidgetUrlNeedsIntentDispatch("geo:55.753630,37.620070"))
        assertTrue(httpRequestWidgetUrlNeedsIntentDispatch("tel:+70000000000"))
    }

    @Test
    fun surroundingWhitespace_isIgnored() {
        assertFalse(httpRequestWidgetUrlNeedsIntentDispatch("  https://example.com  "))
        assertTrue(httpRequestWidgetUrlNeedsIntentDispatch("  geo:55.75,37.62  "))
    }

    @Test
    fun missingOrMalformedScheme_staysOnHttpPath() {
        assertFalse(httpRequestWidgetUrlNeedsIntentDispatch(""))
        assertFalse(httpRequestWidgetUrlNeedsIntentDispatch("example.com/api"))
        assertFalse(httpRequestWidgetUrlNeedsIntentDispatch("//example.com:8080/x"))
        assertFalse(httpRequestWidgetUrlNeedsIntentDispatch(":noscheme"))
        assertFalse(httpRequestWidgetUrlNeedsIntentDispatch("http:"))
    }
}
