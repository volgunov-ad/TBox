package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetsRepositoryDescriptionTest {

    @Test
    fun everyAvailableWidgetTypeHasDescription() {
        val missingDescriptions = WidgetsRepository.getAvailableDataKeysWidgets()
            .filter { WidgetsRepository.getDescriptionResForDataKey(it) == null }

        assertEquals(emptyList<String>(), missingDescriptions)
    }

    @Test
    fun nonInteractiveWidgetDoesNotHaveActionsDescription() {
        assertNull(WidgetsRepository.getActionsDescriptionResForDataKey("voltage"))
    }

    @Test
    fun interactiveWidgetHasActionsDescription() {
        assertNotNull(
            WidgetsRepository.getActionsDescriptionResForDataKey(DAY_NIGHT_THEME_WIDGET_DATA_KEY)
        )
    }
}
