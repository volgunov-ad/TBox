package vad.dashing.tbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeActivationCoordinatorTest {

    @Before
    fun resetUiReadyFlag() {
        ThemeActivationCoordinator.resetMainScreenUiReadyForTests()
    }

    @Test
    fun markMainScreenUiReady_setsUiReadyFlag() {
        ThemeActivationCoordinator.markMainScreenUiReady()
        assertTrue(ThemeActivationCoordinator.mainScreenUiReadyFlow.value)
    }

    @Test
    fun themeActivationInProgress_startsFalse() {
        assertFalse(ThemeActivationCoordinator.themeActivationInProgress)
    }

    @Test
    fun settingsViewModelInit_doesNotMarkMainScreenUiReady() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        SettingsViewModel(SettingsManager(context))

        assertFalse(ThemeActivationCoordinator.mainScreenUiReadyFlow.value)
    }

    @Test
    fun settingsViewModelMainScreenReadyRegistration_marksMainScreenUiReady() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SettingsViewModel(SettingsManager(context))

        viewModel.markMainScreenUiReadyForThemeActivation()

        assertTrue(ThemeActivationCoordinator.mainScreenUiReadyFlow.value)
    }
}
