package vad.dashing.tbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FloatingPanelsAllowBeyondScreenSettingsTest {

    @Test
    fun allowBeyondScreen_defaultsToFalse() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = SettingsManager(context)
        assertFalse(manager.floatingPanelsAllowBeyondScreenFlow.first())
    }

    @Test
    fun allowBeyondScreen_persistsToggle() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = SettingsManager(context)
        manager.saveFloatingPanelsAllowBeyondScreen(true)
        assertTrue(manager.floatingPanelsAllowBeyondScreenFlow.first())
        manager.saveFloatingPanelsAllowBeyondScreen(false)
        assertFalse(manager.floatingPanelsAllowBeyondScreenFlow.first())
    }
}
