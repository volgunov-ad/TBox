package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Test

class CamdUiTextTest {

    @Test
    fun openWindowIdsParsesStatus() {
        assertEquals(setOf(4, 8), CamdUiText.openWindowIds("windows=4,8 avm=stopped keep_avm=0"))
        assertEquals(emptySet<Int>(), CamdUiText.openWindowIds("windows=none avm=running keep_avm=0"))
        assertEquals(emptySet<Int>(), CamdUiText.openWindowIds(""))
    }

    @Test
    fun statusIsReadable() {
        assertEquals(
            "Открыты окна: 4 · лево, 8 · перед · штатный AVM остановлен (держится выключенным)",
            CamdUiText.describeStatus("windows=4,8 avm=stopped keep_avm=1"),
        )
        assertEquals(
            "Окон нет · штатный AVM работает",
            CamdUiText.describeStatus("windows=none avm=running keep_avm=0"),
        )
        assertEquals("", CamdUiText.describeStatus(" "))
    }

    @Test
    fun resultsAreReadable() {
        assertEquals("Окно открыто: 9 · зад", CamdUiText.describeResult("state=open input=9"))
        assertEquals("Окно перемещено: 0 · салон / AR", CamdUiText.describeResult("state=moved input=0"))
        assertEquals("Окно закрыто: 5 · право", CamdUiText.describeResult("state=closed input=5"))
        assertEquals("Все окна закрыты", CamdUiText.describeResult("state=closed"))
        assertEquals(
            "Ошибка: нет разрешения на показ поверх других окон",
            CamdUiText.describeResult("state=error error=overlay_permission"),
        )
        assertEquals(
            "Ошибка: окно не успело обновиться — система занята, попробуйте ещё раз",
            CamdUiText.describeResult("state=error error=overlay_timeout input=4"),
        )
        assertEquals(
            "Ошибка: mb_daemon не ответил",
            CamdUiText.describeResult("state=error error=mb_daemon не ответил"),
        )
        assertEquals("Открываю окно…", CamdUiText.describeResult("Открываю окно…"))
    }
}
