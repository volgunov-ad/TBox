package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSelectionDialogSharedTest {

    @Test
    fun resolveStoredMediaSelectedPlayer_returnsCurrentWhenStillSelected() {
        val result = resolveStoredMediaSelectedPlayer(
            selectedPlayers = setOf("ru.yandex.music", "com.maxmpz.audioplayer"),
            currentSelectedPlayer = "com.maxmpz.audioplayer"
        )

        assertEquals("com.maxmpz.audioplayer", result)
    }

    @Test
    fun resolveStoredMediaSelectedPlayer_returnsOnlyPlayerWhenCurrentMissing() {
        val result = resolveStoredMediaSelectedPlayer(
            selectedPlayers = setOf("ru.yandex.music"),
            currentSelectedPlayer = "missing.package"
        )

        assertEquals("ru.yandex.music", result)
    }

    @Test
    fun resolveStoredMediaSelectedPlayer_returnsEmptyForEmptySelection() {
        val result = resolveStoredMediaSelectedPlayer(
            selectedPlayers = emptySet(),
            currentSelectedPlayer = "missing.package"
        )

        assertEquals("", result)
    }
}
