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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
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
import vad.dashing.tbox.MainScreenAddButtonPosition
import vad.dashing.tbox.MainScreenPageNextButtonPosition
import vad.dashing.tbox.MainScreenPagePrevButtonPosition
import vad.dashing.tbox.MainScreenSettingsButtonPosition
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel

private const val MAIN_SCREEN_PANEL_FADE_MS = 300

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
) {
    val mainPanels by settingsViewModel.mainScreenDashboards.collectAsStateWithLifecycle()
    val pageCount by settingsViewModel.mainScreenPageCount.collectAsStateWithLifecycle()
    val currentPage by settingsViewModel.mainScreenCurrentPage.collectAsStateWithLifecycle()
    val settingsBtnPos by settingsViewModel.mainScreenSettingsButtonPosition.collectAsStateWithLifecycle()
    val addBtnPos by settingsViewModel.mainScreenAddButtonPosition.collectAsStateWithLifecycle()
    val pagePrevBtnPos by settingsViewModel.mainScreenPagePrevButtonPosition.collectAsStateWithLifecycle()
    val pageNextBtnPos by settingsViewModel.mainScreenPageNextButtonPosition.collectAsStateWithLifecycle()
    val cornerBtnSizeDp by settingsViewModel.mainScreenCornerButtonSizeDp.collectAsStateWithLifecycle()
    val cornerBtnBgLight by settingsViewModel.mainScreenCornerButtonBackgroundLight.collectAsStateWithLifecycle()
    val cornerBtnBgDark by settingsViewModel.mainScreenCornerButtonBackgroundDark.collectAsStateWithLifecycle()
    val cornerBtnIconLight by settingsViewModel.mainScreenCornerButtonIconLight.collectAsStateWithLifecycle()
    val cornerBtnIconDark by settingsViewModel.mainScreenCornerButtonIconDark.collectAsStateWithLifecycle()
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
    val newMainPanelDefaultName = stringResource(R.string.floating_dashboard_new_panel_default)
    val wallpaperController = remember { MainScreenWallpaperController() }
    var wallpaperCount by remember { mutableIntStateOf(0) }
    var wallpaperWorkEnabled by remember { mutableStateOf(false) }
    val multiPage = pageCount > 1
    val showWallpaperNavButtons = multiPage && wallpaperCount > 1

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        settingsViewModel.flushMainScreenCurrentPage()
        settingsViewModel.flushMainScreenWallpaperSelection()
    }

    DisposableEffect(Unit) {
        onDispose {
            settingsViewModel.flushMainScreenCurrentPage()
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
            .onGloballyPositioned {
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
                                settingsViewModel.scheduleSaveMainScreenCurrentPage(page)
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
        )

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
        )

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
            )
        }

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
