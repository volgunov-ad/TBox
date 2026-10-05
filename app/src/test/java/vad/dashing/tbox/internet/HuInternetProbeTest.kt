package vad.dashing.tbox.internet

import org.junit.Assert.assertEquals
import org.junit.Test

class HuInternetProbeTest {
    @Test
    fun normalizeUrl_blankUsesDefault() {
        assertEquals(HuInternetProbe.DEFAULT_URL, HuInternetProbe.normalizeUrl(null))
        assertEquals(HuInternetProbe.DEFAULT_URL, HuInternetProbe.normalizeUrl(""))
        assertEquals(HuInternetProbe.DEFAULT_URL, HuInternetProbe.normalizeUrl("   "))
    }

    @Test
    fun normalizeUrl_addsHttpsWhenMissing() {
        assertEquals("https://example.com", HuInternetProbe.normalizeUrl("example.com"))
        assertEquals("https://yandex.ru/path", HuInternetProbe.normalizeUrl("yandex.ru/path"))
    }

    @Test
    fun normalizeUrl_keepsScheme() {
        assertEquals("http://192.168.1.1/ping", HuInternetProbe.normalizeUrl("http://192.168.1.1/ping"))
        assertEquals(
            "https://ya.ru",
            HuInternetProbe.normalizeUrl("  https://ya.ru  "),
        )
    }

    @Test
    fun coerceIntervalSec_clamps() {
        assertEquals(HuInternetProbe.MIN_INTERVAL_SEC, HuInternetProbe.coerceIntervalSec(1))
        assertEquals(HuInternetProbe.MAX_INTERVAL_SEC, HuInternetProbe.coerceIntervalSec(999))
        assertEquals(15, HuInternetProbe.coerceIntervalSec(15))
        assertEquals(
            HuInternetProbe.DEFAULT_INTERVAL_SEC,
            HuInternetProbe.coerceIntervalSec(HuInternetProbe.DEFAULT_INTERVAL_SEC),
        )
    }
}
