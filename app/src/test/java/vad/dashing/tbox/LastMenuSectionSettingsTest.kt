package vad.dashing.tbox

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.ui.LeftMenuTabField
import vad.dashing.tbox.ui.LastMenuSections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LastMenuSectionSettingsTest {

    @Before
    fun clearSettingsDataStore() {
        // Robolectric reuses one Application; preferencesDataStore survives across @Test methods.
        runBlocking {
            ApplicationProvider.getApplicationContext<Application>().settingsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun saveLastMenuSection_persistsPerTab() = runBlocking {
        val manager = SettingsManager(ApplicationProvider.getApplicationContext<Application>())
        manager.saveLastMenuSection(LeftMenuTabField.MODEM.id, "Settings")
        manager.saveLastMenuSection(LeftMenuTabField.GEOPOSITION.id, "Mock")

        val map = manager.lastMenuSectionsFlow.first()
        assertEquals("Settings", map[LeftMenuTabField.MODEM.id])
        assertEquals("Mock", map[LeftMenuTabField.GEOPOSITION.id])
    }

    @Test
    fun saveLastMenuSection_updatesSameTabWithoutClearingOthers() = runBlocking {
        val manager = SettingsManager(ApplicationProvider.getApplicationContext<Application>())
        manager.saveLastMenuSection(LeftMenuTabField.MODEM.id, "Settings")
        manager.saveLastMenuSection(LeftMenuTabField.REFUELS.id, "List")
        manager.saveLastMenuSection(LeftMenuTabField.MODEM.id, "Data")

        val map = manager.lastMenuSectionsFlow.first()
        assertEquals("Data", map[LeftMenuTabField.MODEM.id])
        assertEquals("List", map[LeftMenuTabField.REFUELS.id])
    }

    @Test
    fun lastMenuSectionsFlow_defaultsToEmptyWhenUnset() = runBlocking {
        val manager = SettingsManager(ApplicationProvider.getApplicationContext<Application>())
        assertTrue(manager.lastMenuSectionsFlow.first().isEmpty())
        assertNull(manager.lastMenuSectionsFlow.first()[LeftMenuTabField.SETTINGS.id])
    }

    @Test
    fun saveSelectedTab_mainScreen_preservesLastMenuSections() = runBlocking {
        val manager = SettingsManager(ApplicationProvider.getApplicationContext<Application>())
        manager.saveSelectedTab(LeftMenuTabField.SETTINGS.id)
        manager.saveLastMenuSection(LeftMenuTabField.SETTINGS.id, "SYSTEM")
        manager.saveSelectedTab(SettingsManager.MAIN_SCREEN_TAB_KEY)

        assertEquals(SettingsManager.MAIN_SCREEN_TAB_KEY, manager.selectedTabFlow.first())
        assertEquals(LeftMenuTabField.SETTINGS.id, manager.lastMenuTabFlow.first())
        assertEquals(
            "SYSTEM",
            manager.lastMenuSectionsFlow.first()[LeftMenuTabField.SETTINGS.id],
        )
    }

    @Test
    fun preferenceStoreBackup_includesLastMenuSectionsWithLastMenuTab() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = SettingsManager(context)
        manager.saveSelectedTab(LeftMenuTabField.MODEM.id)
        manager.saveLastMenuSection(LeftMenuTabField.MODEM.id, "Settings")

        val exported = PreferenceStoreBackup.exportEntries(context.settingsDataStore)
        val names = buildSet {
            for (i in 0 until exported.length()) {
                add(exported.getJSONObject(i).getString(PreferenceStoreBackup.K_NAME))
            }
        }
        assertTrue(names.any { it.endsWith("last_menu_tab") })
        assertTrue(names.any { it.endsWith("last_menu_sections") })

        val sectionsEntry = (0 until exported.length())
            .map { exported.getJSONObject(it) }
            .first { it.getString(PreferenceStoreBackup.K_NAME).endsWith("last_menu_sections") }
        val parsed = LastMenuSections.parse(sectionsEntry.getString(PreferenceStoreBackup.K_VALUE))
        assertEquals("Settings", parsed[LeftMenuTabField.MODEM.id])
    }
}
