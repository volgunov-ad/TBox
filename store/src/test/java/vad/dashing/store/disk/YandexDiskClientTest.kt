package vad.dashing.store.disk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexDiskClientTest {
    @Test
    fun encodeQueryValue_spaceIsPercent20() {
        val encoded = YandexDiskClient.encodeQueryValue("/apps/demo/files/my file.txt")
        assertTrue(encoded.contains("my%20file.txt"))
        assertFalse(encoded.contains("+"))
    }

    @Test
    fun encodeQueryValue_cyrillicIsPercentEncoded() {
        val encoded = YandexDiskClient.encodeQueryValue("файл")
        assertFalse(encoded.contains("ф"))
        assertTrue(encoded.startsWith("%"))
        // UTF-8 for «ф» is D1 84
        assertTrue(encoded.contains("%D1%84") || encoded.contains("%d1%84"))
        assertEquals(
            YandexDiskClient.encodeQueryValue("файл"),
            encoded,
        )
    }
}
