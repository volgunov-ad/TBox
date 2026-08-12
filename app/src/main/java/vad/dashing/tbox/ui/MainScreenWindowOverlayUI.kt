package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.AppDataViewModelFactory
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.MainScreenWindowModeExitButtonPosition
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.freeform.FreeformLaunchHelper
import vad.dashing.tbox.freeform.MainScreenWindowOverlayLayout
import vad.dashing.tbox.normalizePanelLayoutSnapDp
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * Full [MainScreen] hosted in the window-mode overlay (not a floating panel).
 *
 * When [MainScreenWindowOverlayLayout] crop is enabled, MainScreen is laid out at full
 * display size and offset so the overlay window shows a clipped viewport (not a shrink).
 * Exit buttons stay in overlay-local coordinates so they remain visible.
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
    val overlayLayout by MainScreenWindowOverlayLayout.state.collectAsStateWithLifecycle()
    val cropEnabled = overlayLayout.cropEnabled

    val exitWindowBtnPos by settingsViewModel.mainScreenWindowModeExitButtonPosition
        .collectAsStateWithLifecycle()
    val restoreWindowBtnPos by settingsViewModel.mainScreenWindowModeRestoreButtonPosition
        .collectAsStateWithLifecycle()
    val cornerBtnSizeDp by settingsViewModel.mainScreenCornerButtonSizeDp.collectAsStateWithLifecycle()
    val cornerBtnBgLight by settingsViewModel.mainScreenCornerButtonBackgroundLight
        .collectAsStateWithLifecycle()
    val cornerBtnBgDark by settingsViewModel.mainScreenCornerButtonBackgroundDark
        .collectAsStateWithLifecycle()
    val cornerBtnIconLight by settingsViewModel.mainScreenCornerButtonIconLight
        .collectAsStateWithLifecycle()
    val cornerBtnIconDark by settingsViewModel.mainScreenCornerButtonIconDark
        .collectAsStateWithLifecycle()
    val layoutSnapDp by settingsViewModel.mainScreenPanelsLayoutSnapDp.collectAsStateWithLifecycle()
    val layoutSnapEnabled by settingsViewModel.mainScreenPanelsLayoutSnapEnabled
        .collectAsStateWithLifecycle()

    TboxAppTheme(theme = currentTheme, fontFamilyId = appFontFamilyId) {
        CompositionLocalProvider(LocalClickSoundEnabled provides uiClickSoundsEnabled) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val overlayWpx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val overlayHpx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val cornerIconSize = cornerBtnSizeDp.dp
                val cornerBackgroundColor = Color(
                    if (currentTheme == 2) cornerBtnBgDark else cornerBtnBgLight,
                )
                val cornerIconTint = Color(
                    if (currentTheme == 2) cornerBtnIconDark else cornerBtnIconLight,
                )
                val normalizedSnapDp = normalizePanelLayoutSnapDp(layoutSnapDp)
                val layoutSnapStepPx = with(density) { normalizedSnapDp.dp.toPx() }
                val effectiveLayoutSnapStepPx = if (layoutSnapEnabled) layoutSnapStepPx else 0f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                ) {
                    val mainModifier = if (cropEnabled) {
                        val fullW = with(density) { overlayLayout.fullWidthPx.toDp() }
                        val fullH = with(density) { overlayLayout.fullHeightPx.toDp() }
                        val ox = MainScreenWindowOverlayLayout.contentOffsetX(overlayLayout)
                        val oy = MainScreenWindowOverlayLayout.contentOffsetY(overlayLayout)
                        Modifier
                            .requiredSize(fullW, fullH)
                            .offset { IntOffset(ox, oy) }
                    } else {
                        Modifier.fillMaxSize()
                    }
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
                        // Crop: host ×/□ in overlay-local coords (below). Shrink: inside MainScreen.
                        onExitWindowMode = if (cropEnabled) {
                            null
                        } else {
                            {
                                FreeformLaunchHelper.exitWindowMode(context.applicationContext)
                            }
                        },
                        onExitWindowModeToFullscreen = if (cropEnabled) {
                            null
                        } else {
                            {
                                FreeformLaunchHelper.exitWindowModeToFullscreen(
                                    context.applicationContext,
                                )
                            }
                        },
                        modifier = mainModifier,
                    )
                }

                if (cropEnabled) {
                    MainScreenDraggableCornerButton(
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.main_screen_window_mode_exit_cd),
                        iconSize = cornerIconSize,
                        backgroundColor = cornerBackgroundColor,
                        iconTint = cornerIconTint,
                        maxWidthPx = overlayWpx,
                        maxHeightPx = overlayHpx,
                        normalizedX = exitWindowBtnPos.x,
                        normalizedY = exitWindowBtnPos.y,
                        onSaveNormalized = { x, y ->
                            settingsViewModel.saveMainScreenWindowModeExitButton(
                                MainScreenWindowModeExitButtonPosition(x, y),
                            )
                        },
                        onClick = {
                            FreeformLaunchHelper.exitWindowMode(context.applicationContext)
                        },
                        layoutSnapStepPx = effectiveLayoutSnapStepPx,
                    )
                    MainScreenDraggableCornerButton(
                        icon = WindowModeRestoreSquareIcon,
                        contentDescription = stringResource(
                            R.string.main_screen_window_mode_restore_cd,
                        ),
                        iconSize = cornerIconSize,
                        backgroundColor = cornerBackgroundColor,
                        iconTint = cornerIconTint,
                        maxWidthPx = overlayWpx,
                        maxHeightPx = overlayHpx,
                        normalizedX = restoreWindowBtnPos.x,
                        normalizedY = restoreWindowBtnPos.y,
                        onSaveNormalized = { x, y ->
                            settingsViewModel.saveMainScreenWindowModeRestoreButton(
                                MainScreenWindowModeExitButtonPosition(x, y),
                            )
                        },
                        onClick = {
                            FreeformLaunchHelper.exitWindowModeToFullscreen(
                                context.applicationContext,
                            )
                        },
                        layoutSnapStepPx = effectiveLayoutSnapStepPx,
                    )
                }
            }
        }
    }
}
