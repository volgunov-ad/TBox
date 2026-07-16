package vad.dashing.tbox

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeSettingsValidatorTest {

    @Test
    fun validateActiveTheme_clearsStaleCacheKeyWhenCacheMissing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settingsManager = SettingsManager(context)
        settingsManager.saveActiveTheme(
            uri = "drive_mode_2_eco",
            fingerprint = "stale-fp",
            sections = setOf(ThemeSection.MAIN_SCREEN),
            applyTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS),
        )

        ThemeSettingsValidator.validateOnStartup(context, settingsManager)

        assertEquals("", settingsManager.activeThemeUriFlow.first())
        assertEquals("", settingsManager.activeThemeFingerprintFlow.first())
    }

    @Test
    fun sanitizeDriveModeThemePaths_removesInaccessibleUris() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settingsManager = SettingsManager(context)
        val accessibleFile = File(context.cacheDir, "eco.tboxtheme").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        }
        val accessibleUri = Uri.fromFile(accessibleFile).toString()

        settingsManager.saveDriveModeThemePaths(
            mapOf(
                2 to accessibleUri,
                3 to "file:///no/such/theme.tboxtheme",
            ),
        )

        ThemeSettingsValidator.validateOnStartup(context, settingsManager)

        val paths = settingsManager.driveModeThemePathsFlow.first()
        assertEquals(accessibleUri, paths[2])
        assertFalse(paths.containsKey(3))
        assertEquals(1, paths.size)
    }
}
