package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.AppDataViewModelFactory
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.freeform.FreeformLaunchHelper
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * Full [MainScreen] hosted in the window-mode overlay (not a floating panel).
 */
@Composable
fun MainScreenWindowOverlayUI(
    settingsManager: SettingsManager,
    appDataManager: AppDataManager,
    onRebootTbox: () -> Unit,
    onTripFinishAndStart: () -> Unit,
) {
    val context = LocalContext.current
    val tboxViewModel: TboxViewModel = viewModel()
    val canViewModel: CanDataViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(settingsManager),
    )
    val appDataViewModel: AppDataViewModel = viewModel(
        factory = AppDataViewModelFactory(appDataManager, settingsManager),
    )
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
    val appFontFamilyId by settingsViewModel.appFontFamilyId.collectAsStateWithLifecycle()
    val leftMenuLayout by settingsViewModel.leftMenuLayout.collectAsStateWithLifecycle()
    val uiClickSoundsEnabled by settingsViewModel.uiClickSoundsEnabled.collectAsStateWithLifecycle()

    TboxAppTheme(theme = currentTheme, fontFamilyId = appFontFamilyId) {
        CompositionLocalProvider(LocalClickSoundEnabled provides uiClickSoundsEnabled) {
            MainScreen(
                tboxViewModel = tboxViewModel,
                canViewModel = canViewModel,
                appDataViewModel = appDataViewModel,
                settingsViewModel = settingsViewModel,
                onOpenConsole = {
                    settingsViewModel.saveSelectedTab(
                        LeftMenuLayout.firstVisibleTabKey(leftMenuLayout),
                    )
                    FreeformLaunchHelper.exitWindowModeToFullscreen(context.applicationContext)
                },
                onTboxRestart = onRebootTbox,
                onTripFinishAndStart = onTripFinishAndStart,
                windowMode = true,
                onExitWindowMode = {
                    FreeformLaunchHelper.exitWindowMode(context.applicationContext)
                },
                onExitWindowModeToFullscreen = {
                    FreeformLaunchHelper.exitWindowModeToFullscreen(context.applicationContext)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
