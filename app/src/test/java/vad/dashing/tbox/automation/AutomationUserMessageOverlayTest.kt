package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationUserMessageOverlayTest {
    @Test
    fun countdown_usesCeilSeconds() {
        assertEquals(ruEn("Закроется автоматически", "Closes automatically"), formatAutoCloseCountdown(0L))
        assertEquals(ruEn("Автозакрытие через 1 с", "Auto-close in 1 s"), formatAutoCloseCountdown(1L))
        assertEquals(ruEn("Автозакрытие через 1 с", "Auto-close in 1 s"), formatAutoCloseCountdown(1_000L))
        assertEquals(ruEn("Автозакрытие через 2 с", "Auto-close in 2 s"), formatAutoCloseCountdown(1_001L))
        assertEquals(ruEn("Автозакрытие через 5 с", "Auto-close in 5 s"), formatAutoCloseCountdown(5_000L))
    }
}
