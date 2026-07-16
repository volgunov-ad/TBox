package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun slaSpeedLimitWidgetIsOfferedInPicker() {
        assertTrue(
            WidgetsRepository.getAvailableDataKeysWidgets().contains(SLA_SPEED_LIMIT_WIDGET_DATA_KEY),
        )
        assertNotNull(WidgetsRepository.getDescriptionResForDataKey(SLA_SPEED_LIMIT_WIDGET_DATA_KEY))
    }

    @Test
    fun speedLimiterWidgetIsHiddenFromPickerUntilDebugged() {
        assertFalse(
            WidgetsRepository.getAvailableDataKeysWidgets().contains(SPEED_LIMITER_WIDGET_DATA_KEY),
        )
    }
}
