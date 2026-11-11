package vad.dashing.tbox.dcl

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
import vad.dashing.tbox.SettingsManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DclLauncherEnvironmentSettingTest {

    @Test
    fun saveDclLauncherEnvironmentEnabled_persistsAndUpdatesRoutingCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = SettingsManager(context)
        DclLauncherEnvironmentSetting.update(false)

        manager.saveDclLauncherEnvironmentEnabled(true)

        assertTrue(manager.dclLauncherEnvironmentEnabledFlow.first())
        assertTrue(DclLauncherEnvironmentSetting.enabled)

        manager.saveDclLauncherEnvironmentEnabled(false)

        assertFalse(manager.dclLauncherEnvironmentEnabledFlow.first())
        assertFalse(DclLauncherEnvironmentSetting.enabled)
    }
}
