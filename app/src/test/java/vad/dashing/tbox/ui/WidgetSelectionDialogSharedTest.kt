package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.DAY_NIGHT_THEME_WIDGET_DATA_KEY

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

    @Test
    fun descriptionResources_areHiddenForUnselectedItem() {
        val result = resolveWidgetSelectionDescriptionResources(
            dataKey = DAY_NIGHT_THEME_WIDGET_DATA_KEY,
            selectedDataKey = "voltage",
        )

        assertNull(result)
    }

    @Test
    fun descriptionResources_hideActionsForNonInteractiveItem() {
        val result = resolveWidgetSelectionDescriptionResources(
            dataKey = "voltage",
            selectedDataKey = "voltage",
        )

        assertNotNull(result)
        assertNull(result?.actionsRes)
    }

    @Test
    fun descriptionResources_showActionsForInteractiveItem() {
        val result = resolveWidgetSelectionDescriptionResources(
            dataKey = DAY_NIGHT_THEME_WIDGET_DATA_KEY,
            selectedDataKey = DAY_NIGHT_THEME_WIDGET_DATA_KEY,
        )

        assertNotNull(result?.descriptionRes)
        assertNotNull(result?.actionsRes)
    }
}
