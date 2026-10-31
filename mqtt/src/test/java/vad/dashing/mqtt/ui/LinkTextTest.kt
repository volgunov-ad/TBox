package vad.dashing.mqtt.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class LinkTextTest {
    @Test
    fun noChangeYetShowsOnlyTheState() {
        assertEquals("на связи", linkText(up = true, sinceMs = 0L, zone = ZoneOffset.UTC))
        assertEquals("нет связи", linkText(up = false, sinceMs = 0L, zone = ZoneOffset.UTC))
    }

    @Test
    fun changeShowsTheClockTime() {
        val ms = (14 * 3600 + 5 * 60 + 9) * 1000L
        assertEquals("нет связи с 14:05:09", linkText(up = false, sinceMs = ms, zone = ZoneOffset.UTC))
    }
}
