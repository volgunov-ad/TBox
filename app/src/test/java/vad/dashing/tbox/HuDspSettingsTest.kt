package vad.dashing.tbox

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HuDspSettingsTest {
    private lateinit var context: Application

    @Before
    fun reset() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            context.settingsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun defaultsToAdayoWithAutoApplyDisabled() = runBlocking {
        val manager = SettingsManager(context)
        assertEquals(1, manager.huDspModeFlow.first())
        assertFalse(manager.huDspAutoApplyFlow.first())
    }

    @Test
    fun persistsEveryFactoryModeAndToggle() = runBlocking {
        val manager = SettingsManager(context)
        for (mode in HuDspMode.MODES) {
            manager.saveHuDspMode(mode)
            assertEquals(mode, SettingsManager(context).huDspModeFlow.first())
        }
        manager.saveHuDspAutoApply(true)
        assertTrue(SettingsManager(context).huDspAutoApplyFlow.first())
        manager.saveHuDspAutoApply(false)
        assertFalse(SettingsManager(context).huDspAutoApplyFlow.first())
        assertEquals(250, manager.huDspModeFlow.first())
    }

    @Test
    fun rejectsInvalidModeWithoutReplacingSavedSelection() = runBlocking {
        val manager = SettingsManager(context)
        manager.saveHuDspMode(2)
        assertTrue(runCatching { manager.saveHuDspMode(11) }.isFailure)
        assertEquals(2, manager.huDspModeFlow.first())
    }
}
