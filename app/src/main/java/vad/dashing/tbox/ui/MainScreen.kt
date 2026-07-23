package vad.dashing.tbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.distinctUntilChanged
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.FloatingDashboardTileEditRequestBus
import vad.dashing.tbox.MAIN_SCREEN_LAYOUT_GRID_MIN_SNAP_DP_EXCLUSIVE
import vad.dashing.tbox.MainScreenAddButtonPosition
import vad.dashing.tbox.MainScreenPageNextButtonPosition
import vad.dashing.tbox.MainScreenPagePrevButtonPosition
import vad.dashing.tbox.MainScreenSettingsButtonPosition
import vad.dashing.tbox.MainScreenWindowModeExitButtonPosition
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.freeform.WindowModeUiGuard
import vad.dashing.tbox.normalizePanelLayoutSnapDp

private const val MAIN_SCREEN_PANEL_FADE_MS = 300

/** Outline square (icons-core has no CropSquare); restore-fullscreen in window mode. */
private val WindowModeRestoreSquareIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "WindowModeRestoreSquare",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ) {
            moveTo(5f, 5f)
            lineTo(19f, 5f)
            lineTo(19f, 19f)
            lineTo(5f, 19f)
            close()
            moveTo(7f, 7f)
            lineTo(7f, 17f)
            lineTo(17f, 17f)
            lineTo(17f, 7f)
            close()
        }
    }.build()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenConsole: () -> Unit,
    onTboxRestart: () -> Unit,
    onTripFinishAndStart: () -> Unit,
    modifier: Modifier = Modifier,
    /** When true, this is the window-mode overlay: show exit corner buttons, hide activity-only chrome extras. */
    windowMode: Boolean = false,
    /** Close window mode without restoring MainActivity (X). */
    onExitWindowMode: (() -> Unit)? = null,
    /** Close window mode and restore MainActivity fullscreen (square). */
    onExitWindowModeToFullscreen: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val mainPanels by settingsViewModel.mainScreenDashboards.collectAsStateWithLifecycle()
    val pageCount by settingsViewModel.mainScreenPageCount.collectAsStateWithLifecycle()
    val normalCurrentPage by settingsViewModel.mainScreenCurrentPage.collectAsStateWithLifecycle()
    val windowModeCurrentPage by
        settingsViewModel.mainScreenWindowModeCurrentPage.collectAsStateWithLifecycle()
    val currentPage = if (windowMode) windowModeCurrentPage ?: normalCurrentPage else normalCurrentPage
    val settingsBtnPos by settingsViewModel.mainScreenSettingsButtonPosition.collectAsStateWithLifecycle()
    val addBtnPos by settingsViewModel.mainScreenAddButtonPosition.collectAsStateWithLifecycle()
    val pagePrevBtnPos by settingsViewModel.mainScreenPagePrevButtonPosition.collectAsStateWithLifecycle()
    val pageNextBtnPos by settingsViewModel.mainScreenPageNextButtonPosition.collectAsStateWithLifecycle()
    val exitWindowBtnPos by settingsViewModel.mainScreenWindowModeExitButtonPosition.collectAsStateWithLifecycle()
    val restoreWindowBtnPos by
        settingsViewModel.mainScreenWindowModeRestoreButtonPosition.collectAsStateWithLifecycle()
    val cornerBtnSizeDp by settingsViewModel.mainScreenCornerButtonSizeDp.collectAsStateWithLifecycle()
    val cornerBtnBgLight by settingsViewModel.mainScreenCornerButtonBackgroundLight.collectAsStateWithLifecycle()
    val cornerBtnBgDark by settingsViewModel.mainScreenCornerButtonBackgroundDark.collectAsStateWithLifecycle()
    val cornerBtnIconLight by settingsViewModel.mainScreenCornerButtonIconLight.collectAsStateWithLifecycle()
    val cornerBtnIconDark by settingsViewModel.mainScreenCornerButtonIconDark.collectAsStateWithLifecycle()
    val layoutSnapDp by settingsViewModel.mainScreenPanelsLayoutSnapDp.collectAsStateWithLifecycle()
    val layoutSnapEnabled by settingsViewModel.mainScreenPanelsLayoutSnapEnabled.collectAsStateWithLifecycle()
    val showLayoutGrid by settingsViewModel.mainScreenShowLayoutGrid.collectAsStateWithLifecycle()
    val folderLight by settingsViewModel.mainScreenWallpaperLightFolderUri.collectAsStateWithLifecycle()
    val folderDark by settingsViewModel.mainScreenWallpaperDarkFolderUri.collectAsStateWithLifecycle()
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
    val forLightTheme = currentTheme != 2
    val activeFolderUriStr = if (forLightTheme) folderLight else folderDark
    val cornerIconSize = cornerBtnSizeDp.dp
    val cornerBackgroundColor = Color(
        if (currentTheme == 2) cornerBtnBgDark else cornerBtnBgLight
    )
    val cornerIconTint = Color(
        if (currentTheme == 2) cornerBtnIconDark else cornerBtnIconLight
    )
    val density = LocalDensity.current
    val normalizedSnapDp = normalizePanelLayoutSnapDp(layoutSnapDp)
    val layoutSnapStepPx = with(density) { normalizedSnapDp.dp.toPx() }
    val effectiveLayoutSnapStepPx = if (layoutSnapEnabled) layoutSnapStepPx else 0f
    val drawLayoutGrid =
        showLayoutGrid && normalizedSnapDp > MAIN_SCREEN_LAYOUT_GRID_MIN_SNAP_DP_EXCLUSIVE
    val layoutGridColor = MaterialTheme.colorScheme.onSurface
    val newMainPanelDefaultName = stringResource(R.string.floating_dashboard_new_panel_default)
    val wallpaperController = remember { MainScreenWallpaperController() }
    var wallpaperCount by remember { mutableIntStateOf(0) }
    var wallpaperWorkEnabled by remember { mutableStateOf(false) }
    var themeActivationReadyMarked by remember { mutableStateOf(false) }
    val multiPage = pageCount > 1
    val showWallpaperNavButtons = multiPage && wallpaperCount > 1

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        settingsViewModel.flushMainScreenCurrentPage(windowMode)
        settingsViewModel.flushMainScreenWallpaperSelection()
    }

    DisposableEffect(Unit) {
        onDispose {
            settingsViewModel.flushMainScreenCurrentPage(windowMode)
            settingsViewModel.flushMainScreenWallpaperSelection()
        }
    }

    var floatingOverlayEditRequest by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val pendingFloatingTileEdit by FloatingDashboardTileEditRequestBus.pending
        .collectAsStateWithLifecycle()
    LaunchedEffect(pendingFloatingTileEdit) {
        pendingFloatingTileEdit?.let { req ->
            floatingOverlayEditRequest = req
            FloatingDashboardTileEditRequestBus.clear()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .mainScreenLayoutGrid(
                enabled = drawLayoutGrid,
                stepPx = layoutSnapStepPx,
                lineColor = layoutGridColor,
            )
            .onGloballyPositioned {
                if (!themeActivationReadyMarked) {
                    themeActivationReadyMarked = true
                    settingsViewModel.markMainScreenUiReadyForThemeActivation()
                }
                if (!wallpaperWorkEnabled) {
                    wallpaperWorkEnabled = true
                }
            }
    ) {
        MainScreenWallpaperBackground(
            theme = currentTheme,
            settingsViewModel = settingsViewModel,
            currentMainScreenPage = currentPage,
            userScrollEnabled = !multiPage,
            wallpaperController = wallpaperController,
            onWallpaperCountChanged = { wallpaperCount = it },
            folderUriStr = activeFolderUriStr,
            forLightTheme = forLightTheme,
            wallpaperWorkEnabled = wallpaperWorkEnabled,
            modifier = Modifier.fillMaxSize(),
        )
        val maxWpx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxHpx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        if (multiPage) {
            key(pageCount) {
                val pagePagerState = rememberPagerState(
                    initialPage = (currentPage - 1).coerceIn(0, pageCount - 1),
                    pageCount = { pageCount },
                )
                val currentPageState by rememberUpdatedState(currentPage)
                LaunchedEffect(currentPage, pageCount) {
                    val want = (currentPage - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    if (pagePagerState.currentPage != want) {
                        pagePagerState.animateScrollToPage(want)
                    }
                }
                LaunchedEffect(pagePagerState, pageCount) {
                    snapshotFlow { pagePagerState.settledPage }
                        .distinctUntilChanged()
                        .collect { settled ->
                            val page = (settled + 1).coerceIn(1, pageCount)
                            if (page != currentPageState) {
                                settingsViewModel.scheduleSaveMainScreenCurrentPage(page, windowMode)
                            }
                        }
                }
                HorizontalPager(
                    state = pagePagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0,
                ) { pageIndex ->
                    val pageOffset = (
                        (pagePagerState.currentPage - pageIndex) + pagePagerState.currentPageOffsetFraction
                        ).absoluteValue
                    val panelAlpha by animateFloatAsState(
                        targetValue = (1f - pageOffset.coerceIn(0f, 1f) * 0.9f).coerceIn(0.1f, 1f),
                        animationSpec = tween(MAIN_SCREEN_PANEL_FADE_MS),
                        label = "main_screen_page_panels_fade",
                    )
                    MainScreenPagePanels(
                        pageNumber = pageIndex + 1,
                        pageCount = pageCount,
                        mainPanels = mainPanels,
                        maxWpx = maxWpx,
                        maxHpx = maxHpx,
                        tboxViewModel = tboxViewModel,
                        canViewModel = canViewModel,
                        appDataViewModel = appDataViewModel,
                        settingsViewModel = settingsViewModel,
                        onRebootTbox = onTboxRestart,
                        onTripFinishAndStart = onTripFinishAndStart,
                        windowMode = windowMode,
                        modifier = Modifier.graphicsLayer { alpha = panelAlpha },
                    )
                }
            }
        } else {
            MainScreenPagePanels(
                pageNumber = currentPage,
                pageCount = pageCount,
                mainPanels = mainPanels,
                maxWpx = maxWpx,
                maxHpx = maxHpx,
                tboxViewModel = tboxViewModel,
                canViewModel = canViewModel,
                appDataViewModel = appDataViewModel,
                settingsViewModel = settingsViewModel,
                onRebootTbox = onTboxRestart,
                onTripFinishAndStart = onTripFinishAndStart,
                windowMode = windowMode,
            )
        }

        MainScreenDraggableCornerButton(
            icon = ImageVector.vectorResource(R.drawable.ic_main_open_console),
            contentDescription = stringResource(R.string.main_open_console_cd),
            iconSize = cornerIconSize,
            backgroundColor = cornerBackgroundColor,
            iconTint = cornerIconTint,
            maxWidthPx = maxWpx,
            maxHeightPx = maxHpx,
            normalizedX = settingsBtnPos.x,
            normalizedY = settingsBtnPos.y,
            onSaveNormalized = { x, y ->
                settingsViewModel.saveMainScreenSettingsButton(MainScreenSettingsButtonPosition(x, y))
            },
            onClick = onOpenConsole,
            layoutSnapStepPx = effectiveLayoutSnapStepPx,
        )

        if (!windowMode) {
            MainScreenDraggableCornerButton(
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.main_screen_add_panel_cd),
                iconSize = cornerIconSize,
                backgroundColor = cornerBackgroundColor,
                iconTint = cornerIconTint,
                maxWidthPx = maxWpx,
                maxHeightPx = maxHpx,
                normalizedX = addBtnPos.x,
                normalizedY = addBtnPos.y,
                onSaveNormalized = { x, y ->
                    settingsViewModel.saveMainScreenAddButton(MainScreenAddButtonPosition(x, y))
                },
                onClick = {
                    settingsViewModel.addMainScreenDashboard(newMainPanelDefaultName, currentPage)
                },
                layoutSnapStepPx = effectiveLayoutSnapStepPx,
            )
        }

        if (showWallpaperNavButtons) {
            MainScreenDraggableCornerButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.main_screen_wallpaper_prev_cd),
                iconSize = cornerIconSize,
                backgroundColor = cornerBackgroundColor,
                iconTint = cornerIconTint,
                maxWidthPx = maxWpx,
                maxHeightPx = maxHpx,
                normalizedX = pagePrevBtnPos.x,
                normalizedY = pagePrevBtnPos.y,
                onSaveNormalized = { x, y ->
                    settingsViewModel.saveMainScreenPagePrevButton(MainScreenPagePrevButtonPosition(x, y))
                },
                onClick = { wallpaperController.step(-1) },
                onHorizontalSwipe = wallpaperController::step,
                layoutSnapStepPx = effectiveLayoutSnapStepPx,
            )
            MainScreenDraggableCornerButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.main_screen_wallpaper_next_cd),
                iconSize = cornerIconSize,
                backgroundColor = cornerBackgroundColor,
                iconTint = cornerIconTint,
                maxWidthPx = maxWpx,
                maxHeightPx = maxHpx,
                normalizedX = pageNextBtnPos.x,
                normalizedY = pageNextBtnPos.y,
                onSaveNormalized = { x, y ->
                    settingsViewModel.saveMainScreenPageNextButton(MainScreenPageNextButtonPosition(x, y))
                },
                onClick = { wallpaperController.step(1) },
                onHorizontalSwipe = wallpaperController::step,
                layoutSnapStepPx = effectiveLayoutSnapStepPx,
            )
        }

        if (windowMode && onExitWindowMode != null) {
            MainScreenDraggableCornerButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.main_screen_window_mode_exit_cd),
                iconSize = cornerIconSize,
                backgroundColor = cornerBackgroundColor,
                iconTint = cornerIconTint,
                maxWidthPx = maxWpx,
                maxHeightPx = maxHpx,
                normalizedX = exitWindowBtnPos.x,
                normalizedY = exitWindowBtnPos.y,
                onSaveNormalized = { x, y ->
                    settingsViewModel.saveMainScreenWindowModeExitButton(
                        MainScreenWindowModeExitButtonPosition(x, y),
                    )
                },
                onClick = onExitWindowMode,
                layoutSnapStepPx = effectiveLayoutSnapStepPx,
            )
        }

        if (windowMode && onExitWindowModeToFullscreen != null) {
            MainScreenDraggableCornerButton(
                icon = WindowModeRestoreSquareIcon,
                contentDescription = stringResource(R.string.main_screen_window_mode_restore_cd),
                iconSize = cornerIconSize,
                backgroundColor = cornerBackgroundColor,
                iconTint = cornerIconTint,
                maxWidthPx = maxWpx,
                maxHeightPx = maxHpx,
                normalizedX = restoreWindowBtnPos.x,
                normalizedY = restoreWindowBtnPos.y,
                onSaveNormalized = { x, y ->
                    settingsViewModel.saveMainScreenWindowModeRestoreButton(
                        MainScreenWindowModeExitButtonPosition(x, y),
                    )
                },
                onClick = onExitWindowModeToFullscreen,
                layoutSnapStepPx = effectiveLayoutSnapStepPx,
            )
        }

        LaunchedEffect(floatingOverlayEditRequest, windowMode) {
            if (windowMode && floatingOverlayEditRequest != null) {
                floatingOverlayEditRequest = null
                WindowModeUiGuard.toastEditingBlocked(context)
            }
        }

        if (!windowMode) {
            floatingOverlayEditRequest?.let { (panelId, widgetIndex) ->
                MainScreenFloatingOverlayEdit(
                    panelId = panelId,
                    widgetIndex = widgetIndex,
                    settingsViewModel = settingsViewModel,
                    currentTheme = currentTheme,
                    onDismiss = { floatingOverlayEditRequest = null },
                )
            }
        }
    }
}
