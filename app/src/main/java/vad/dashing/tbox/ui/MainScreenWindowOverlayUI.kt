package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
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
 * display size in **pixel** constraints and placed so the activity-space viewport origin
 * maps to overlay (0,0) — no Dp round-trip and no RTL-mirrored [Modifier.offset].
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
        // Main-screen panel coords are absolute LTR pixels; keep crop placement LTR too.
        CompositionLocalProvider(
            LocalClickSoundEnabled provides uiClickSoundsEnabled,
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
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
                    if (cropEnabled) {
                        val fullW = overlayLayout.fullWidthPx.coerceAtLeast(1)
                        val fullH = overlayLayout.fullHeightPx.coerceAtLeast(1)
                        val placeX = MainScreenWindowOverlayLayout.contentOffsetX(overlayLayout)
                        val placeY = MainScreenWindowOverlayLayout.contentOffsetY(overlayLayout)
                        Layout(
                            content = {
                                MainScreen(
                                    tboxViewModel = tboxViewModel,
                                    canViewModel = canViewModel,
                                    appDataViewModel = appDataViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onOpenConsole = {
                                        settingsViewModel.saveSelectedTab(
                                            LeftMenuLayout.firstVisibleTabKey(leftMenuLayout),
                                        )
                                        FreeformLaunchHelper.exitWindowModeToFullscreen(
                                            context.applicationContext,
                                        )
                                    },
                                    onTboxRestart = onRebootTbox,
                                    onTripFinishAndStart = onTripFinishAndStart,
                                    windowMode = true,
                                    onExitWindowMode = null,
                                    onExitWindowModeToFullscreen = null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                            measurePolicy = { measurables, constraints ->
                                val placeable = measurables.first().measure(
                                    Constraints.fixed(fullW, fullH),
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    // Absolute place — do not use placeRelative (RTL).
                                    placeable.place(placeX, placeY)
                                }
                            },
                        )
                    } else {
                        MainScreen(
                            tboxViewModel = tboxViewModel,
                            canViewModel = canViewModel,
                            appDataViewModel = appDataViewModel,
                            settingsViewModel = settingsViewModel,
                            onOpenConsole = {
                                settingsViewModel.saveSelectedTab(
                                    LeftMenuLayout.firstVisibleTabKey(leftMenuLayout),
                                )
                                FreeformLaunchHelper.exitWindowModeToFullscreen(
                                    context.applicationContext,
                                )
                            },
                            onTboxRestart = onRebootTbox,
                            onTripFinishAndStart = onTripFinishAndStart,
                            windowMode = true,
                            onExitWindowMode = {
                                FreeformLaunchHelper.exitWindowMode(context.applicationContext)
                            },
                            onExitWindowModeToFullscreen = {
                                FreeformLaunchHelper.exitWindowModeToFullscreen(
                                    context.applicationContext,
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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
