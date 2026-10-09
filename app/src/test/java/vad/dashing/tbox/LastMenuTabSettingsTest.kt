package vad.dashing.tbox

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.ui.LeftMenuTabField

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LastMenuTabSettingsTest {

    @Before
    fun clearSettingsDataStore() {
        // Robolectric reuses one Application; preferencesDataStore survives across @Test methods.
        runBlocking {
            ApplicationProvider.getApplicationContext<Application>().settingsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun saveSelectedTab_nonMain_updatesLastMenuTab() = runBlocking {
        val manager = SettingsManager(ApplicationProvider.getApplicationContext<Application>())
        manager.saveSelectedTab(LeftMenuTabField.THEMES.id)
        assertEquals(LeftMenuTabField.THEMES.id, manager.selectedTabFlow.first())
        assertEquals(LeftMenuTabField.THEMES.id, manager.lastMenuTabFlow.first())
    }

    @Test
    fun saveSelectedTab_mainScreen_preservesLastMenuTab() = runBlocking {
        val manager = SettingsManager(ApplicationProvider.getApplicationContext<Application>())
        manager.saveSelectedTab(LeftMenuTabField.THEMES.id)
        manager.saveSelectedTab(SettingsManager.MAIN_SCREEN_TAB_KEY)
        assertEquals(SettingsManager.MAIN_SCREEN_TAB_KEY, manager.selectedTabFlow.first())
        assertEquals(LeftMenuTabField.THEMES.id, manager.lastMenuTabFlow.first())
    }

    @Test
    fun lastMenuTabFlow_defaultsToMainWhenUnset() = runBlocking {
        val manager = SettingsManager(ApplicationProvider.getApplicationContext<Application>())
        assertEquals(SettingsManager.MAIN_SCREEN_TAB_KEY, manager.lastMenuTabFlow.first())
    }
}
