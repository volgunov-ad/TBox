package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.ui.LeftMenuLayout
import vad.dashing.tbox.ui.LeftMenuTabField

class LeftMenuLayoutTest {

    @Test
    fun default_enablesLockedAndFloatingPanelsSettings() {
        val layout = LeftMenuLayout.default()
        val enabled = LeftMenuLayout.enabledTabKeys(layout).toSet()
        assertEquals(
            setOf(
                LeftMenuTabField.SETTINGS.id,
                LeftMenuTabField.FLOATING_PANELS_SETTINGS.id,
                LeftMenuTabField.THEMES.id,
                LeftMenuTabField.MAIN_SCREEN_SETTINGS.id,
            ),
            enabled,
        )
        assertEquals(LeftMenuTabField.defaultOrder().size, layout.rows.size)
    }

    @Test
    fun parse_blank_returnsDefault() {
        assertEquals(LeftMenuLayout.default(), LeftMenuLayout.parse(""))
    }

    @Test
    fun parse_corrupt_returnsDefault() {
        assertEquals(LeftMenuLayout.default(), LeftMenuLayout.parse("{not json"))
    }

    @Test
    fun enforceLocked_keepsSettingsEnabled() {
        val rows = LeftMenuLayout.default().rows.map { row ->
            if (row.field == LeftMenuTabField.SETTINGS) row.copy(enabled = false) else row
        }
        val enforced = LeftMenuLayout.enforceLocked(rows)
        val settingsRow = enforced.first { it.field == LeftMenuTabField.SETTINGS }
        assertTrue(settingsRow.enabled)
    }

    @Test
    fun firstVisibleTabKey_returnsFirstEnabledInOrder() {
        val layout = LeftMenuLayout.default()
        assertEquals(LeftMenuTabField.SETTINGS.id, LeftMenuLayout.firstVisibleTabKey(layout))
    }

    @Test
    fun firstVisibleTabKey_skipsDisabledModemWhenSettingsFirst() {
        val layout = LeftMenuLayout(
            LeftMenuTabField.defaultOrder().map { field ->
                LeftMenuLayout.Row(
                    field,
                    enabled = field == LeftMenuTabField.SETTINGS,
                )
            },
        )
        assertEquals(LeftMenuTabField.SETTINGS.id, LeftMenuLayout.firstVisibleTabKey(layout))
    }

    @Test
    fun resolveSelectedTab_disabledTab_fallsBackToFirstVisible() {
        val layout = LeftMenuLayout.default()
        assertEquals(
            LeftMenuTabField.SETTINGS.id,
            LeftMenuLayout.resolveSelectedTab(LeftMenuTabField.TRIPS.id, layout),
        )
    }

    @Test
    fun resolveSelectedTab_mainScreen_unchanged() {
        val layout = LeftMenuLayout.default()
        assertEquals(SettingsManager.MAIN_SCREEN_TAB_KEY, LeftMenuLayout.resolveSelectedTab(SettingsManager.MAIN_SCREEN_TAB_KEY, layout))
    }

    @Test
    fun parseSelectedTabKey_rejectsLegacyNumericValues() {
        assertEquals(SettingsManager.MAIN_SCREEN_TAB_KEY, LeftMenuLayout.parseSelectedTabKey("4"))
        assertEquals(SettingsManager.MAIN_SCREEN_TAB_KEY, LeftMenuLayout.parseSelectedTabKey("100"))
        assertEquals(SettingsManager.MAIN_SCREEN_TAB_KEY, LeftMenuLayout.parseSelectedTabKey("unknown"))
    }

    @Test
    fun parseSelectedTabKey_acceptsValidKeys() {
        assertEquals(LeftMenuTabField.TRIPS.id, LeftMenuLayout.parseSelectedTabKey("trips"))
        assertEquals(SettingsManager.MAIN_SCREEN_TAB_KEY, LeftMenuLayout.parseSelectedTabKey(SettingsManager.MAIN_SCREEN_TAB_KEY))
        assertEquals(SettingsManager.UPDATE_TAB_KEY, LeftMenuLayout.parseSelectedTabKey(SettingsManager.UPDATE_TAB_KEY))
    }

    @Test
    fun isSidebarTabEnabled_respectsLayout() {
        val layout = LeftMenuLayout.default()
        assertFalse(LeftMenuLayout.isSidebarTabEnabled(LeftMenuTabField.TRIPS.id, layout))
        assertTrue(LeftMenuLayout.isSidebarTabEnabled(LeftMenuTabField.SETTINGS.id, layout))
        assertFalse(LeftMenuLayout.isSidebarTabEnabled(SettingsManager.MAIN_SCREEN_TAB_KEY, layout))
        assertTrue(LeftMenuLayout.isSidebarTabEnabled(SettingsManager.UPDATE_TAB_KEY, layout))
    }

    @Test
    fun resolveSelectedTab_updateTab_unchanged() {
        val layout = LeftMenuLayout.default()
        assertEquals(
            SettingsManager.UPDATE_TAB_KEY,
            LeftMenuLayout.resolveSelectedTab(SettingsManager.UPDATE_TAB_KEY, layout),
        )
    }
}
