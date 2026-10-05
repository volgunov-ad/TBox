package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationUserMessageOverlayTest {
    @Test
    fun countdown_usesCeilSeconds() {
        assertEquals("Закроется автоматически", formatAutoCloseCountdown(0L))
        assertEquals("Автозакрытие через 1 с", formatAutoCloseCountdown(1L))
        assertEquals("Автозакрытие через 1 с", formatAutoCloseCountdown(1_000L))
        assertEquals("Автозакрытие через 2 с", formatAutoCloseCountdown(1_001L))
        assertEquals("Автозакрытие через 5 с", formatAutoCloseCountdown(5_000L))
    }
}
